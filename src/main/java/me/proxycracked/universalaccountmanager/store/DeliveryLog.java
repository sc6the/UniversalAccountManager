package me.proxycracked.universalaccountmanager.store;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.platform.win32.Crypt32Util;

import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.StringUtils;

/**
 * Every delivery any shop has made, kept on disk so all three behave the same.
 *
 * <p>Localts can list past orders over its API and Fernan Club can list past purchases; Nicealts
 * cannot, so without a local record "Import Previous Purchases" would exist for two shops out of
 * three and a Nicealts alt that failed to log in the first time would be gone for good. Every
 * delivery is written here instead, and the history screen merges this with whatever the shop
 * itself reports.</p>
 *
 * <p>The entries are live account credentials, so the file is DPAPI-encrypted for the current
 * Windows user exactly like the API keys.</p>
 */
public final class DeliveryLog {
    private static final int MAX_ORDERS_PER_PROVIDER = 2000;
    private static final Map<String, Map<String, LoggedOrder>> ORDERS = new LinkedHashMap<String, Map<String, LoggedOrder>>();
    private static boolean loaded;

    private DeliveryLog() {
    }

    public static synchronized void record(String providerId, StoreOrder order) {
        if (put(providerId, order, 0L)) {
            save();
        }
    }

    /**
     * Caches an order read back from a shop's history without writing the file.
     *
     * <p>A history scan reads hundreds of orders, and every save is a DPAPI encrypt plus a file
     * rewrite, so the scan defers them and calls {@link #flush()} as it goes. Anything cached here
     * is never fetched again, which is what makes a second scan cost nothing.</p>
     */
    public static synchronized void recordDeferred(String providerId, StoreOrder order, long timestamp) {
        put(providerId, order, timestamp);
    }

    public static synchronized void flush() {
        save();
    }

    /** True when the order is already cached, so the shop does not need to be asked for it. */
    public static synchronized boolean contains(String providerId, String orderId) {
        load();
        Map<String, LoggedOrder> orders = ORDERS.get(providerId);
        return orders != null && orders.containsKey(orderId);
    }

    private static boolean put(String providerId, StoreOrder order, long timestamp) {
        if (order == null || StringUtils.isBlank(order.getId()) || order.getItems().isEmpty()) {
            return false;
        }
        load();
        Map<String, LoggedOrder> orders = ORDERS.get(providerId);
        if (orders == null) {
            orders = new LinkedHashMap<String, LoggedOrder>();
            ORDERS.put(providerId, orders);
        }
        long stamp = timestamp > 0L ? timestamp : System.currentTimeMillis();
        orders.put(order.getId(), new LoggedOrder(order.getProductName(), stamp, order.getItems()));
        trim(orders);
        return true;
    }

    public static synchronized List<StoreOrderSummary> summaries(String providerId) {
        load();
        Map<String, LoggedOrder> orders = ORDERS.get(providerId);
        if (orders == null) {
            return Collections.emptyList();
        }
        List<StoreOrderSummary> summaries = new ArrayList<StoreOrderSummary>();
        for (Map.Entry<String, LoggedOrder> entry : orders.entrySet()) {
            LoggedOrder logged = entry.getValue();
            summaries.add(new StoreOrderSummary(
                entry.getKey(), "", logged.productName, logged.timestamp, logged.items.size()
            ));
        }
        Collections.sort(summaries, new Comparator<StoreOrderSummary>() {
            @Override
            public int compare(StoreOrderSummary left, StoreOrderSummary right) {
                return Long.compare(right.getTimestamp(), left.getTimestamp());
            }
        });
        return summaries;
    }

    public static synchronized StoreOrder order(String providerId, String orderId) {
        load();
        Map<String, LoggedOrder> orders = ORDERS.get(providerId);
        LoggedOrder logged = orders == null ? null : orders.get(orderId);
        if (logged == null) {
            return null;
        }
        return new StoreOrder(orderId, logged.productName, "DELIVERED", true, new ArrayList<StoreItem>(logged.items));
    }

    /** Drops the oldest orders first, by their own timestamp rather than by insertion order. */
    private static void trim(Map<String, LoggedOrder> orders) {
        while (orders.size() > MAX_ORDERS_PER_PROVIDER) {
            String oldest = null;
            long oldestStamp = Long.MAX_VALUE;
            for (Map.Entry<String, LoggedOrder> entry : orders.entrySet()) {
                if (entry.getValue().timestamp <= oldestStamp) {
                    oldestStamp = entry.getValue().timestamp;
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) {
                return;
            }
            orders.remove(oldest);
        }
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = file();
        if (!file.isFile()) {
            return;
        }
        byte[] clear = null;
        try {
            byte[] encrypted = Base64.getDecoder().decode(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII).trim()
            );
            clear = Crypt32Util.cryptUnprotectData(encrypted);
            JsonElement root = new JsonParser().parse(new String(clear, StandardCharsets.UTF_8));
            if (root == null || !root.isJsonObject()) {
                return;
            }
            JsonElement providers = root.getAsJsonObject().get("providers");
            if (providers == null || !providers.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> provider : providers.getAsJsonObject().entrySet()) {
                if (!provider.getValue().isJsonObject()) {
                    continue;
                }
                Map<String, LoggedOrder> orders = new LinkedHashMap<String, LoggedOrder>();
                for (Map.Entry<String, JsonElement> entry : provider.getValue().getAsJsonObject().entrySet()) {
                    LoggedOrder logged = readOrder(entry.getValue());
                    if (logged != null) {
                        orders.put(entry.getKey(), logged);
                    }
                }
                ORDERS.put(provider.getKey(), orders);
            }
        } catch (Exception error) {
            System.err.println("[UniversalAccountManager] Could not read the delivery log: " + error.getMessage());
        } finally {
            if (clear != null) {
                Arrays.fill(clear, (byte) 0);
            }
        }
    }

    private static LoggedOrder readOrder(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        List<StoreItem> items = new ArrayList<StoreItem>();
        JsonElement itemsElement = json.get("items");
        if (itemsElement != null && itemsElement.isJsonArray()) {
            for (JsonElement itemElement : itemsElement.getAsJsonArray()) {
                if (!itemElement.isJsonObject()) {
                    continue;
                }
                JsonObject item = itemElement.getAsJsonObject();
                StoreItemKind kind;
                try {
                    kind = StoreItemKind.valueOf(string(item, "kind"));
                } catch (RuntimeException ignored) {
                    kind = StoreItemKind.UNSUPPORTED;
                }
                items.add(new StoreItem(
                    string(item, "id"), string(item, "content"), kind, string(item, "label"),
                    string(item, "username"), string(item, "uuid"),
                    string(item, "accessToken"), string(item, "refreshToken")
                ));
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        return new LoggedOrder(string(json, "productName"), number(json, "timestamp"), items);
    }

    private static void save() {
        JsonObject providers = new JsonObject();
        for (Map.Entry<String, Map<String, LoggedOrder>> provider : ORDERS.entrySet()) {
            JsonObject orders = new JsonObject();
            for (Map.Entry<String, LoggedOrder> entry : provider.getValue().entrySet()) {
                orders.add(entry.getKey(), writeOrder(entry.getValue()));
            }
            providers.add(provider.getKey(), orders);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("providers", providers);

        File file = file();
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            return;
        }
        byte[] clear = root.toString().getBytes(StandardCharsets.UTF_8);
        try {
            byte[] encrypted = Crypt32Util.cryptProtectData(clear);
            File temporary = new File(directory, file.getName() + ".tmp");
            Files.write(temporary.toPath(), Base64.getEncoder().encode(encrypted));
            Arrays.fill(encrypted, (byte) 0);
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            System.err.println("[UniversalAccountManager] Could not write the delivery log: " + error.getMessage());
        } finally {
            Arrays.fill(clear, (byte) 0);
        }
    }

    private static JsonObject writeOrder(LoggedOrder logged) {
        JsonArray items = new JsonArray();
        for (StoreItem item : logged.items) {
            JsonObject json = new JsonObject();
            json.addProperty("id", item.getId());
            json.addProperty("content", item.getContent());
            json.addProperty("kind", item.getKind().name());
            json.addProperty("label", item.getLabel());
            json.addProperty("username", item.getUsername());
            json.addProperty("uuid", item.getUuid());
            json.addProperty("accessToken", item.getAccessToken());
            json.addProperty("refreshToken", item.getRefreshToken());
            items.add(json);
        }
        JsonObject order = new JsonObject();
        order.addProperty("productName", logged.productName);
        order.addProperty("timestamp", logged.timestamp);
        order.add("items", items);
        return order;
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

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/universalaccountmanager_store_deliveries.dat");
    }

    private static final class LoggedOrder {
        private final String productName;
        private final long timestamp;
        private final List<StoreItem> items;

        private LoggedOrder(String productName, long timestamp, List<StoreItem> items) {
            this.productName = productName == null ? "" : productName;
            this.timestamp = timestamp;
            this.items = new ArrayList<StoreItem>(items);
        }
    }
}
