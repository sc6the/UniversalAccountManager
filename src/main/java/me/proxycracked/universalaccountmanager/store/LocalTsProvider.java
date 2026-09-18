package me.proxycracked.universalaccountmanager.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.proxycracked.universalaccountmanager.localts.LocalTsClient;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient.Order;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient.OrderItem;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient.OrderPage;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient.OrderSummary;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient.Product;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient.User;

import org.apache.commons.lang3.StringUtils;

/** Localts: a full catalogue, quantity purchases, and order history the shop keeps for you. */
public final class LocalTsProvider implements StoreProvider {
    private static final StoreCapabilities CAPABILITIES =
        new StoreCapabilities(true, true, true, false, false, false, false);

    private String apiKey = "";
    private List<Product> catalogue = Collections.emptyList();

    @Override
    public String id() {
        return "localts";
    }

    @Override
    public String displayName() {
        return "Localts";
    }

    @Override
    public String websiteUrl() {
        return LocalTsClient.BASE_URL;
    }

    @Override
    public StoreCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public StoreAccount connect(String apiKey) throws Exception {
        User user = LocalTsClient.getMe(apiKey);
        this.apiKey = apiKey;
        return new StoreAccount(user.getUsername(), user.getBalance(), "");
    }

    @Override
    public StoreAccount refreshAccount() throws Exception {
        User user = LocalTsClient.getMe(apiKey);
        return new StoreAccount(user.getUsername(), user.getBalance(), "");
    }

    @Override
    public List<StoreProduct> products() throws Exception {
        List<Product> fetched = LocalTsClient.getProducts(apiKey);
        catalogue = fetched;
        List<StoreProduct> products = new ArrayList<StoreProduct>();
        for (Product product : fetched) {
            if (!product.isSupportedAccountProduct()) {
                continue;
            }
            products.add(new StoreProduct(
                product.getId(),
                product.getName(),
                product.getDescription(),
                category(product),
                product.getPriceInCredits(),
                product.getStock(),
                kind(product),
                0,
                product.getQuantityDiscounts()
            ));
        }
        return products;
    }

    private static StoreItemKind kind(Product product) {
        return product.isCookieProduct() ? StoreItemKind.COOKIE_TOKEN : StoreItemKind.REFRESH_TOKEN;
    }

    private static String category(Product product) {
        String delivery = product.isCookieProduct() ? "Cookie" : "Refresh Token";
        return delivery + (product.isUnbanned() ? " Unbanned" : " Banned");
    }

    @Override
    public StoreOrder purchase(StoreProduct product, int amount, StoreStatus status) throws Exception {
        String orderId = LocalTsClient.purchase(apiKey, product.getId(), amount);
        status.update("&7Order " + orderId + " is being packaged...&r");
        Order order = waitForOrder(orderId, status);
        return toOrder(order, product.getKind());
    }

    /** Localts packages asynchronously, so the order is polled until the items appear. */
    private Order waitForOrder(String orderId, StoreStatus status) throws Exception {
        int consecutiveErrors = 0;
        for (int attempt = 0; attempt < 60; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            try {
                Order order = LocalTsClient.getOrder(apiKey, orderId);
                consecutiveErrors = 0;
                if (order.isPackaged()) {
                    return order;
                }
                status.update("&7Order " + orderId + ": " + order.getStatus() + "...&r");
            } catch (InterruptedException error) {
                throw error;
            } catch (Exception error) {
                // The order is already paid for, so keep polling through transient API errors.
                consecutiveErrors++;
                if (consecutiveErrors >= 5) {
                    throw new StoreException("Could not read order " + orderId + ": " + error.getMessage()
                        + ". Import it later with Import Order ID");
                }
                status.update("&e" + error.getMessage() + "&r");
                Thread.sleep(3000L * consecutiveErrors);
                continue;
            }
            Thread.sleep(2500L);
        }
        throw new StoreException("Order packaging timed out. Import order " + orderId + " later with Import Order ID");
    }

    @Override
    public List<StoreOrderSummary> orderHistory(StoreStatus status) throws Exception {
        List<StoreOrderSummary> summaries = new ArrayList<StoreOrderSummary>();
        int page = 0;
        int totalPages = 1;
        while (page < totalPages) {
            OrderPage result = LocalTsClient.getOrders(apiKey, page, 100);
            for (OrderSummary summary : result.getOrders()) {
                Product product = findProduct(summary.getProductId());
                // Products that can never be imported are dropped before an order read is spent on them.
                if (product != null && !product.isSupportedAccountProduct()) {
                    continue;
                }
                summaries.add(new StoreOrderSummary(
                    summary.getId(),
                    summary.getProductId(),
                    product == null ? "" : product.getName(),
                    summary.getTimestamp(),
                    0
                ));
            }
            totalPages = Math.max(0, result.getTotalPages());
            page++;
        }
        return summaries;
    }

    @Override
    public StoreOrder order(String orderId) throws Exception {
        Order order = LocalTsClient.getOrder(apiKey, orderId);
        return toOrder(order, kindFor(order.getProductName()));
    }

    @Override
    public String orderIdLabel() {
        return "Order ID";
    }

    private StoreOrder toOrder(Order order, StoreItemKind fallbackKind) {
        List<StoreItem> items = new ArrayList<StoreItem>();
        int index = 0;
        for (OrderItem item : order.getItems()) {
            index++;
            String label = (StringUtils.isBlank(order.getProductName()) ? "Localts Account" : order.getProductName())
                + " #" + index;
            items.add(StoreItem.of(item.getId(), item.getContent(), kindOf(item.getContent(), fallbackKind), label));
        }
        return new StoreOrder(order.getId(), order.getProductName(), order.getStatus(), order.isPackaged(), items);
    }

    private StoreItemKind kindFor(String productName) {
        Product product = findProductByName(productName);
        return product == null ? StoreItemKind.UNSUPPORTED : kind(product);
    }

    /** The catalogue is the first word; the delivery's own shape decides when it is silent. */
    private static StoreItemKind kindOf(String content, StoreItemKind fallbackKind) {
        if (fallbackKind == StoreItemKind.COOKIE_TOKEN || fallbackKind == StoreItemKind.REFRESH_TOKEN) {
            return fallbackKind;
        }
        String value = content == null ? "" : content.trim();
        if (value.isEmpty()) {
            return StoreItemKind.UNSUPPORTED;
        }
        int colons = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == ':') {
                colons++;
            }
        }
        if (colons == 0) {
            return StoreItemKind.COOKIE_TOKEN;
        }
        return colons == 1 ? StoreItemKind.REFRESH_TOKEN : StoreItemKind.UNSUPPORTED;
    }

    private Product findProduct(String productId) {
        for (Product product : catalogue) {
            if (product.getId().equals(productId)) {
                return product;
            }
        }
        return null;
    }

    private Product findProductByName(String productName) {
        if (StringUtils.isBlank(productName)) {
            return null;
        }
        for (Product product : catalogue) {
            if (productName.equalsIgnoreCase(product.getName())) {
                return product;
            }
        }
        return null;
    }
}
