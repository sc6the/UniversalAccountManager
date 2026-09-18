package me.proxycracked.universalaccountmanager.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import net.minecraft.client.Minecraft;

public final class ExpiredAccountCleaner {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "UniversalAccountManager-ExpiredCleaner");
        thread.setDaemon(true);
        return thread;
    });

    private ExpiredAccountCleaner() {
    }

    public static void schedule() {
        final long generation = GENERATION.incrementAndGet();
        EXECUTOR.submit(() -> {
            long deadline = System.currentTimeMillis() + 15000L;
            while (generation == GENERATION.get() && System.currentTimeMillis() < deadline) {
                if (allValidated()) break;
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (generation != GENERATION.get()) return;
            Minecraft.getMinecraft().addScheduledTask(() -> clearValidatedExpired(generation));
        });
    }

    private static boolean allValidated() {
        try {
            List<Account> snapshot = new ArrayList<>(UniversalAccountManager.accounts);
            for (Account account : snapshot) {
                if (account.getAvailable() == null) return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void clearValidatedExpired(long generation) {
        if (generation != GENERATION.get()) return;
        int before = UniversalAccountManager.accounts.size();
        UniversalAccountManager.accounts.removeIf(ExpiredAccountCleaner::isBeyondRepair);
        if (UniversalAccountManager.accounts.size() != before) {
            UniversalAccountManager.resort();
            UniversalAccountManager.save();
        }
    }

    /**
     * Only sweeps pasted-token accounts that have nothing left to log in with.
     *
     * <p>Offline accounts have no token by design, and anything holding a refresh token can mint a
     * new session on demand, so neither is ever "expired" - deleting those was silent data loss.</p>
     */
    private static boolean isBeyondRepair(Account account) {
        return Boolean.FALSE.equals(account.getAvailable())
            && !AccountTypes.isOffline(account)
            && !AccountTypes.isRefreshable(account);
    }
}
