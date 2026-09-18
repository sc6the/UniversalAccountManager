package me.proxycracked.universalaccountmanager.auth;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import me.proxycracked.universalaccountmanager.UniversalAccountManager;

import org.apache.commons.lang3.StringUtils;

/**
 * Marks accounts as usable or dead for the list rendering and the expired-account cleanup.
 *
 * <p>Replaces the bundled version, which only ever asked "does the stored Minecraft access token
 * still work". That token lives about a day, so every refreshable account went grey (and could be
 * swept away by {@link ExpiredAccountCleaner}) the day after it was added. An account that holds a
 * refresh token is fine, and an offline account has nothing to validate at all.</p>
 */
public final class AccountValidator {
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "UniversalAccountManager-Validate-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    private AccountValidator() {
    }

    public static void validateAll() {
        for (Account account : new ArrayList<Account>(UniversalAccountManager.accounts)) {
            validate(account);
        }
    }

    public static void validate(final Account account) {
        if (account == null) {
            return;
        }
        if (AccountTypes.isOffline(account)) {
            account.setAvailable(Boolean.TRUE);
            return;
        }
        if (AccountTypes.isRefreshable(account)) {
            // A refresh token can always mint a new session, so this account is not expired.
            account.setAvailable(Boolean.TRUE);
            return;
        }
        if (StringUtils.isBlank(account.getAccessToken())) {
            account.setAvailable(Boolean.FALSE);
            return;
        }

        EXEC.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    account.setAvailable(TokenAuth.validate(account.getAccessToken()) ? Boolean.TRUE : Boolean.FALSE);
                } catch (Exception ignored) {
                    // Network trouble is not the account's fault; leave it alone.
                    if (account.getAvailable() == null) {
                        account.setAvailable(Boolean.TRUE);
                    }
                }
            }
        });
    }
}
