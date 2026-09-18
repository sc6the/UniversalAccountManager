package me.proxycracked.universalaccountmanager.store;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.StringUtils;

/**
 * Remembers which delivered items were already turned into accounts and which ones are dead.
 *
 * <p>An item that has been imported once is never offered again. The record used to lapse when the
 * account it produced left the list or stopped validating, which meant every deleted or expired alt
 * came back on the next scan and had to be skipped by hand - and, because a lapsed record makes an
 * order look importable, it also kept the history scan reading orders it had already finished with.
 * The single-order lookup with Shift held is the way back for an account deleted by mistake.</p>
 *
 * <p>Keys are namespaced by shop. Localts item ids stay bare so the cache written by the old
 * Localts-only screen keeps counting.</p>
 */
public final class StoreImportTracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ImportRecord> IMPORTED = new LinkedHashMap<String, ImportRecord>();
    private static final Map<String, FailureRecord> FAILED = new LinkedHashMap<String, FailureRecord>();
    private static final String[] TRANSIENT_MARKERS = {
        "rate limit", "429", "timed out", "timeout", "unavailable", "temporarily",
        "connection", "connect", "unknownhost", "no route", "network", "socket", "502", "503", "504"
    };
    private static boolean loaded;

    private StoreImportTracker() {
    }

    /** Localts ids are already unique and already cached unprefixed; the rest are namespaced. */
    public static String key(String providerId, String itemId) {
        if (StringUtils.isBlank(itemId)) {
            return "";
        }
        return "localts".equals(providerId) ? itemId : providerId + ":" + itemId;
    }

    /** True when the item has already been imported, whatever became of the account afterwards. */
    public static synchronized boolean isSatisfied(String key) {
        load();
        return IMPORTED.containsKey(key);
    }

    /** Drops the import record for one item so it can be offered again. */
    public static synchronized void clearImported(String key) {
        load();
        if (IMPORTED.remove(key) != null) {
            save();
        }
    }

    /** True when the item failed for a reason that will not fix itself. */
    public static synchronized boolean isFailed(String key) {
        load();
        return FAILED.containsKey(key);
    }

    public static synchronized String failureReason(String key) {
        load();
        FailureRecord record = FAILED.get(key);
        return record == null ? "" : record.reason;
    }

    public static synchronized void markImported(String key, String uuid, String username) {
        if (StringUtils.isBlank(key)) {
            return;
        }
        load();
        IMPORTED.put(key, new ImportRecord(uuid, username, System.currentTimeMillis()));
        FAILED.remove(key);
        save();
    }

    /** Records a failure; transient errors are not cached so they can be retried. */
    public static synchronized void markFailed(String key, String reason) {
        if (StringUtils.isBlank(key) || isTransient(reason)) {
            return;
        }
        load();
        FAILED.put(key, new FailureRecord(reason, System.currentTimeMillis()));
        save();
    }

    public static synchronized void clearFailure(String key) {
        load();
        if (FAILED.remove(key) != null) {
            save();
        }
    }

    public static boolean isTransient(String reason) {
        if (StringUtils.isBlank(reason)) {
            return true;
        }
        String text = reason.toLowerCase(Locale.ROOT);
        for (String marker : TRANSIENT_MARKERS) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = file();
        if (!file.isFile()) {
            file = legacyFile();
            if (!file.isFile()) {
                return;
            }
        }
        try {
            FileReader reader = new FileReader(file);
            try {
                JsonElement root = new JsonParser().parse(reader);
                if (root == null || root.isJsonNull()) {
                    return;
                }
                if (root.isJsonArray()) {
                    // Oldest format: a plain array of imported Localts item ids.
                    for (JsonElement element : root.getAsJsonArray()) {
                        if (element.isJsonPrimitive()) {
                            IMPORTED.put(element.getAsString(), new ImportRecord("", "", 0L));
                        }
                    }
                    return;
                }
                if (!root.isJsonObject()) {
                    return;
                }
                JsonObject json = root.getAsJsonObject();
                readImported(json.get("imported"));
                readFailed(json.get("failed"));
            } finally {
                reader.close();
            }
        } catch (Exception error) {
            System.err.println("[UniversalAccountManager] Could not load the store import history: " + error.getMessage());
        }
    }

    private static void readImported(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject record = entry.getValue().getAsJsonObject();
            IMPORTED.put(entry.getKey(), new ImportRecord(
                string(record, "uuid"), string(record, "username"), number(record, "importedAt")
            ));
        }
    }

    private static void readFailed(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject record = entry.getValue().getAsJsonObject();
            FAILED.put(entry.getKey(), new FailureRecord(string(record, "reason"), number(record, "failedAt")));
        }
    }

    private static String string(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static long number(JsonObject json, String field) {
        JsonElement value = json.get(field);
        try {
            return value == null || !value.isJsonPrimitive() ? 0L : value.getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static void save() {
        File file = file();
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 3);
        root.add("imported", GSON.toJsonTree(IMPORTED));
        root.add("failed", GSON.toJsonTree(FAILED));
        try {
            FileWriter writer = new FileWriter(file);
            try {
                GSON.toJson(root, writer);
            } finally {
                writer.close();
            }
        } catch (Exception error) {
            System.err.println("[UniversalAccountManager] Could not save the store import history: " + error.getMessage());
        }
    }

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/universalaccountmanager_store_imports.json");
    }

    /** The file the Localts-only screen wrote, read once so nothing is re-offered after upgrading. */
    private static File legacyFile() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/universalaccountmanager_localts_imported.json");
    }

    private static final class ImportRecord {
        private final String uuid;
        private final String username;
        private final long importedAt;

        private ImportRecord(String uuid, String username, long importedAt) {
            this.uuid = uuid == null ? "" : uuid;
            this.username = username == null ? "" : username;
            this.importedAt = importedAt;
        }
    }

    private static final class FailureRecord {
        private final String reason;
        private final long failedAt;

        private FailureRecord(String reason, long failedAt) {
            this.reason = reason == null ? "" : reason;
            this.failedAt = failedAt;
        }
    }
}
