package me.proxycracked.universalaccountmanager.store;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import me.proxycracked.universalaccountmanager.nicealts.NiceAltsClient;
import me.proxycracked.universalaccountmanager.nicealts.NiceAltsClient.Catalogue;
import me.proxycracked.universalaccountmanager.nicealts.NiceAltsClient.HistoryEntry;
import me.proxycracked.universalaccountmanager.nicealts.NiceAltsClient.User;

import org.apache.commons.lang3.StringUtils;

/**
 * Nicealts: a fixed catalogue, one alt per call, and two things no other shop has - a
 * subscription generator and a per-server custom purchase.
 *
 * <p>The catalogue and delivery format follow Nicealts' public API documentation. Stock and prices
 * come from the public endpoint so the confirmation screen always matches the current shop.</p>
 */
public final class NiceAltsProvider implements StoreProvider {
    private static final StoreCapabilities CAPABILITIES =
        new StoreCapabilities(true, true, false, true, true, false, false);
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Product id, name, price in credits, category. */
    private static final Object[][] CATALOGUE = {
        {1, "Hypixel Unbanned 1-7", 10.0D, "Hypixel Unbanned"},
        {2, "Hypixel Bedwars 8+", 12.0D, "Hypixel Unbanned"},
        {3, "Hypixel Ranked", 20.0D, "Hypixel Unbanned"},
        {4, "LQ Unbanned", 3.0D, "Hypixel Unbanned"},
        {5, "DonutSMP Unbanned", 5.0D, "DonutSMP"},
        {6, "Banned", 2.0D, "Banned"},
    };
    /** Products the unbanned generator currently reports stock for. */
    private static final int[] GENERATOR_POOL = {1, 2, 3, 4, 67};
    private static final int DONUT_PRODUCT = 5;
    /** One call buys one alt, so a bigger order is a loop; this caps how much it can spend at once. */
    private static final int MAX_PER_ORDER = 10;
    private static final int BANNED_PRODUCT = 6;

    /** Credits charged by {@code /api/custompurchase}, before the chat-mode surcharge. */
    public static final double CUSTOM_PURCHASE_CREDITS = 5.0D;
    public static final double CUSTOM_PURCHASE_CHAT_SURCHARGE = 1.0D;

    private String apiKey = "";
    private Map<Integer, Integer> stock = Collections.emptyMap();
    private Map<Integer, Double> prices = Collections.emptyMap();
    private User user;

    @Override
    public String id() {
        return "nicealts";
    }

    @Override
    public String displayName() {
        return "Nicealts";
    }

    @Override
    public String websiteUrl() {
        return NiceAltsClient.BASE_URL;
    }

    @Override
    public StoreCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public StoreAccount connect(String apiKey) throws Exception {
        User fetched = NiceAltsClient.getBalance(apiKey);
        this.apiKey = apiKey;
        this.user = fetched;
        return toAccount(fetched);
    }

    @Override
    public StoreAccount refreshAccount() throws Exception {
        user = NiceAltsClient.getBalance(apiKey);
        return toAccount(user);
    }

    private static StoreAccount toAccount(User user) {
        String detail = "Generator: " + user.subscriptionName();
        String expiry = daysLeft(user.getSubscriptionExpiry());
        if (user.hasGeneratorAccess() && !expiry.isEmpty()) {
            detail += " (" + expiry + ")";
        }
        return new StoreAccount(user.getUsername(), user.getBalance(), detail);
    }

    private static String daysLeft(String isoInstant) {
        if (StringUtils.isBlank(isoInstant)) {
            return "";
        }
        try {
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.Instant.now(), java.time.Instant.parse(isoInstant)
            );
            return days > 0L ? days + "d left" : "expired";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    @Override
    public List<StoreProduct> products() throws Exception {
        Catalogue catalogue = NiceAltsClient.getCatalogue();
        stock = catalogue.getStock();
        prices = catalogue.getPrices();
        List<StoreProduct> products = new ArrayList<StoreProduct>();
        for (Object[] row : CATALOGUE) {
            int id = (Integer) row[0];
            products.add(new StoreProduct(
                Integer.toString(id),
                (String) row[1],
                "One account per purchase.",
                (String) row[3],
                priceOf(id, (Double) row[2]),
                stockOf(id),
                StoreItemKind.REFRESH_TOKEN,
                MAX_PER_ORDER,
                null
            ));
        }
        return products;
    }

    public boolean hasGeneratorAccess() {
        return user != null && user.hasGeneratorAccess();
    }

    /** Stock behind each generator category, which does not line up with the sold products. */
    public int generatorStock(String category) {
        if ("DonutSMP".equalsIgnoreCase(category)) {
            return stockOf(DONUT_PRODUCT);
        }
        if ("Banned".equalsIgnoreCase(category)) {
            return stockOf(BANNED_PRODUCT);
        }
        int total = 0;
        for (int id : GENERATOR_POOL) {
            total += stockOf(id);
        }
        return total;
    }

    private int stockOf(int productId) {
        Integer count = stock.get(productId);
        return count == null ? 0 : count;
    }

    private double priceOf(int productId, double fallback) {
        Double price = prices.get(productId);
        return price == null ? fallback : price;
    }

    @Override
    public StoreOrder purchase(StoreProduct product, int amount, StoreStatus status) throws Exception {
        // The endpoint has no quantity field, so a larger order is a series of single purchases.
        List<StoreItem> items = new ArrayList<StoreItem>();
        int wanted = Math.max(1, amount);
        Exception failure = null;
        for (int index = 0; index < wanted; index++) {
            if (wanted > 1) {
                status.update("&7Buying " + (index + 1) + "/" + wanted + " from Nicealts...&r");
            }
            try {
                List<String> delivered = NiceAltsClient.purchase(apiKey, Integer.parseInt(product.getId()));
                for (String content : delivered) {
                    items.add(parseItem(content, product.getName() + " #" + (items.size() + 1)));
                }
            } catch (Exception error) {
                failure = error;
                break;
            }
        }
        if (items.isEmpty()) {
            throw failure == null ? new StoreException("Nicealts delivered nothing") : failure;
        }
        if (failure != null) {
            // Part of the order went through, so the items are kept and the reason is reported.
            status.update("&eNicealts stopped after " + items.size() + "/" + wanted + ": " + failure.getMessage() + "&r");
        }
        return new StoreOrder(newOrderId(), product.getName(), "DELIVERED", true, items);
    }

    /** Generates one account on the subscription; nothing is charged and nothing is stocked out. */
    public StoreOrder generate(String category) throws Exception {
        String token = NiceAltsClient.generate(apiKey, category);
        StoreItem item = new StoreItem(
            itemId(token), token, StoreItemKind.ACCESS_TOKEN, "Generated " + category, "", "", token, ""
        );
        return new StoreOrder(newOrderId(), "Generated " + category, "DELIVERED", true,
            Collections.singletonList(item));
    }

    /**
     * Buys one alt that Nicealts has just checked against a specific server.
     *
     * @param protocol   {@code 1.8}, {@code 1.21} or {@code 26.1}
     * @param chatBanCheck use the chat ban system, which costs one extra credit and is the only
     *                     mode Minemen accepts
     */
    public StoreOrder customPurchase(String server, String protocol, boolean chatBanCheck) throws Exception {
        List<String> tokens = NiceAltsClient.customPurchase(apiKey, server, protocol, chatBanCheck ? "chat" : "normal");
        List<StoreItem> items = new ArrayList<StoreItem>();
        for (String token : tokens) {
            items.add(parseItem(token, "Custom " + server + " #" + (items.size() + 1)));
        }
        if (items.isEmpty()) {
            throw new StoreException("Nicealts delivered nothing for that server");
        }
        return new StoreOrder(newOrderId(), "Custom purchase (" + server + ")", "DELIVERED", true, items);
    }

    /**
     * Nicealts ships {@code mctoken: ... | refreshtoken: ...}; the custom purchase and the
     * generator ship a bare Minecraft token. Splitting on the first colon would take the label.
     */
    static StoreItem parseItem(String content, String label) {
        String value = content == null ? "" : content.trim();
        String accessToken = between(value, "mctoken:", "|");
        String refreshToken = after(value, "refreshtoken:");
        if (accessToken.isEmpty() && refreshToken.isEmpty()) {
            // A bare token: an access token starts a JWT, anything else is treated as a refresh token.
            boolean looksLikeJwt = value.startsWith("ey");
            return new StoreItem(
                itemId(value), value,
                looksLikeJwt ? StoreItemKind.ACCESS_TOKEN : StoreItemKind.REFRESH_TOKEN,
                label, "", "",
                looksLikeJwt ? value : "",
                looksLikeJwt ? "" : value
            );
        }
        StoreItemKind kind = refreshToken.isEmpty() ? StoreItemKind.ACCESS_TOKEN : StoreItemKind.REFRESH_TOKEN;
        String primary = refreshToken.isEmpty() ? accessToken : refreshToken;
        return new StoreItem(itemId(value), primary, kind, label, "", "", accessToken, refreshToken);
    }

    private static String between(String value, String prefix, String terminator) {
        int start = value.indexOf(prefix);
        if (start < 0) {
            return "";
        }
        start += prefix.length();
        int end = value.indexOf(terminator, start);
        return (end < 0 ? value.substring(start) : value.substring(start, end)).trim();
    }

    private static String after(String value, String prefix) {
        int start = value.indexOf(prefix);
        return start < 0 ? "" : value.substring(start + prefix.length()).trim();
    }

    /** Nicealts gives deliveries no id, so one is derived from the content and stays stable. */
    private static String itemId(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            String hex = new BigInteger(1, hash).toString(16);
            return hex.length() > 16 ? hex.substring(0, 16) : hex;
        } catch (Exception ignored) {
            return Integer.toHexString(content.hashCode());
        }
    }

    private static String newOrderId() {
        return "NA-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    }

    @Override
    public List<StoreOrderSummary> orderHistory(StoreStatus status) throws Exception {
        List<HistoryEntry> history = NiceAltsClient.getHistory(apiKey);
        List<StoreOrderSummary> summaries = new ArrayList<StoreOrderSummary>();
        for (HistoryEntry entry : history) {
            String productName = productName(entry.getProductId());
            List<StoreItem> items = new ArrayList<StoreItem>();
            for (String content : entry.getItems()) {
                items.add(parseItem(content, productName + " #" + (items.size() + 1)));
            }
            long timestamp = historyTimestamp(entry.getTimestamp());
            StoreOrder order = new StoreOrder(entry.getPurchaseId(), productName, "DELIVERED", true, items);
            DeliveryLog.recordDeferred(id(), order, timestamp);
            summaries.add(new StoreOrderSummary(
                entry.getPurchaseId(), entry.getProductId(), productName, timestamp, items.size()
            ));
        }
        DeliveryLog.flush();
        return summaries;
    }

    @Override
    public StoreOrder order(String orderId) {
        return DeliveryLog.order(id(), orderId);
    }

    private static String productName(String productId) {
        for (Object[] row : CATALOGUE) {
            if (Integer.toString((Integer) row[0]).equals(productId)) {
                return (String) row[1];
            }
        }
        return "Nicealts Account";
    }

    private static long historyTimestamp(String timestamp) {
        if (StringUtils.isBlank(timestamp)) {
            return 0L;
        }
        try {
            return LocalDateTime.parse(timestamp.trim(), HISTORY_TIME).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    @Override
    public String orderIdLabel() {
        return "Delivery ID";
    }
}
