package me.proxycracked.universalaccountmanager.nicealts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public final class NiceAltsClient {
    private static final String BASE_URL = "https://app.nicealts.com";
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
        .setConnectionRequestTimeout(15000)
        .setConnectTimeout(15000)
        .setSocketTimeout(30000)
        .build();

    private NiceAltsClient() {
    }

    public static Map<Integer, Integer> getStock() throws Exception {
        JsonObject json = execute(new HttpGet(BASE_URL + "/public/stock"));
        if (!"success".equalsIgnoreCase(optionalString(json, "status"))) {
            throw new NiceAltsException(errorMessage(json, "Nicealts rejected the stock request"));
        }
        JsonElement stockElement = json.get("stock");
        if (stockElement == null || !stockElement.isJsonObject()) throw new NiceAltsException("Nicealts response is missing stock");
        Map<Integer, Integer> stock = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : stockElement.getAsJsonObject().entrySet()) {
            try {
                stock.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsInt());
            } catch (RuntimeException ignored) {
            }
        }
        return Collections.unmodifiableMap(stock);
    }

    public static User getBalance(String apiKey) throws Exception {
        JsonObject json = execute(post("/api/balance", apiKey, null));
        return new User(requiredString(json, "username"), requiredNumber(json, "balance"), optionalString(json, "sub_status"));
    }

    public static List<String> purchase(String apiKey, int productId) throws Exception {
        JsonObject json = execute(post("/api/purchase", apiKey, productId));
        JsonElement itemsElement = json.get("items");
        if (itemsElement == null || !itemsElement.isJsonArray()) throw new NiceAltsException("Nicealts response is missing purchased items");
        List<String> items = new ArrayList<>();
        JsonArray array = itemsElement.getAsJsonArray();
        for (JsonElement item : array) if (item.isJsonPrimitive()) items.add(item.getAsString());
        if (items.isEmpty()) throw new NiceAltsException("Nicealts returned no purchased items");
        return items;
    }

    private static HttpPost post(String path, String apiKey, Integer productId) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("api_key", apiKey);
        if (productId != null) body.addProperty("product_id", Integer.toString(productId));
        HttpPost request = new HttpPost(BASE_URL + path);
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(body.toString(), StandardCharsets.UTF_8));
        return request;
    }

    private static JsonObject execute(HttpRequestBase request) throws Exception {
        request.setConfig(REQUEST_CONFIG);
        request.setHeader("Accept", "application/json");
        request.setHeader("User-Agent", "UniversalAccountManager/2.10");
        try (CloseableHttpClient client = HttpClients.createDefault(); CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
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
            return json;
        }
    }

    private static String requiredString(JsonObject json, String field) throws NiceAltsException {
        String value = optionalString(json, field);
        if (value.isEmpty()) throw new NiceAltsException("Nicealts response is missing " + field);
        return value;
    }

    private static double requiredNumber(JsonObject json, String field) throws NiceAltsException {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive()) throw new NiceAltsException("Nicealts response is missing " + field);
        return value.getAsDouble();
    }

    private static String optionalString(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String errorMessage(JsonObject json, String fallback) {
        String error = optionalString(json, "message");
        if (error.isEmpty()) error = optionalString(json, "error");
        return error.isEmpty() ? fallback : error;
    }

    public static final class User {
        private final String username;
        private final double balance;
        private final String subscription;

        private User(String username, double balance, String subscription) {
            this.username = username;
            this.balance = balance;
            this.subscription = subscription;
        }

        public String getUsername() { return username; }
        public double getBalance() { return balance; }
        public String getSubscription() { return subscription; }
    }

    public static final class NiceAltsException extends Exception {
        private NiceAltsException(String message) { super(message); }
    }
}
