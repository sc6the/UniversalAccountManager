package me.proxycracked.universalaccountmanager.store;

/** A row of purchase history, before the delivered items are fetched. */
public final class StoreOrderSummary {
    private final String id;
    private final String productId;
    private final String productName;
    private final long timestamp;
    private final int itemCount;

    public StoreOrderSummary(String id, String productId, String productName, long timestamp, int itemCount) {
        this.id = id == null ? "" : id;
        this.productId = productId == null ? "" : productId;
        this.productName = productName == null ? "" : productName;
        this.timestamp = timestamp;
        this.itemCount = itemCount;
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public long getTimestamp() { return timestamp; }
    public int getItemCount() { return itemCount; }
}
