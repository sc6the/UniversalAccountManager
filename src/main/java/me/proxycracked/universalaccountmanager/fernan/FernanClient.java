package me.proxycracked.universalaccountmanager.fernan;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.proxycracked.universalaccountmanager.store.RateLimitSignal;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * The Fernan Club API.
 *
 * <p>Authenticated with an {@code X-API-Key} header, and every reply - success or failure - is
 * wrapped as <code>{"success": bool, "data": {...}}</code> with the message under
 * {@code data.error}, so {@link #execute} unwraps once and everything below works on {@code data}.</p>
 *
 * <p>Deliveries are a base64 Netscape cookie file plus a live Minecraft access token, which is why
 * the products are cookie products as far as the importer is concerned.</p>
 */
public final class FernanClient {
    public static final String BASE_URL = "https://api.fernan.club/api/v1";
    public static final String WEBSITE_URL = "https://fernan.club/api";
    /**
     * The published docs give the body and the 201 for a refund request but not its path; it is the
     * write side of the {@code /store/refunds} collection that the two documented reads use.
     */
    private static final String REFUND_PATH = "/store/refunds";
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
        .setConnectionRequestTimeout(15000)
        .setConnectTimeout(15000)
        .setSocketTimeout(60000)
        .build();

    private FernanClient() {
    }

    public static User getMe(String apiKey) throws Exception {
        JsonObject data = execute(new HttpGet(BASE_URL + "/user/me"), apiKey);
        return new User(
            optionalString(data, "username"),
            requiredNumber(data, "balance"),
            data.has("role_id") && data.get("role_id").isJsonPrimitive() ? data.get("role_id").getAsInt() : 0
        );
    }

    public static List<Product> getStock(String apiKey) throws Exception {
        JsonObject data = execute(new HttpGet(BASE_URL + "/store/stock"), apiKey);
        List<Product> products = new ArrayList<Product>();
        for (JsonElement element : array(data, "stock")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject product = element.getAsJsonObject();
            products.add(new Product(
                integer(product, "product_id"),
                optionalString(product, "product_name"),
                optionalString(product, "product_description"),
                optionalString(product, "category"),
                optionalString(product, "server_category"),
                integer(product, "count"),
                product.has("price") && product.get("price").isJsonPrimitive() ? product.get("price").getAsDouble() : 0.0D,
                integer(product, "cooldown"),
                integer(product, "purchase_limit")
            ));
        }
        return Collections.unmodifiableList(products);
    }

    /** How many of each product can still be bought right now, keyed by product id. */
    public static Map<Integer, Cooldown> getCooldowns(String apiKey) throws Exception {
        JsonObject data = execute(new HttpGet(BASE_URL + "/store/cooldown"), apiKey);
        Map<Integer, Cooldown> cooldowns = new HashMap<Integer, Cooldown>();
        for (JsonElement element : array(data, "cooldowns")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject cooldown = element.getAsJsonObject();
            int productId = integer(cooldown, "product_id");
            cooldowns.put(productId, new Cooldown(
                productId,
                integer(cooldown, "remaining_allowed"),
                cooldown.has("on_cooldown") && cooldown.get("on_cooldown").isJsonPrimitive()
                    && cooldown.get("on_cooldown").getAsBoolean(),
                optionalString(cooldown, "cooldown_ends_at")
            ));
        }
        return cooldowns;
    }

    public static Purchase purchase(String apiKey, int productId, int amount, String referralCode) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("product_id", productId);
        body.addProperty("amount", amount);
        if (!StringUtils.isBlank(referralCode)) {
            body.addProperty("referral_code", referralCode.trim());
        }
        JsonObject data = execute(post("/store/purchase", body), apiKey);
        return new Purchase(
            optionalString(data, "purchase_id"),
            optionalString(data, "product"),
            integer(data, "delivered_amount"),
            data.has("total_cost") && data.get("total_cost").isJsonPrimitive() ? data.get("total_cost").getAsDouble() : 0.0D,
            integer(data, "invalid_count"),
            items(array(data, "products"))
        );
    }

    public static PurchasePage getPurchases(String apiKey, int limit, int offset) throws Exception {
        URI uri = new URIBuilder(BASE_URL + "/store/purchases")
            .addParameter("limit", Integer.toString(limit))
            .addParameter("offset", Integer.toString(offset))
            .build();
        JsonObject data = execute(new HttpGet(uri), apiKey);
        List<PurchaseSummary> summaries = new ArrayList<PurchaseSummary>();
        for (JsonElement element : array(data, "purchases")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject purchase = element.getAsJsonObject();
            summaries.add(new PurchaseSummary(
                optionalString(purchase, "purchase_id"),
                integer(purchase, "product_id"),
                optionalString(purchase, "product_name"),
                optionalString(purchase, "purchased_at"),
                integer(purchase, "amount")
            ));
        }
        return new PurchasePage(summaries, integer(data, "total"));
    }

    public static Purchase getPurchase(String apiKey, String purchaseId) throws Exception {
        JsonObject data = execute(new HttpGet(BASE_URL + "/store/purchases/" + purchaseId), apiKey);
        JsonElement element = data.get("purchase");
        JsonObject purchase = element != null && element.isJsonObject() ? element.getAsJsonObject() : data;
        return new Purchase(
            optionalString(purchase, "purchase_id"),
            optionalString(purchase, "product_name"),
            integer(purchase, "amount"),
            purchase.has("total_paid") && purchase.get("total_paid").isJsonPrimitive()
                ? purchase.get("total_paid").getAsDouble() : 0.0D,
            0,
            items(array(purchase, "products"))
        );
    }

    /** Adds a Peso key's value to the balance. */
    public static Redemption redeem(String apiKey, String key) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("key", key.trim());
        JsonObject data = execute(post("/user/redeem", body), apiKey);
        return new Redemption(
            data.has("value") && data.get("value").isJsonPrimitive() ? data.get("value").getAsDouble() : 0.0D,
            data.has("balance_after") && data.get("balance_after").isJsonPrimitive()
                ? data.get("balance_after").getAsDouble() : 0.0D
        );
    }

    /** Opens a refund request for delivered items that could not be logged into. */
    public static String requestRefund(String apiKey, String purchaseId, List<String> productUuids, String reason) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("purchase_id", purchaseId);
        JsonArray uuids = new JsonArray();
        for (String uuid : productUuids) {
            if (!StringUtils.isBlank(uuid)) {
                uuids.add(new com.google.gson.JsonPrimitive(uuid));
            }
        }
        if (uuids.size() == 0) {
            throw new FernanException("None of those items carry the product id a refund needs");
        }
        body.add("product_uuids", uuids);
        body.addProperty("reason", trimReason(reason));
        JsonObject data = execute(post(REFUND_PATH, body), apiKey);
        return optionalString(data, "refund_id");
    }

    private static String trimReason(String reason) {
        String value = StringUtils.isBlank(reason) ? "Items not working" : reason.trim();
        return value.length() > 50 ? value.substring(0, 50) : value;
    }

    private static List<Item> items(JsonArray array) {
        List<Item> items = new ArrayList<Item>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String uuid = optionalString(item, "uuid");
            if (StringUtils.isBlank(uuid)) {
                uuid = optionalString(item, "mc_uuid");
            }
            items.add(new Item(
                uuid,
                optionalString(item, "username"),
                optionalString(item, "data"),
                optionalString(item, "access_token")
            ));
        }
        return items;
    }

    private static HttpPost post(String path, JsonObject body) throws Exception {
        HttpPost request = new HttpPost(BASE_URL + path);
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(body.toString(), StandardCharsets.UTF_8));
        return request;
    }

    private static JsonObject execute(HttpRequestBase request, String apiKey) throws Exception {
        request.setConfig(REQUEST_CONFIG);
        request.setHeader("Accept", "application/json");
        request.setHeader("User-Agent", "UniversalAccountManager/2.14");
        if (!StringUtils.isBlank(apiKey)) {
            request.setHeader("X-API-Key", apiKey);
        }
        CloseableHttpClient client = HttpClients.createDefault();
        try {
            CloseableHttpResponse response = client.execute(request);
            try {
                int status = response.getStatusLine().getStatusCode();
                String body = response.getEntity() == null
                    ? ""
                    : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                JsonObject json;
                try {
                    JsonElement parsed = new JsonParser().parse(body);
                    json = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
                } catch (RuntimeException error) {
                    throw new FernanException("Fernan Club returned an invalid response (HTTP " + status + ")");
                }

                JsonElement dataElement = json.get("data");
                JsonObject data = dataElement != null && dataElement.isJsonObject()
                    ? dataElement.getAsJsonObject() : new JsonObject();
                boolean success = json.has("success")
                    && json.get("success").isJsonPrimitive()
                    && json.get("success").getAsBoolean();

                if (status == 429) {
                    RateLimitSignal.report(0L);
                }
                if (status < 200 || status >= 300 || !success) {
                    throw new FernanException(errorMessage(data, status));
                }
                return data;
            } finally {
                response.close();
            }
        } finally {
            client.close();
        }
    }

    private static String errorMessage(JsonObject data, int status) {
        String error = optionalString(data, "error");
        if (!StringUtils.isBlank(error)) {
            return error;
        }
        switch (status) {
            case 401: return "Fernan Club: not signed in - check the API key";
            case 403: return "Fernan Club: forbidden";
            case 404: return "Fernan Club: not found";
            case 409: return "Fernan Club: conflict (already used or already refunded)";
            case 422: return "Fernan Club: the request was rejected as invalid";
            case 429: return "Fernan Club: rate limited, wait a moment";
            case 503: return "Fernan Club: temporarily unavailable";
            default: return "Fernan Club request failed (HTTP " + status + ")";
        }
    }

    private static JsonArray array(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String optionalString(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static int integer(JsonObject json, String field) {
        JsonElement value = json.get(field);
        try {
            return value == null || !value.isJsonPrimitive() ? 0 : value.getAsInt();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static double requiredNumber(JsonObject json, String field) throws FernanException {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            throw new FernanException("Fernan Club response is missing " + field);
        }
        return value.getAsDouble();
    }

    public static final class User {
        private final String username;
        private final double balance;
        private final int roleId;

        private User(String username, double balance, int roleId) {
            this.username = username;
            this.balance = balance;
            this.roleId = roleId;
        }

        public String getUsername() { return username; }
        public double getBalance() { return balance; }
        public int getRoleId() { return roleId; }
    }

    public static final class Product {
        private final int id;
        private final String name;
        private final String description;
        private final String category;
        private final String serverCategory;
        private final int count;
        private final double price;
        private final int cooldownMinutes;
        private final int purchaseLimit;

        private Product(int id, String name, String description, String category, String serverCategory,
                        int count, double price, int cooldownMinutes, int purchaseLimit) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.serverCategory = serverCategory;
            this.count = count;
            this.price = price;
            this.cooldownMinutes = cooldownMinutes;
            this.purchaseLimit = purchaseLimit;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }

        /** Delivery format, e.g. {@code cookie}. */
        public String getCategory() { return category; }

        /** Which server the alt is meant for, e.g. {@code hypixel}. */
        public String getServerCategory() { return serverCategory; }

        public int getCount() { return count; }
        public double getPrice() { return price; }
        public int getCooldownMinutes() { return cooldownMinutes; }
        public int getPurchaseLimit() { return purchaseLimit; }
    }

    public static final class Cooldown {
        private final int productId;
        private final int remainingAllowed;
        private final boolean onCooldown;
        private final String endsAt;

        private Cooldown(int productId, int remainingAllowed, boolean onCooldown, String endsAt) {
            this.productId = productId;
            this.remainingAllowed = remainingAllowed;
            this.onCooldown = onCooldown;
            this.endsAt = endsAt;
        }

        public int getProductId() { return productId; }
        public int getRemainingAllowed() { return remainingAllowed; }
        public boolean isOnCooldown() { return onCooldown; }
        public String getEndsAt() { return endsAt; }
    }

    public static final class Item {
        private final String uuid;
        private final String username;
        private final String cookieData;
        private final String accessToken;

        private Item(String uuid, String username, String cookieData, String accessToken) {
            this.uuid = uuid;
            this.username = username;
            this.cookieData = cookieData;
            this.accessToken = accessToken;
        }

        public String getUuid() { return uuid; }
        public String getUsername() { return username; }

        /** Base64 Netscape cookie file. */
        public String getCookieData() { return cookieData; }

        public String getAccessToken() { return accessToken; }
    }

    public static final class Purchase {
        private final String id;
        private final String productName;
        private final int deliveredAmount;
        private final double totalCost;
        private final int invalidCount;
        private final List<Item> items;

        private Purchase(String id, String productName, int deliveredAmount, double totalCost, int invalidCount, List<Item> items) {
            this.id = id;
            this.productName = productName;
            this.deliveredAmount = deliveredAmount;
            this.totalCost = totalCost;
            this.invalidCount = invalidCount;
            this.items = Collections.unmodifiableList(items);
        }

        public String getId() { return id; }
        public String getProductName() { return productName; }
        public int getDeliveredAmount() { return deliveredAmount; }
        public double getTotalCost() { return totalCost; }

        /** Items the shop itself found dead and already refunded. */
        public int getInvalidCount() { return invalidCount; }

        public List<Item> getItems() { return items; }
    }

    public static final class PurchaseSummary {
        private final String id;
        private final int productId;
        private final String productName;
        private final String purchasedAt;
        private final int amount;

        private PurchaseSummary(String id, int productId, String productName, String purchasedAt, int amount) {
            this.id = id;
            this.productId = productId;
            this.productName = productName;
            this.purchasedAt = purchasedAt;
            this.amount = amount;
        }

        public String getId() { return id; }
        public int getProductId() { return productId; }
        public String getProductName() { return productName; }
        public String getPurchasedAt() { return purchasedAt; }
        public int getAmount() { return amount; }
    }

    public static final class PurchasePage {
        private final List<PurchaseSummary> purchases;
        private final int total;

        private PurchasePage(List<PurchaseSummary> purchases, int total) {
            this.purchases = Collections.unmodifiableList(purchases);
            this.total = total;
        }

        public List<PurchaseSummary> getPurchases() { return purchases; }
        public int getTotal() { return total; }
    }

    public static final class Redemption {
        private final double value;
        private final double balanceAfter;

        private Redemption(double value, double balanceAfter) {
            this.value = value;
            this.balanceAfter = balanceAfter;
        }

        public double getValue() { return value; }
        public double getBalanceAfter() { return balanceAfter; }
    }

    public static final class FernanException extends Exception {
        FernanException(String message) {
            super(message);
        }
    }
}
