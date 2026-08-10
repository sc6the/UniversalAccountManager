package me.proxycracked.universalaccountmanager.localts;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;

public final class LocalTsImportTracker {
    private static final Gson GSON = new Gson();
    private static final Type SET_TYPE = new TypeToken<Set<String>>() { }.getType();
    private static final Set<String> IMPORTED = new HashSet<>();
    private static boolean loaded;

    private LocalTsImportTracker() {
    }

    public static synchronized boolean isImported(String itemId) {
        load();
        return IMPORTED.contains(itemId);
    }

    public static synchronized void markImported(Collection<String> itemIds) {
        load();
        IMPORTED.addAll(itemIds);
        File file = file();
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) return;
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(IMPORTED, SET_TYPE, writer);
        } catch (Exception error) {
            System.err.println("[UniversalAccountManager] Could not save Localts import history: " + error.getMessage());
        }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        File file = file();
        if (!file.isFile()) return;
        try (FileReader reader = new FileReader(file)) {
            Set<String> values = GSON.fromJson(reader, SET_TYPE);
            if (values != null) IMPORTED.addAll(values);
        } catch (Exception error) {
            System.err.println("[UniversalAccountManager] Could not load Localts import history: " + error.getMessage());
        }
    }

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/universalaccountmanager_localts_imported.json");
    }
}
