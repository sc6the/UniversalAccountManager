package me.proxycracked.universalaccountmanager.gui;

import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.AccountLogin;
import me.proxycracked.universalaccountmanager.auth.AccountTypes;
import me.proxycracked.universalaccountmanager.auth.OfflineAuth;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.utils.Notification;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.Session;

import org.lwjgl.input.Keyboard;

/** Adds a cracked/offline account: a username, the offline-mode UUID derived from it, no tokens. */
public class GuiOfflineAccount extends GuiScreen {
    private final GuiScreen previousScreen;

    private GuiTextField usernameField;
    private GuiButton addButton;
    private GuiButton addAndLoginButton;
    private GuiButton cancelButton;
    private String status = "&7Offline accounts only work on offline-mode (cracked) servers.&r";

    public GuiOfflineAccount(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int centerX = width / 2;
        int centerY = height / 2;

        usernameField = new GuiTextField(0, fontRendererObj, centerX - 100, centerY - 12, 200, 20);
        usernameField.setMaxStringLength(16);
        usernameField.setFocused(true);

        buttonList.add(addButton = new GuiButton(0, centerX - 155, centerY + 16, 100, 20, "Add"));
        buttonList.add(addAndLoginButton = new GuiButton(1, centerX - 50, centerY + 16, 100, 20, "Add and Login"));
        buttonList.add(cancelButton = new GuiButton(2, centerX + 55, centerY + 16, 100, 20, "Cancel"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        usernameField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Offline Account", width / 2, height / 2 - 60, 0xFFFFFF);
        drawString(fontRendererObj, "Username:", width / 2 - 100, height / 2 - 26, 0xAAAAAA);

        String formatted = TextFormatting.translate(status);
        int textWidth = fontRendererObj.getStringWidth(formatted);
        Gui.drawRect(width / 2 - textWidth / 2 - 4, height / 2 + 44, width / 2 + textWidth / 2 + 4, height / 2 + 56, 0x40000000);
        drawCenteredString(fontRendererObj, formatted, width / 2, height / 2 + 46, 0xFFFFFF);

        usernameField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        usernameField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == 1) {
            actionPerformed(cancelButton);
        } else if (keyCode == 28) {
            actionPerformed(addAndLoginButton);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception ignored) {
            // GuiScreen#mouseClicked declares IOException; nothing here can throw it.
        }
        usernameField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        if (button == cancelButton) {
            mc.displayGuiScreen(new GuiAddAccount(previousScreen));
            return;
        }
        add(button == addAndLoginButton);
    }

    private void add(boolean alsoLogin) {
        String username = usernameField.getText().trim();
        String problem = OfflineAuth.validate(username);
        if (problem != null) {
            status = "&c" + problem + "&r";
            return;
        }

        Account existing = AccountLogin.find(OfflineAuth.offlineUuid(username), username);
        if (existing != null && !AccountTypes.isOffline(existing)) {
            status = "&cAn account named " + username + " is already saved.&r";
            return;
        }

        Account account = existing;
        if (account == null) {
            account = OfflineAuth.account(username);
            UniversalAccountManager.accounts.add(account);
            UniversalAccountManager.resort();
        }
        account.setAvailable(Boolean.TRUE);
        UniversalAccountManager.save();

        if (!alsoLogin) {
            mc.displayGuiScreen(new GuiAccountManager(
                previousScreen,
                new Notification(TextFormatting.translate("&aAdded offline account " + username + "&r"), 4000L)
            ));
            return;
        }

        Session session = OfflineAuth.session(username);
        SessionManager.set(session);
        mc.displayGuiScreen(new GuiAccountManager(
            previousScreen,
            new Notification(TextFormatting.translate("&aPlaying offline as " + username + "&r"), 5000L)
        ));
    }
}
