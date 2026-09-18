package me.proxycracked.universalaccountmanager.store;

import java.util.List;

/**
 * One alt shop, reduced to the operations the store screen needs.
 *
 * <p>An instance holds the API key for the session, so a provider is created per shop and reused
 * while the screen is open.</p>
 */
public interface StoreProvider {
    /** Stable key for the credential file, the delivery log and the import cache. */
    String id();

    String displayName();

    /** Where the user gets an API key, opened in the system browser. */
    String websiteUrl();

    StoreCapabilities capabilities();

    /** Validates the key, stores it on the provider and returns the shop account. */
    StoreAccount connect(String apiKey) throws Exception;

    StoreAccount refreshAccount() throws Exception;

    List<StoreProduct> products() throws Exception;

    /**
     * Buys and waits for delivery. Providers that package asynchronously report progress through
     * {@code status} and only return once the items exist.
     */
    StoreOrder purchase(StoreProduct product, int amount, StoreStatus status) throws Exception;

    /** Past orders as the shop knows them; empty when the shop has no history endpoint. */
    List<StoreOrderSummary> orderHistory(StoreStatus status) throws Exception;

    /** One past order by id; only called when {@link StoreCapabilities#supportsOrderLookup()} is true. */
    StoreOrder order(String orderId) throws Exception;

    /** What the shop calls an order id, for labels and the lookup field's hint. */
    String orderIdLabel();
}
