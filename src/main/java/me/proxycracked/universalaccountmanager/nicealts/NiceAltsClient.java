package me.proxycracked.universalaccountmanager.nicealts;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * The Nicealts API.
 *
 * <p>Every call is a JSON POST carrying {@code api_key} in the body, except the public stock list.
 * The endpoint set and the delivery format were read out of Nicealts' own manager mod, which is why
 * {@link #purchase} knows that an item arrives as {@code mctoken: ... | refreshtoken: ...} rather
 * than as the bare token the mod used to assume.</p>
 */
public final class NiceAltsClient {
    public static final String BASE_URL = "https://app.nicealts.com";
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
        .setConnectionRequestTimeout(15000)
        .setConnectTimeout(15000)
        .setSocketTimeout(60000)
        .build();

    private NiceAltsClient() {
    }

    /** Live stock and prices per product id from the public catalogue endpoint. */
    public static Catalogue getCatalogue() throws Exception {
        JsonObject json = execute(new HttpGet(BASE_URL + "/public/stock"));
        Map<Integer, Integer> counts = integerMap(json, "stock");
        Map<Integer, Double> prices = numberMap(json, "prices");
        if (counts.isEmpty()) {
            throw new NiceAltsException(errorMessage(json, "Nicealts returned no stock"));
        }
        return new Catalogue(
            Collections.unmodifiableMap(counts),
            Collections.unmodifiableMap(prices)
        );
    }

    public static Map<Integer, Integer> getStock() throws Exception {
        return getCatalogue().getStock();
    }

    private static Map<Integer, Integer> integerMap(JsonObject json, String field) {
        JsonElement element = json.get(field);
        JsonObject values = element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            try {
                counts.put(Integer.parseInt(entry.getKey().trim()), entry.getValue().getAsInt());
            } catch (RuntimeException ignored) {
            }
        }
        return counts;
    }

    private static Map<Integer, Double> numberMap(JsonObject json, String field) {
        JsonElement element = json.get(field);
        JsonObject values = element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        Map<Integer, Double> numbers = new HashMap<Integer, Double>();
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            try {
                numbers.put(Integer.parseInt(entry.getKey().trim()), entry.getValue().getAsDouble());
            } catch (RuntimeException ignored) {
            }
        }
        return numbers;
    }

    public static User getBalance(String apiKey) throws Exception {
        JsonObject json = execute(post("/api/balance", body(apiKey)));
        return new User(
            optionalString(json, "username"),
            requiredNumber(json, "balance"),
            optionalString(json, "sub_status"),
            optionalString(json, "sub_expiry")
        );
    }

    /** One call, one alt. The reply is {@code {"items": ["mctoken: ... | refreshtoken: ..."]}}. */
    public static List<String> purchase(String apiKey, int productId) throws Exception {
        JsonObject body = body(apiKey);
        body.addProperty("product_id", Integer.toString(productId));
        return strings(execute(post("/api/purchase", body)), "items", "Nicealts returned no purchased items");
    }

    /**
     * Subscription generator: no credits are spent, but the account only comes as a Minecraft
     * access token, so it expires in about a day.
     *
     * @param category {@code Unbanned}, {@code DonutSMP} or {@code Banned}
     */
    public static String generate(String apiKey, String category) throws Exception {
        JsonObject body = body(apiKey);
        body.addProperty("category", category);
        JsonObject json = execute(post("/api/generate", body));
        String token = optionalString(json, "token");
        if (StringUtils.isBlank(token)) {
            throw new NiceAltsException(errorMessage(json, "Nicealts returned no generated account"));
        }
        return token;
    }

    /**
     * Buys one alt prechecked against a specific server.
     *
     * @param protocol   {@code 1.8}, {@code 1.21} or {@code 26.1}
     * @param banMessage {@code normal} (5 credits) or {@code chat} (6 credits); Minemen requires chat
     */
    public static List<String> customPurchase(String apiKey, String server, String protocol, String banMessage) throws Exception {
        JsonObject body = body(apiKey);
        body.addProperty("server", server);
        body.addProperty("protocol", protocol);
        if (!StringUtils.isBlank(banMessage)) {
            body.addProperty("ban_message", banMessage);
        }
        return strings(execute(post("/api/custompurchase", body)), "tokens", "Nicealts returned no accounts");
    }

    /** The five most recent purchases exposed by Nicealts, including their delivered items. */
    public static List<HistoryEntry> getHistory(String apiKey) throws Exception {
        JsonObject json = execute(post("/api/history", body(apiKey)));
        JsonElement historyElement = json.get("history");
        if (historyElement == null || !historyElement.isJsonArray()) {
            throw new NiceAltsException(errorMessage(json, "Nicealts response is missing purchase history"));
        }
        List<HistoryEntry> history = new ArrayList<HistoryEntry>();
        for (JsonElement element : historyElement.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String purchaseId = optionalString(entry, "purchase_id");
            if (StringUtils.isBlank(purchaseId)) {
                continue;
            }
            List<String> items = new ArrayList<String>();
            JsonElement itemsElement = entry.get("items");
            if (itemsElement != null && itemsElement.isJsonArray()) {
                for (JsonElement item : itemsElement.getAsJsonArray()) {
                    if (item.isJsonPrimitive() && !StringUtils.isBlank(item.getAsString())) {
                        items.add(item.getAsString());
                    }
                }
            }
            history.add(new HistoryEntry(
                purchaseId,
                optionalString(entry, "product_id"),
                optionalString(entry, "timestamp"),
                Collections.unmodifiableList(items)
            ));
        }
        return Collections.unmodifiableList(history);
    }

    private static List<String> strings(JsonObject json, String field, String emptyMessage) throws NiceAltsException {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonArray()) {
            throw new NiceAltsException(errorMessage(json, emptyMessage));
        }
        List<String> values = new ArrayList<String>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (value.isJsonPrimitive() && !StringUtils.isBlank(value.getAsString())) {
                values.add(value.getAsString());
            }
        }
        if (values.isEmpty()) {
            throw new NiceAltsException(emptyMessage);
        }
        return values;
    }

    private static JsonObject body(String apiKey) {
        JsonObject body = new JsonObject();
        body.addProperty("api_key", apiKey);
        return body;
    }

    private static HttpPost post(String path, JsonObject body) throws Exception {
        HttpPost request = new HttpPost(BASE_URL + path);
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(body.toString(), StandardCharsets.UTF_8));
        return request;
    }

    private static JsonObject execute(HttpRequestBase request) throws Exception {
        request.setConfig(REQUEST_CONFIG);
        request.setHeader("Accept", "application/json");
        request.setHeader("User-Agent", "UniversalAccountManager/2.14");
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
                    throw new NiceAltsException("Nicealts returned an invalid response (HTTP " + status + ")");
                }
                if (status < 200 || status >= 300) {
                    throw new NiceAltsException(errorMessage(json, "Nicealts request failed (HTTP " + status + ")"));
                }
                String reportedStatus = optionalString(json, "status");
                if (!StringUtils.isBlank(reportedStatus) && !"success".equalsIgnoreCase(reportedStatus)) {
                    throw new NiceAltsException(errorMessage(json, "Nicealts rejected the request"));
                }
                if (!StringUtils.isBlank(optionalString(json, "error"))) {
                    throw new NiceAltsException(optionalString(json, "error"));
                }
                return json;
            } finally {
                response.close();
            }
        } finally {
            client.close();
        }
    }

    private static double requiredNumber(JsonObject json, String field) throws NiceAltsException {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            throw new NiceAltsException(errorMessage(json, "Nicealts response is missing " + field));
        }
        return value.getAsDouble();
    }

    private static String optionalString(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return "";
        }
        String text = value.getAsString();
        return "null".equals(text) ? "" : text;
    }

    private static String errorMessage(JsonObject json, String fallback) {
        String error = optionalString(json, "error");
        if (StringUtils.isBlank(error)) {
            error = optionalString(json, "message");
        }
        return StringUtils.isBlank(error) ? fallback : error;
    }

    public static final class User {
        private final String username;
        private final double balance;
        private final String subscription;
        private final String subscriptionExpiry;

        private User(String username, double balance, String subscription, String subscriptionExpiry) {
            this.username = username;
            this.balance = balance;
            this.subscription = subscription;
            this.subscriptionExpiry = subscriptionExpiry;
        }

        public String getUsername() { return username; }
        public double getBalance() { return balance; }

        /** Raw {@code sub_status}: {@code P2}, {@code P}, {@code none} or empty. */
        public String getSubscription() { return subscription; }

        /** ISO instant the subscription ends, or empty. */
        public String getSubscriptionExpiry() { return subscriptionExpiry; }

        public boolean hasGeneratorAccess() {
            return "P2".equalsIgnoreCase(subscription) || "P".equalsIgnoreCase(subscription);
        }

        public String subscriptionName() {
            if ("P2".equalsIgnoreCase(subscription)) {
                return "Premium+";
            }
            if ("P".equalsIgnoreCase(subscription)) {
                return "Premium";
            }
            return "None";
        }
    }

    public static final class Catalogue {
        private final Map<Integer, Integer> stock;
        private final Map<Integer, Double> prices;

        private Catalogue(Map<Integer, Integer> stock, Map<Integer, Double> prices) {
            this.stock = stock;
            this.prices = prices;
        }

        public Map<Integer, Integer> getStock() { return stock; }
        public Map<Integer, Double> getPrices() { return prices; }
    }

    public static final class HistoryEntry {
        private final String purchaseId;
        private final String productId;
        private final String timestamp;
        private final List<String> items;

        private HistoryEntry(String purchaseId, String productId, String timestamp, List<String> items) {
            this.purchaseId = purchaseId;
            this.productId = productId;
            this.timestamp = timestamp;
            this.items = items;
        }

        public String getPurchaseId() { return purchaseId; }
        public String getProductId() { return productId; }
        public String getTimestamp() { return timestamp; }
        public List<String> getItems() { return items; }
    }

    public static final class NiceAltsException extends Exception {
        NiceAltsException(String message) {
            super(message);
        }
    }
}
