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
        UniversalAccountManager.accounts.removeIf(account -> Boolean.FALSE.equals(account.getAvailable()));
        if (UniversalAccountManager.accounts.size() != before) {
            UniversalAccountManager.resort();
            UniversalAccountManager.save();
        }
    }
}
