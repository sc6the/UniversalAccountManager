package me.proxycracked.universalaccountmanager.store;

import java.util.Collections;
import java.util.List;

/** A finished (or still packaging) order and whatever it has delivered so far. */
public final class StoreOrder {
    private final String id;
    private final String productName;
    private final String status;
    private final boolean ready;
    private final List<StoreItem> items;

    public StoreOrder(String id, String productName, String status, boolean ready, List<StoreItem> items) {
        this.id = id == null ? "" : id;
        this.productName = productName == null ? "" : productName;
        this.status = status == null ? "" : status;
        this.ready = ready;
        this.items = items == null ? Collections.<StoreItem>emptyList() : Collections.unmodifiableList(items);
    }

    public String getId() { return id; }
    public String getProductName() { return productName; }
    public String getStatus() { return status; }

    /** True once the shop has handed the items over; only Localts ever answers false. */
    public boolean isReady() { return ready; }

    public List<StoreItem> getItems() { return items; }
}
