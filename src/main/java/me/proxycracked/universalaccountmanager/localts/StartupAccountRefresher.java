package me.proxycracked.universalaccountmanager.localts;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.AccountLogin;
import me.proxycracked.universalaccountmanager.auth.AccountTypes;
import me.proxycracked.universalaccountmanager.auth.TokenAuth;

import org.apache.commons.lang3.StringUtils;

/**
 * Tops up Minecraft tokens once per launch, for the accounts that actually need it.
 *
 * <p>The first version refreshed every Microsoft account in parallel on every launch. Microsoft
 * rotates a refresh token on use and invalidates the old one, so a burst like that both drew 429s
 * and raced whatever the player did next in the manager - the way an account gets permanently
 * "expired". Now the stored token is checked first and only the dead ones are refreshed, one at a
 * time, with the rotated token written back immediately.</p>
 */
public final class StartupAccountRefresher {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final long SPACING_MILLIS = 750L;

    private StartupAccountRefresher() {
    }

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "UAM-Startup-Refresh");
                thread.setDaemon(true);
                return thread;
            }
        });
        executor.submit(new Runnable() {
            @Override
            public void run() {
                refreshStaleAccounts();
            }
        });
        executor.shutdown();
    }

    private static void refreshStaleAccounts() {
        List<Account> candidates = new ArrayList<Account>();
        synchronized (UniversalAccountManager.accounts) {
            for (Account account : UniversalAccountManager.accounts) {
                if (AccountTypes.isRefreshable(account) && !AccountTypes.isOffline(account)) {
                    candidates.add(account);
                }
            }
        }

        boolean changed = false;
        for (Account account : candidates) {
            if (!StringUtils.isBlank(account.getAccessToken()) && TokenAuth.validate(account.getAccessToken())) {
                account.setAvailable(Boolean.TRUE);
                continue;
            }
            try {
                AccountLogin.Result result = AccountLogin.fromRefreshToken(
                    account.getRefreshToken(), account.getType(), null);
                AccountLogin.apply(account, result);
                changed = true;
            } catch (Exception ignored) {
                // Keep the stored tokens: the account is still refreshable on demand, and a
                // transient Microsoft failure must not look like an expired account.
                account.setAvailable(Boolean.TRUE);
            }
            try {
                Thread.sleep(SPACING_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (changed) {
            UniversalAccountManager.save();
        }
    }
}
