package me.proxycracked.universalaccountmanager.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

public final class SkinFavoritesManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Favorite> FAVORITES = new ArrayList<>();
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Set<String> KNOWN_ACCOUNTS = new HashSet<>();
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "UniversalAccountManager-DefaultSkin");
        thread.setDaemon(true);
        return thread;
    });

    private static boolean loaded;
    private static boolean accountTrackingInitialized;
    private static String defaultId = "";

    private SkinFavoritesManager() {
    }

    public static synchronized List<Favorite> getFavorites() {
        load();
        return Collections.unmodifiableList(new ArrayList<>(FAVORITES));
    }

    public static synchronized String getDefaultId() {
        load();
        return defaultId;
    }

    public static synchronized Favorite addFavorite(byte[] pngBytes, String fileName, String variant) throws Exception {
        load();
        if (pngBytes == null || pngBytes.length == 0) throw new IOException("The selected skin is empty");
        if (pngBytes.length > 4 * 1024 * 1024) throw new IOException("Skin files must be smaller than 4 MB");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (image == null) throw new IOException("The selected file is not a PNG image");
        if (image.getWidth() != 64 || (image.getHeight() != 32 && image.getHeight() != 64)) {
            throw new IOException("Skins must be 64x32 or 64x64 pixels");
        }

        String id = UUID.randomUUID().toString().replace("-", "");
        String name = favoriteName(fileName, FAVORITES.size() + 1);
        Favorite favorite = new Favorite(id, name, "slim".equalsIgnoreCase(variant) ? "slim" : "classic");
        File directory = favoritesDirectory();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create the skins folder");

        File destination = favorite.file();
        File temporary = new File(directory, id + ".tmp");
        Files.write(temporary.toPath(), pngBytes);
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        FAVORITES.add(favorite);
        saveMetadata();
        return favorite;
    }

    public static synchronized void setDefault(String id) throws IOException {
        load();
        if (find(id) == null) throw new IOException("Select a favorite first");
        defaultId = id;
        saveMetadata();
        resetAccountBaseline();
    }

    public static int applyFavorite(Favorite favorite, String accessToken) throws Exception {
        if (favorite == null || !favorite.file().isFile()) throw new IOException("Favorite skin file is missing");
        return SkinChanger.applySkinFile(Files.readAllBytes(favorite.file().toPath()), favorite.variant, accessToken);
    }

    public static synchronized ResourceLocation getTexture(Favorite favorite) {
        if (favorite == null) return null;
        ResourceLocation cached = TEXTURES.get(favorite.id);
        if (cached != null) return cached;
        try {
            BufferedImage source = ImageIO.read(favorite.file());
            if (source == null) return null;
            BufferedImage processed = new ImageBufferDownload().parseUserSkin(source);
            DynamicTexture texture = new DynamicTexture(processed == null ? source : processed);
            ResourceLocation location = Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation("uam_favorite_skin_" + favorite.id, texture);
            TEXTURES.put(favorite.id, location);
            return location;
        } catch (Exception error) {
            return null;
        }
    }

    public static synchronized void initializeAccountTracking() {
        resetAccountBaseline();
        accountTrackingInitialized = true;
    }

    public static synchronized void onAccountsSaved() {
        if (!accountTrackingInitialized) {
            initializeAccountTracking();
            return;
        }

        load();
        Favorite defaultFavorite = find(defaultId);
        for (Account account : UniversalAccountManager.accounts) {
            String key = accountKey(account);
            if (key == null || !KNOWN_ACCOUNTS.add(key) || defaultFavorite == null) continue;
            String accessToken = account.getAccessToken();
            if (accessToken == null || accessToken.trim().isEmpty()) continue;
            Favorite favorite = defaultFavorite;
            DEFAULT_EXECUTOR.submit(() -> {
                try {
                    int code = applyFavorite(favorite, accessToken);
                    if (code != 200) {
                        System.err.println("[UniversalAccountManager] Default skin returned HTTP " + code + " for " + key);
                    }
                } catch (Exception error) {
                    System.err.println("[UniversalAccountManager] Could not apply default skin for " + key + ": " + error.getMessage());
                }
            });
        }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        FAVORITES.clear();
        File metadata = metadataFile();
        if (!metadata.isFile()) return;
        try (FileReader reader = new FileReader(metadata)) {
            State state = GSON.fromJson(reader, State.class);
            if (state == null) return;
            defaultId = state.defaultId == null ? "" : state.defaultId;
            if (state.favorites != null) {
                for (Favorite favorite : state.favorites) {
                    if (favorite != null && favorite.id != null && favorite.file().isFile()) FAVORITES.add(favorite);
                }
            }
            if (find(defaultId) == null) defaultId = "";
        } catch (Exception error) {
            System.err.println("[UniversalAccountManager] Could not load skin favorites: " + error.getMessage());
        }
    }

    private static void saveMetadata() throws IOException {
        File directory = baseDirectory();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create the skins config folder");
        File target = metadataFile();
        File temporary = new File(directory, "favorites.json.tmp");
        try (FileWriter writer = new FileWriter(temporary)) {
            GSON.toJson(new State(defaultId, FAVORITES), writer);
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static Favorite find(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Favorite favorite : FAVORITES) if (id.equals(favorite.id)) return favorite;
        return null;
    }

    private static void resetAccountBaseline() {
        KNOWN_ACCOUNTS.clear();
        for (Account account : UniversalAccountManager.accounts) {
            String key = accountKey(account);
            if (key != null) KNOWN_ACCOUNTS.add(key);
        }
    }

    private static String accountKey(Account account) {
        if (account == null) return null;
        String uuid = account.getUuid();
        if (uuid != null && !uuid.trim().isEmpty()) return "uuid:" + uuid.replace("-", "").toLowerCase();
        String username = account.getUsername();
        return username == null || username.trim().isEmpty() ? null : "name:" + username.toLowerCase();
    }

    private static String favoriteName(String fileName, int fallbackNumber) {
        String name = fileName == null ? "" : fileName.trim();
        if (name.toLowerCase().endsWith(".png")) name = name.substring(0, name.length() - 4);
        name = name.replaceAll("[\\r\\n\\t]", " ").trim();
        if (name.isEmpty()) name = "Favorite " + fallbackNumber;
        return name.length() > 28 ? name.substring(0, 28) : name;
    }

    private static File baseDirectory() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/universalaccountmanager_skins");
    }

    private static File favoritesDirectory() {
        return new File(baseDirectory(), "favorites");
    }

    private static File metadataFile() {
        return new File(baseDirectory(), "favorites.json");
    }

    public static final class Favorite {
        private String id;
        private String name;
        private String variant;

        private Favorite() {
        }

        private Favorite(String id, String name, String variant) {
            this.id = id;
            this.name = name;
            this.variant = variant;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getVariant() { return variant; }
        private File file() { return new File(favoritesDirectory(), id + ".png"); }
    }

    private static final class State {
        private String defaultId;
        private List<Favorite> favorites;

        private State() {
        }

        private State(String defaultId, List<Favorite> favorites) {
            this.defaultId = defaultId;
            this.favorites = new ArrayList<>(favorites);
        }
    }
}
