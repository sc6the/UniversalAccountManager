package me.proxycracked.universalaccountmanager.store;

/** Anything a shop rejected or answered badly, with a message fit to show in the GUI. */
public class StoreException extends Exception {
    public StoreException(String message) {
        super(message);
    }

    public StoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
