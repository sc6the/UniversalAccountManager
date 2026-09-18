package me.proxycracked.universalaccountmanager.store;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/** One buyable product, flattened from whatever the shop's catalogue looks like. */
public final class StoreProduct {
    private final String id;
    private final String name;
    private final String description;
    private final String category;
    private final double price;
    private final int stock;
    private final StoreItemKind kind;
    private final int purchaseLimit;
    private final Map<Integer, Double> quantityDiscounts;

    public StoreProduct(String id, String name, String description, String category, double price, int stock,
                        StoreItemKind kind, int purchaseLimit, Map<Integer, Double> quantityDiscounts) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.category = StringUtils.isBlank(category) ? "Other" : category;
        this.price = price;
        this.stock = stock;
        this.kind = kind == null ? StoreItemKind.UNSUPPORTED : kind;
        this.purchaseLimit = purchaseLimit;
        this.quantityDiscounts = quantityDiscounts == null
            ? Collections.<Integer, Double>emptyMap()
            : Collections.unmodifiableMap(quantityDiscounts);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
    public StoreItemKind getKind() { return kind; }

    /** Per-order cap the shop enforces, or 0 when only stock limits the order. */
    public int getPurchaseLimit() { return purchaseLimit; }

    public int maxAmount() {
        int max = stock;
        if (purchaseLimit > 0) {
            max = Math.min(max, purchaseLimit);
        }
        return Math.max(0, max);
    }

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
        return price * amount * (1.0D - discountFor(amount) / 100.0D);
    }
}
