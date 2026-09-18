package me.proxycracked.universalaccountmanager.gui;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.AccountLogin;
import me.proxycracked.universalaccountmanager.auth.AuthHttp;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.utils.Notification;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;

import net.minecraft.client.gui.GuiScreen;

import org.apache.commons.lang3.StringUtils;

/**
 * Drives the account-list login, replacing the bundled {@code GuiAccountManager#doLogin}.
 *
 * <p>That version branched on the account type and only knew how to refresh through the Azure app,
 * so alt-shop and cookie accounts had no way back once their Minecraft token aged out, and offline
 * accounts had no path at all. Everything goes through {@link AccountLogin} here instead.</p>
 *
 * <p>The screen's own {@code notification}, {@code task} and {@code executor} fields are written by
 * reflection so its existing rendering and its cancel-on-close keep working untouched.</p>
 */
public final class LoginController {
    private static Field selectedAccountField;
    private static Field notificationField;
    private static Field taskField;
    private static Field executorField;

    private LoginController() {
    }

    /**
     * Starts a login for the row the screen has selected.
     *
     * @return true when this took over, i.e. the bundled implementation must not run
     */
    public static boolean start(final GuiScreen screen) {
        CompletableFuture<?> running = read(taskField(screen), screen);
        if (running != null && !running.isDone()) {
            return true;
        }

        Integer selected = read(selectedAccountField(screen), screen);
        if (selected == null || selected < 0 || selected >= UniversalAccountManager.accounts.size()) {
            return true;
        }

        final Account account = UniversalAccountManager.accounts.get(selected);
        final String username = StringUtils.isBlank(account.getUsername()) ? "???" : account.getUsername();

        ExecutorService executor = read(executorField(screen), screen);
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
            write(executorField(screen), screen, executor);
        }

        final Consumer<String> status = new Consumer<String>() {
            @Override
            public void accept(String message) {
                showStatus(screen, message, -1L);
            }
        };

        showStatus(screen, "&7Logging in... (" + username + ")&r", -1L);
        CompletableFuture<Void> task = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    AccountLogin.Result result = AccountLogin.login(account, status);
                    AccountLogin.apply(account, result);
                    SessionManager.set(result.getSession());
                    showStatus(screen, "&aLogged in! (" + result.getSession().getUsername() + ")&r", 5000L);
                } catch (Exception error) {
                    showStatus(screen, "&c" + AuthHttp.rootMessage(error) + " (" + username + ")&r", 7000L);
                }
            }
        }, executor);
        write(taskField(screen), screen, task);
        return true;
    }

    private static void showStatus(GuiScreen screen, String message, long duration) {
        write(notificationField(screen), screen, new Notification(TextFormatting.translate(message), duration));
    }

    private static Field selectedAccountField(GuiScreen screen) {
        if (selectedAccountField == null) {
            selectedAccountField = find(screen, "selectedAccount");
        }
        return selectedAccountField;
    }

    private static Field notificationField(GuiScreen screen) {
        if (notificationField == null) {
            notificationField = find(screen, "notification");
        }
        return notificationField;
    }

    private static Field taskField(GuiScreen screen) {
        if (taskField == null) {
            taskField = find(screen, "task");
        }
        return taskField;
    }

    private static Field executorField(GuiScreen screen) {
        if (executorField == null) {
            executorField = find(screen, "executor");
        }
        return executorField;
    }

    private static Field find(GuiScreen screen, String name) {
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Keep walking up; the field lives on GuiAccountManager itself.
            }
        }
        System.err.println("[UniversalAccountManager] Could not find GuiAccountManager." + name);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(Field field, Object instance) {
        if (field == null) {
            return null;
        }
        try {
            return (T) field.get(instance);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void write(Field field, Object instance, Object value) {
        if (field == null) {
            return;
        }
        try {
            field.set(instance, value);
        } catch (Exception ignored) {
            // Losing a status line is not worth breaking the login over.
        }
    }
}
