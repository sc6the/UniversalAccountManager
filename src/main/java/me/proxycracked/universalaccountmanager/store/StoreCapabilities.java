package me.proxycracked.universalaccountmanager.store;

/**
 * What a shop can do beyond listing products and selling one.
 *
 * <p>The store screen is the same for every shop; these flags are the only thing that changes what
 * it shows, so a feature one shop lacks costs a hidden button rather than a separate screen.</p>
 */
public final class StoreCapabilities {
    private final boolean quantity;
    private final boolean serverHistory;
    private final boolean orderLookup;
    private final boolean customPurchase;
    private final boolean generate;
    private final boolean redeem;
    private final boolean refunds;

    public StoreCapabilities(boolean quantity, boolean serverHistory, boolean orderLookup,
                             boolean customPurchase, boolean generate, boolean redeem, boolean refunds) {
        this.quantity = quantity;
        this.serverHistory = serverHistory;
        this.orderLookup = orderLookup;
        this.customPurchase = customPurchase;
        this.generate = generate;
        this.redeem = redeem;
        this.refunds = refunds;
    }

    /** More than one account per order. Nicealts is one call, one alt. */
    public boolean supportsQuantity() { return quantity; }

    /** The shop can list past orders. Shops that cannot are covered by the local delivery log. */
    public boolean supportsServerHistory() { return serverHistory; }

    /** A single past order can be fetched by its id. */
    public boolean supportsOrderLookup() { return orderLookup; }

    /** Nicealts only: buy an alt prechecked against a specific server. */
    public boolean supportsCustomPurchase() { return customPurchase; }

    /** Nicealts only: subscription generator, no credits spent. */
    public boolean supportsGenerate() { return generate; }

    /** Fernan Club only: top the balance up with a Peso key. */
    public boolean supportsRedeem() { return redeem; }

    /** Fernan Club only: ask for credits back on a dead delivery. */
    public boolean supportsRefunds() { return refunds; }
}
