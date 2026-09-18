package me.proxycracked.universalaccountmanager.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.proxycracked.universalaccountmanager.fernan.FernanClient;
import me.proxycracked.universalaccountmanager.fernan.FernanClient.Cooldown;
import me.proxycracked.universalaccountmanager.fernan.FernanClient.Item;
import me.proxycracked.universalaccountmanager.fernan.FernanClient.Product;
import me.proxycracked.universalaccountmanager.fernan.FernanClient.Purchase;
import me.proxycracked.universalaccountmanager.fernan.FernanClient.PurchasePage;
import me.proxycracked.universalaccountmanager.fernan.FernanClient.PurchaseSummary;
import me.proxycracked.universalaccountmanager.fernan.FernanClient.User;

import org.apache.commons.lang3.StringUtils;

/**
 * Fernan Club: quantity purchases, purchase history, a Peso-key top-up and refunds.
 *
 * <p>Deliveries are a base64 cookie file next to a live access token, so an import upgrades the
 * cookies into a refresh token and only falls back to the access token when that fails.</p>
 */
public final class FernanProvider implements StoreProvider {
    private static final StoreCapabilities CAPABILITIES =
        new StoreCapabilities(true, true, true, false, false, true, true);
    private static final int HISTORY_PAGE_SIZE = 100;

    private String apiKey = "";
    private Map<Integer, Cooldown> cooldowns = Collections.emptyMap();

    @Override
    public String id() {
        return "fernan";
    }

    @Override
    public String displayName() {
        return "Fernan Club";
    }

    @Override
    public String websiteUrl() {
        return FernanClient.WEBSITE_URL;
    }

    @Override
    public StoreCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public StoreAccount connect(String apiKey) throws Exception {
        User user = FernanClient.getMe(apiKey);
        this.apiKey = apiKey;
        return new StoreAccount(user.getUsername(), user.getBalance(), "");
    }

    @Override
    public StoreAccount refreshAccount() throws Exception {
        User user = FernanClient.getMe(apiKey);
        return new StoreAccount(user.getUsername(), user.getBalance(), "");
    }

    @Override
    public List<StoreProduct> products() throws Exception {
        List<Product> fetched = FernanClient.getStock(apiKey);
        try {
            cooldowns = FernanClient.getCooldowns(apiKey);
        } catch (Exception ignored) {
            // Cooldowns only sharpen the per-order cap; the catalogue is still usable without them.
            cooldowns = Collections.emptyMap();
        }
        List<StoreProduct> products = new ArrayList<StoreProduct>();
        for (Product product : fetched) {
            products.add(new StoreProduct(
                Integer.toString(product.getId()),
                product.getName(),
                description(product),
                category(product),
                product.getPrice(),
                product.getCount(),
                StoreItemKind.COOKIE_FILE,
                limitFor(product),
                null
            ));
        }
        return products;
    }

    private String description(Product product) {
        StringBuilder text = new StringBuilder(product.getDescription());
        Cooldown cooldown = cooldowns.get(product.getId());
        if (cooldown != null && cooldown.isOnCooldown()) {
            if (text.length() > 0) {
                text.append("  |  ");
            }
            text.append("On cooldown until ").append(cooldown.getEndsAt());
        }
        return text.toString();
    }

    /** The shop's own per-order limit, tightened by whatever the cooldown still allows. */
    private int limitFor(Product product) {
        int limit = Math.max(0, product.getPurchaseLimit());
        Cooldown cooldown = cooldowns.get(product.getId());
        if (cooldown != null) {
            int remaining = Math.max(0, cooldown.getRemainingAllowed());
            limit = limit == 0 ? remaining : Math.min(limit, remaining);
        }
        return limit;
    }

    private static String category(Product product) {
        String server = capitalise(product.getServerCategory());
        String delivery = capitalise(product.getCategory());
        if (server.isEmpty()) {
            return delivery.isEmpty() ? "Accounts" : delivery;
        }
        return delivery.isEmpty() ? server : server + " " + delivery;
    }

    private static String capitalise(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }

    @Override
    public StoreOrder purchase(StoreProduct product, int amount, StoreStatus status) throws Exception {
        Purchase purchase = FernanClient.purchase(apiKey, Integer.parseInt(product.getId()), amount, null);
        if (purchase.getInvalidCount() > 0) {
            status.update("&e" + purchase.getInvalidCount() + " item(s) were dead on arrival and refunded by the shop.&r");
        }
        return toOrder(purchase);
    }

    /** Adds a Peso key's value to the balance. */
    public FernanClient.Redemption redeem(String key) throws Exception {
        return FernanClient.redeem(apiKey, key);
    }

    /** Opens a refund request for delivered items that could not be logged into. */
    public String requestRefund(String purchaseId, List<String> productUuids, String reason) throws Exception {
        return FernanClient.requestRefund(apiKey, purchaseId, productUuids, reason);
    }

    @Override
    public List<StoreOrderSummary> orderHistory(StoreStatus status) throws Exception {
        List<StoreOrderSummary> summaries = new ArrayList<StoreOrderSummary>();
        int offset = 0;
        while (true) {
            PurchasePage page = FernanClient.getPurchases(apiKey, HISTORY_PAGE_SIZE, offset);
            for (PurchaseSummary summary : page.getPurchases()) {
                summaries.add(new StoreOrderSummary(
                    summary.getId(),
                    Integer.toString(summary.getProductId()),
                    summary.getProductName(),
                    parseInstant(summary.getPurchasedAt()),
                    summary.getAmount()
                ));
            }
            offset += page.getPurchases().size();
            if (page.getPurchases().isEmpty() || offset >= page.getTotal()) {
                break;
            }
            status.update("&7Read " + offset + "/" + page.getTotal() + " purchases...&r");
        }
        return summaries;
    }

    private static long parseInstant(String isoInstant) {
        if (StringUtils.isBlank(isoInstant)) {
            return 0L;
        }
        try {
            return java.time.Instant.parse(isoInstant).toEpochMilli();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    @Override
    public StoreOrder order(String orderId) throws Exception {
        return toOrder(FernanClient.getPurchase(apiKey, orderId.trim()));
    }

    @Override
    public String orderIdLabel() {
        return "Purchase ID";
    }

    private StoreOrder toOrder(Purchase purchase) {
        List<StoreItem> items = new ArrayList<StoreItem>();
        int index = 0;
        for (Item item : purchase.getItems()) {
            index++;
            String product = StringUtils.isBlank(purchase.getProductName()) ? "Fernan Account" : purchase.getProductName();
            String label = StringUtils.isBlank(item.getUsername()) ? product + " #" + index : item.getUsername();
            items.add(new StoreItem(
                StringUtils.isBlank(item.getUuid()) ? purchase.getId() + "-" + index : item.getUuid(),
                item.getCookieData(),
                StoreItemKind.COOKIE_FILE,
                label,
                item.getUsername(),
                item.getUuid(),
                item.getAccessToken(),
                ""
            ));
        }
        return new StoreOrder(purchase.getId(), purchase.getProductName(), "DELIVERED", true, items);
    }
}
