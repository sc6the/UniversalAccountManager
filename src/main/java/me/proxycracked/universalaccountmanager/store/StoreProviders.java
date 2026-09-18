package me.proxycracked.universalaccountmanager.store;

/** The shops the mod can buy from, in the order the Buy Accounts screen lists them. */
public final class StoreProviders {
    public static final String LOCALTS = "localts";
    public static final String NICEALTS = "nicealts";
    public static final String FERNAN = "fernan";

    private StoreProviders() {
    }

    public static StoreProvider create(String providerId) {
        if (NICEALTS.equals(providerId)) {
            return new NiceAltsProvider();
        }
        if (FERNAN.equals(providerId)) {
            return new FernanProvider();
        }
        return new LocalTsProvider();
    }
}
