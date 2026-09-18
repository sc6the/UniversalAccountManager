package me.proxycracked.universalaccountmanager.store;

/** A past delivery that has not become an account yet, offered on the import screen. */
public final class StoreImportCandidate {
    private final StoreItem item;
    private final String orderId;

    public StoreImportCandidate(StoreItem item, String orderId) {
        this.item = item;
        this.orderId = orderId == null ? "" : orderId;
    }

    public StoreItem getItem() { return item; }
    public String getOrderId() { return orderId; }

    public String getLabel() {
        return item.getLabel().isEmpty() ? item.displayName() : item.getLabel();
    }
}
