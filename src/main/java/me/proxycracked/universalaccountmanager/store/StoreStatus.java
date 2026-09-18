package me.proxycracked.universalaccountmanager.store;

/** Progress line from a provider back to whatever screen started the work. */
public interface StoreStatus {
    StoreStatus IGNORED = new StoreStatus() {
        @Override
        public void update(String message) {
        }
    };

    void update(String message);
}
