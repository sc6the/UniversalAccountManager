package me.proxycracked.universalaccountmanager.gui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.AccountLogin;
import me.proxycracked.universalaccountmanager.auth.AccountTypes;
import me.proxycracked.universalaccountmanager.auth.AuthHttp;
import me.proxycracked.universalaccountmanager.auth.RefreshTokenParser;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.utils.Notification;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

/**
 * Imports Microsoft refresh tokens - one pasted token, a clipboard full of them, or a file.
 *
 * <p>Every token is redeemed through {@link AccountLogin}, which tries the legacy MSA client first
 * (what Localts and the other shops mint) and the mod's Azure app second, so a mixed list imports
 * in one pass.</p>
 */
public class GuiRefreshTokenLogin extends GuiScreen {
    private static final int MAX_LOG_LINES = 6;

    private final GuiScreen previousScreen;
    private final List<String> log = Collections.synchronizedList(new ArrayList<String>());

    private GuiTextField refreshTokenField;
    private GuiButton importButton;
    private GuiButton clipboardButton;
    private GuiButton fileButton;
    private GuiButton cancelButton;

    private volatile String status = "&7Paste a refresh token, or import a list.&r";
    private volatile boolean busy;
    private volatile String loggedInAs;
    private volatile boolean done;

    public GuiRefreshTokenLogin(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int centerX = width / 2;
        int centerY = height / 2;

        refreshTokenField = new GuiTextField(0, fontRendererObj, centerX - 150, centerY - 34, 300, 20);
        refreshTokenField.setMaxStringLength(32767);
        refreshTokenField.setFocused(true);

        buttonList.add(importButton = new GuiButton(0, centerX - 150, centerY - 8, 146, 20, "Add and Login"));
        buttonList.add(clipboardButton = new GuiButton(1, centerX + 4, centerY - 8, 146, 20, "Import Clipboard List"));
        buttonList.add(fileButton = new GuiButton(2, centerX - 150, centerY + 16, 146, 20, "Import From File..."));
        buttonList.add(cancelButton = new GuiButton(3, centerX + 4, centerY + 16, 146, 20, "Cancel"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        refreshTokenField.updateCursorCounter();
        if (done) {
            done = false;
            mc.displayGuiScreen(new GuiAccountManager(
                previousScreen,
                new Notification(TextFormatting.translate("&aAdded and logged in as " + loggedInAs + "&r"), 5000L)
            ));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Import Refresh Tokens", width / 2, height / 2 - 80, 0xFFFFFF);
        drawString(fontRendererObj, "Refresh token (or mail:pass:token):", width / 2 - 150, height / 2 - 48, 0xAAAAAA);

        String formatted = TextFormatting.translate(status);
        int textWidth = fontRendererObj.getStringWidth(formatted);
        Gui.drawRect(width / 2 - textWidth / 2 - 4, height / 2 + 44, width / 2 + textWidth / 2 + 4, height / 2 + 56, 0x40000000);
        drawCenteredString(fontRendererObj, formatted, width / 2, height / 2 + 46, 0xFFFFFF);

        synchronized (log) {
            int line = 0;
            for (String entry : log) {
                drawCenteredString(
                    fontRendererObj,
                    TextFormatting.translate(entry),
                    width / 2,
                    height / 2 + 62 + line * 10,
                    0xFFFFFF
                );
                line++;
            }
        }

        refreshTokenField.drawTextBox();
        importButton.enabled = !busy;
        clipboardButton.enabled = !busy;
        fileButton.enabled = !busy;
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        refreshTokenField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == 1) {
            actionPerformed(cancelButton);
        } else if (keyCode == 28) {
            actionPerformed(importButton);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception ignored) {
            // GuiScreen#mouseClicked declares IOException; nothing here can throw it.
        }
        refreshTokenField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        switch (button.id) {
            case 0:
                startImport(RefreshTokenParser.parseAll(refreshTokenField.getText()), true);
                break;
            case 1:
                startImport(RefreshTokenParser.parseAll(getClipboardString()), false);
                break;
            case 2:
                chooseFile();
                break;
            case 3:
                mc.displayGuiScreen(new GuiAddAccount(previousScreen));
                break;
            default:
                break;
        }
    }

    private void chooseFile() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The default look and feel is fine too.
        }
        status = "&7File picker opened in background.&r";
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                java.awt.FileDialog dialog = new java.awt.FileDialog((java.awt.Frame) null, "Select Token List", java.awt.FileDialog.LOAD);
                dialog.setDirectory(System.getProperty("user.home") + File.separator + "Downloads");
                dialog.setFile("*.txt");
                dialog.setModal(true);
                dialog.setVisible(true);
                String name = dialog.getFile();
                if (name == null) {
                    status = "&eFile selection canceled.&r";
                    return;
                }
                File file = new File(dialog.getDirectory(), name);
                try {
                    startImport(RefreshTokenParser.parseAll(read(file)), false);
                } catch (Exception error) {
                    status = "&cCould not read that file: " + AuthHttp.rootMessage(error) + "&r";
                }
            }
        });
    }

    private static String read(File file) throws Exception {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
        return content.toString();
    }

    private void startImport(final List<String> tokens, final boolean loginWithFirst) {
        if (busy) {
            return;
        }
        if (tokens.isEmpty()) {
            status = "&cNo refresh token found in that text.&r";
            return;
        }

        busy = true;
        log.clear();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                importAll(tokens, loginWithFirst);
            }
        }, "UniversalAccountManager-TokenImport");
        worker.setDaemon(true);
        worker.start();
    }

    private void importAll(List<String> tokens, boolean loginWithFirst) {
        int imported = 0;
        int failed = 0;
        try {
            for (int index = 0; index < tokens.size(); index++) {
                String token = tokens.get(index);
                status = "&7Importing " + (index + 1) + "/" + tokens.size() + "...&r";
                try {
                    AccountLogin.Result result = AccountLogin.fromRefreshToken(token, AccountTypes.MSA, null);
                    Account account = AccountLogin.upsert(result);
                    imported++;
                    append("&a+ " + account.getUsername() + "&r");
                    if (loginWithFirst && imported == 1) {
                        SessionManager.set(result.getSession());
                        loggedInAs = result.getSession().getUsername();
                    }
                } catch (Exception error) {
                    failed++;
                    append("&c- " + shortToken(token) + ": " + AuthHttp.rootMessage(error) + "&r");
                }
                if (index + 1 < tokens.size()) {
                    // Microsoft answers bursts of token requests with 429s.
                    Thread.sleep(600L);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }

        if (imported == 0) {
            status = "&cImported 0 accounts; " + failed + " failed.&r";
        } else if (failed == 0) {
            status = "&aImported " + imported + " account" + (imported == 1 ? "" : "s") + ".&r";
        } else {
            status = "&eImported " + imported + ", " + failed + " failed.&r";
        }
        busy = false;
        if (loggedInAs != null) {
            done = true;
        }
    }

    private void append(String line) {
        synchronized (log) {
            log.add(line);
            while (log.size() > MAX_LOG_LINES) {
                log.remove(0);
            }
        }
    }

    private static String shortToken(String token) {
        return token.length() <= 12 ? token : token.substring(0, 12) + "...";
    }
}
