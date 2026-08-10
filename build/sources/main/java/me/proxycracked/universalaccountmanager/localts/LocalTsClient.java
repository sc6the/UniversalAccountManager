package me.proxycracked.universalaccountmanager.localts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public final class LocalTsClient {
    public static final String BASE_URL = "https://localts.store";
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
        .setConnectionRequestTimeout(15000)
        .setConnectTimeout(15000)
        .setSocketTimeout(30000)
        .build();

    private LocalTsClient() {
    }

    public static User getMe(String apiKey) throws Exception {
        JsonObject json = execute(new HttpGet(BASE_URL + "/v1/me"), apiKey);
        return new User(requiredString(json, "username"), requiredNumber(json, "balance"));
    }

    public static List<Product> getProducts() throws Exception {
        JsonObject json = execute(new HttpGet(BASE_URL + "/v1/products"), null);
        JsonArray array = requiredArray(json, "products");
        List<Product> products = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject product = element.getAsJsonObject();
            List<String> tags = new ArrayList<>();
            JsonElement tagsElement = product.get("tags");
            if (tagsElement != null && tagsElement.isJsonArray()) {
                for (JsonElement tag : tagsElement.getAsJsonArray()) {
                    if (tag.isJsonPrimitive()) {
                        tags.add(tag.getAsString());
                    }
                }
            }

            TreeMap<Integer, Double> discounts = new TreeMap<>();
            JsonElement discountsElement = product.get("quantityDiscounts");
            if (discountsElement != null && discountsElement.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : discountsElement.getAsJsonObject().entrySet()) {
                    try {
                        discounts.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsDouble());
                    } catch (RuntimeException ignored) {
                    }
                }
            }

            products.add(new Product(
                requiredString(product, "id"),
                requiredString(product, "name"),
                optionalString(product, "description"),
                optionalString(product, "category"),
                requiredNumber(product, "priceInCredits"),
                requiredInt(product, "stock"),
                optionalString(product, "type"),
                Collections.unmodifiableList(tags),
                Collections.unmodifiableMap(discounts)
            ));
        }
        products.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER));
        return products;
    }

    public static String purchase(String apiKey, String productId, int amount) throws Exception {
        URI uri = new URIBuilder(BASE_URL + "/v1/products/" + productId + "/purchase")
            .addParameter("amount", Integer.toString(amount))
            .build();
        JsonObject json = execute(new HttpPost(uri), apiKey);
        return requiredString(json, "orderId");
    }

    public static Order getOrder(String apiKey, String orderId) throws Exception {
        URI uri = new URIBuilder(BASE_URL + "/v1/orders/get-order")
            .addParameter("id", orderId)
            .build();
        JsonObject json = execute(new HttpGet(uri), apiKey);
        String status = requiredString(json, "status");
        String productName = optionalString(json, "product-name");
        List<OrderItem> items = new ArrayList<>();
        JsonElement itemsElement = json.get("items");
        if (itemsElement != null && itemsElement.isJsonArray()) {
            for (JsonElement element : itemsElement.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    JsonObject item = element.getAsJsonObject();
                    items.add(new OrderItem(requiredString(item, "id"), requiredString(item, "content")));
                }
            }
        }
        return new Order(orderId, status, productName, Collections.unmodifiableList(items));
    }

    public static OrderPage getOrders(String apiKey, int page, int size) throws Exception {
        URI uri = new URIBuilder(BASE_URL + "/v1/orders")
            .addParameter("page", Integer.toString(page))
            .addParameter("size", Integer.toString(size))
            .build();
        JsonObject json = execute(new HttpGet(uri), apiKey);
        List<OrderSummary> orders = new ArrayList<>();
        for (JsonElement element : requiredArray(json, "orders")) {
            if (!element.isJsonObject()) continue;
            JsonObject order = element.getAsJsonObject();
            orders.add(new OrderSummary(
                requiredString(order, "id"),
                requiredString(order, "productId"),
                optionalString(order, "productType"),
                order.has("timestamp") ? order.get("timestamp").getAsLong() : 0L
            ));
        }
        return new OrderPage(
            Collections.unmodifiableList(orders),
            requiredInt(json, "page"),
            requiredInt(json, "totalPages")
        );
    }

    private static JsonObject execute(HttpRequestBase request, String apiKey) throws Exception {
        request.setConfig(REQUEST_CONFIG);
        request.setHeader("Accept", "application/json");
        request.setHeader("User-Agent", "UniversalAccountManager/2.10");
        if (!StringUtils.isBlank(apiKey)) {
            request.setHeader("X-API-Key", apiKey);
        }

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), "UTF-8");
            JsonObject json;
            try {
                JsonElement parsed = new JsonParser().parse(body);
                json = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            } catch (RuntimeException error) {
                throw new LocalTsException("Localts returned an invalid response (HTTP " + status + ")");
            }

            if (status < 200 || status >= 300) {
                throw new LocalTsException(errorMessage(json, "Localts request failed (HTTP " + status + ")"));
            }
            if (!json.has("success") || !json.get("success").getAsBoolean()) {
                throw new LocalTsException(errorMessage(json, "Localts rejected the request"));
            }
            return json;
        }
    }

    private static String errorMessage(JsonObject json, String fallback) {
        String message = optionalString(json, "error");
        return StringUtils.isBlank(message) ? fallback : message;
    }

    private static String requiredString(JsonObject json, String field) throws LocalTsException {
        String value = optionalString(json, field);
        if (StringUtils.isBlank(value)) {
            throw new LocalTsException("Localts response is missing " + field);
        }
        return value;
    }

    private static String optionalString(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static double requiredNumber(JsonObject json, String field) throws LocalTsException {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            throw new LocalTsException("Localts response is missing " + field);
        }
        return value.getAsDouble();
    }

    private static int requiredInt(JsonObject json, String field) throws LocalTsException {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            throw new LocalTsException("Localts response is missing " + field);
        }
        return value.getAsInt();
    }

    private static JsonArray requiredArray(JsonObject json, String field) throws LocalTsException {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonArray()) {
            throw new LocalTsException("Localts response is missing " + field);
        }
        return value.getAsJsonArray();
    }

    public static final class User {
        private final String username;
        private final double balance;

        private User(String username, double balance) {
            this.username = username;
            this.balance = balance;
        }

        public String getUsername() {
            return username;
        }

        public double getBalance() {
            return balance;
        }
    }

    public static final class Product {
        private final String id;
        private final String name;
        private final String description;
        private final String category;
        private final double priceInCredits;
        private final int stock;
        private final String type;
        private final List<String> tags;
        private final Map<Integer, Double> quantityDiscounts;

        private Product(String id, String name, String description, String category, double priceInCredits, int stock, String type,
                        List<String> tags, Map<Integer, Double> quantityDiscounts) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.priceInCredits = priceInCredits;
            this.stock = stock;
            this.type = type;
            this.tags = tags;
            this.quantityDiscounts = quantityDiscounts;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public double getPriceInCredits() { return priceInCredits; }
        public int getStock() { return stock; }
        public String getType() { return type; }
        public List<String> getTags() { return tags; }

        public double discountFor(int amount) {
            double discount = 0.0D;
            for (Map.Entry<Integer, Double> tier : quantityDiscounts.entrySet()) {
                if (amount >= tier.getKey()) {
                    discount = Math.max(discount, tier.getValue());
                }
            }
            return Math.max(0.0D, Math.min(100.0D, discount));
        }

        public double totalFor(int amount) {
            return priceInCredits * amount * (1.0D - discountFor(amount) / 100.0D);
        }

        public boolean isRefreshTokenProduct() {
            StringBuilder searchable = new StringBuilder();
            searchable.append(name).append(' ').append(description).append(' ').append(category).append(' ').append(type);
            for (String tag : tags) {
                searchable.append(' ').append(tag);
            }
            String text = searchable.toString().toLowerCase();
            return text.contains("refresh token") || text.contains("refresh-token") || text.contains("oauth token");
        }

        public boolean isCookieProduct() {
            return hasTag("cookie") || description.toLowerCase().contains("format of a cookie");
        }

        public boolean isUnbanned() {
            return hasTag("unbanned") || name.toLowerCase().contains("unbanned");
        }

        public boolean isSupportedAccountProduct() {
            return isRefreshTokenProduct() || isCookieProduct();
        }

        private boolean hasTag(String expected) {
            for (String tag : tags) {
                if (expected.equalsIgnoreCase(tag.trim())) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class Order {
        private final String id;
        private final String status;
        private final String productName;
        private final List<OrderItem> items;

        private Order(String id, String status, String productName, List<OrderItem> items) {
            this.id = id;
            this.status = status;
            this.productName = productName;
            this.items = items;
        }

        public String getId() { return id; }
        public String getStatus() { return status; }
        public String getProductName() { return productName; }
        public List<OrderItem> getItems() { return items; }
        public boolean isPackaged() { return "PACKAGED".equalsIgnoreCase(status); }
    }

    public static final class OrderItem {
        private final String id;
        private final String content;

        private OrderItem(String id, String content) {
            this.id = id;
            this.content = content;
        }

        public String getId() { return id; }
        public String getContent() { return content; }
    }

    public static final class OrderSummary {
        private final String id;
        private final String productId;
        private final String productType;
        private final long timestamp;

        private OrderSummary(String id, String productId, String productType, long timestamp) {
            this.id = id;
            this.productId = productId;
            this.productType = productType;
            this.timestamp = timestamp;
        }

        public String getId() { return id; }
        public String getProductId() { return productId; }
        public String getProductType() { return productType; }
        public long getTimestamp() { return timestamp; }
    }

    public static final class OrderPage {
        private final List<OrderSummary> orders;
        private final int page;
        private final int totalPages;

        private OrderPage(List<OrderSummary> orders, int page, int totalPages) {
            this.orders = orders;
            this.page = page;
            this.totalPages = totalPages;
        }

        public List<OrderSummary> getOrders() { return orders; }
        public int getPage() { return page; }
        public int getTotalPages() { return totalPages; }
    }

    public static final class LocalTsException extends Exception {
        public LocalTsException(String message) {
            super(message);
        }
    }
}
