package me.proxycracked.universalaccountmanager.store;

/** The signed-in shop account: who you are, what you can spend, and any shop-specific extra. */
public final class StoreAccount {
    private final String username;
    private final double balance;
    private final String detail;

    public StoreAccount(String username, double balance, String detail) {
        this.username = username == null ? "" : username;
        this.balance = balance;
        this.detail = detail == null ? "" : detail;
    }

    public String getUsername() { return username; }
    public double getBalance() { return balance; }

    /** Subscription state, role, or whatever else the shop exposes; drawn after the balance. */
    public String getDetail() { return detail; }
}
