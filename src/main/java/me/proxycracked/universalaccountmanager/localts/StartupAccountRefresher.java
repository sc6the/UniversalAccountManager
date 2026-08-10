package me.proxycracked.universalaccountmanager.localts;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.localts.RefreshTokenImporter.ImportedAccount;
import org.apache.commons.lang3.StringUtils;

public final class StartupAccountRefresher {
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private StartupAccountRefresher() {
    }

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) return;
        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "UAM-Startup-Refresh");
            thread.setDaemon(true);
            return thread;
        });
        List<CompletableFuture<Void>> refreshes = new ArrayList<>();
        for (Account account : new ArrayList<>(UniversalAccountManager.accounts)) {
            if (!account.isMs() || StringUtils.isBlank(account.getRefreshToken())) continue;
            CompletableFuture<Void> refresh = RefreshTokenImporter.importToken(account.getRefreshToken(), executor)
                .thenAccept(result -> updateCurrent(account, result))
                .exceptionally(error -> null);
            refreshes.add(refresh);
        }
        CompletableFuture.allOf(refreshes.toArray(new CompletableFuture[refreshes.size()]))
            .thenRunAsync(() -> {
                if (!refreshes.isEmpty()) UniversalAccountManager.save();
                executor.shutdown();
            }, executor);
    }

    private static void updateCurrent(Account original, ImportedAccount refreshed) {
        synchronized (UniversalAccountManager.accounts) {
            Account target = original;
            for (Account account : UniversalAccountManager.accounts) {
                boolean sameUuid = !StringUtils.isBlank(original.getUuid()) && original.getUuid().equalsIgnoreCase(account.getUuid());
                boolean sameName = original.getUsername().equalsIgnoreCase(account.getUsername());
                if (sameUuid || sameName) {
                    target = account;
                    break;
                }
            }
            target.setRefreshToken(refreshed.getRefreshToken());
            target.setAccessToken(refreshed.getAccessToken());
            target.setUsername(refreshed.getSession().getUsername());
            target.setUuid(refreshed.getSession().getPlayerID());
        }
    }
}
