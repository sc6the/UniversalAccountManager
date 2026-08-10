package me.proxycracked.universalaccountmanager.gui;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.MicrosoftAuth;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.utils.Notification;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.Session;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

public class GuiRefreshTokenLogin extends GuiScreen {
    private final GuiScreen previousScreen;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "UniversalAccountManager-RefreshToken");
        thread.setDaemon(true);
        return thread;
    });

    private GuiTextField refreshTokenField;
    private GuiButton addButton;
    private GuiButton cancelButton;
    private volatile String status = "&7Paste a Microsoft refresh token.&r";
    private volatile boolean success;
    private volatile String addedUsername;
    private CompletableFuture<Void> task;

    public GuiRefreshTokenLogin(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int centerX = width / 2;
        int centerY = height / 2;

        refreshTokenField = new GuiTextField(0, fontRendererObj, centerX - 150, centerY - 12, 300, 20);
        refreshTokenField.setMaxStringLength(32767);
        refreshTokenField.setFocused(true);
        buttonList.add(addButton = new GuiButton(0, centerX - 150, centerY + 16, 146, 20, "Add and Login"));
        buttonList.add(cancelButton = new GuiButton(1, centerX + 4, centerY + 16, 146, 20, "Cancel"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
        executor.shutdownNow();
    }

    @Override
    public void updateScreen() {
        refreshTokenField.updateCursorCounter();
        if (success) {
            success = false;
            mc.displayGuiScreen(new GuiAccountManager(
                previousScreen,
                new Notification(TextFormatting.translate("&aAdded and logged in as " + addedUsername + "&r"), 5000L)
            ));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Microsoft Refresh Token", width / 2, height / 2 - 60, 0xFFFFFF);
        drawString(fontRendererObj, "Refresh token:", width / 2 - 150, height / 2 - 26, 0xAAAAAA);

        String formatted = TextFormatting.translate(status);
        int textWidth = fontRendererObj.getStringWidth(formatted);
        Gui.drawRect(width / 2 - textWidth / 2 - 4, height / 2 + 44, width / 2 + textWidth / 2 + 4, height / 2 + 56, 0x40000000);
        drawCenteredString(fontRendererObj, formatted, width / 2, height / 2 + 46, 0xFFFFFF);

        refreshTokenField.drawTextBox();
        if (addButton != null) {
            addButton.enabled = task == null || task.isDone();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        refreshTokenField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == 1) {
            actionPerformed(cancelButton);
        } else if (keyCode == 28) {
            actionPerformed(addButton);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception ignored) {
        }
        refreshTokenField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        if (button.id == 0) {
            addRefreshToken();
        } else if (button.id == 1) {
            mc.displayGuiScreen(new GuiAddAccount(previousScreen));
        }
    }

    private void addRefreshToken() {
        String suppliedToken = refreshTokenField.getText().trim();
        if (StringUtils.isBlank(suppliedToken)) {
            status = "&cRefresh token is empty.&r";
            return;
        }
        if (task != null && !task.isDone()) {
            return;
        }

        AtomicReference<String> rotatedRefreshToken = new AtomicReference<>(suppliedToken);
        AtomicReference<String> minecraftAccessToken = new AtomicReference<>("");
        status = "&7Refreshing Microsoft credentials...&r";

        task = MicrosoftAuth.refreshMSAccessTokens(suppliedToken, executor)
            .thenComposeAsync(tokens -> {
                rotatedRefreshToken.set(tokens.get("refresh_token"));
                status = "&7Acquiring Xbox token...&r";
                return MicrosoftAuth.acquireXboxAccessToken(tokens.get("access_token"), executor);
            }, executor)
            .thenComposeAsync(xboxToken -> {
                status = "&7Acquiring XSTS token...&r";
                return MicrosoftAuth.acquireXboxXstsToken(xboxToken, executor);
            }, executor)
            .thenComposeAsync(xsts -> {
                status = "&7Acquiring Minecraft token...&r";
                return MicrosoftAuth.acquireMCAccessToken(xsts.get("Token"), xsts.get("uhs"), executor);
            }, executor)
            .thenComposeAsync(accessToken -> {
                minecraftAccessToken.set(accessToken);
                status = "&7Fetching Minecraft profile...&r";
                return MicrosoftAuth.login(accessToken, executor);
            }, executor)
            .thenAcceptAsync(session -> saveAccount(session, rotatedRefreshToken.get(), minecraftAccessToken.get()), executor)
            .exceptionally(error -> {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                status = "&cRefresh-token login failed: " + message + "&r";
                return null;
            });
    }

    private void saveAccount(Session session, String refreshToken, String accessToken) {
        Account match = null;
        for (Account account : UniversalAccountManager.accounts) {
            if ((!StringUtils.isBlank(account.getUuid()) && account.getUuid().equalsIgnoreCase(session.getPlayerID()))
                || account.getUsername().equalsIgnoreCase(session.getUsername())) {
                match = account;
                break;
            }
        }

        if (match == null) {
            match = new Account("ms", refreshToken, accessToken, session.getUsername(), session.getPlayerID(), 0L);
            UniversalAccountManager.accounts.add(match);
        } else {
            match.setType("ms");
            match.setRefreshToken(refreshToken);
            match.setAccessToken(accessToken);
            match.setUsername(session.getUsername());
            match.setUuid(session.getPlayerID());
        }

        UniversalAccountManager.resort();
        UniversalAccountManager.save();
        SessionManager.set(session);
        addedUsername = session.getUsername();
        success = true;
        status = "&aAccount added.&r";
    }
}
