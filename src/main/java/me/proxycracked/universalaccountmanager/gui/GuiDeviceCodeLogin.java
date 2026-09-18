package me.proxycracked.universalaccountmanager.gui;

import java.net.URI;

import me.proxycracked.universalaccountmanager.auth.AccountLogin;
import me.proxycracked.universalaccountmanager.auth.AccountTypes;
import me.proxycracked.universalaccountmanager.auth.AuthHttp;
import me.proxycracked.universalaccountmanager.auth.MsaAuth;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.utils.Notification;
import me.proxycracked.universalaccountmanager.utils.SystemUtils;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.Session;

/**
 * Microsoft sign-in through the device-code flow.
 *
 * <p>No local callback server, no browser redirect back into the game, and it is the only flow that
 * hands this mod a long-lived refresh token from the same MSA client the alt shops use - which is
 * why an account added here survives its Minecraft token expiring.</p>
 */
public class GuiDeviceCodeLogin extends GuiScreen {
    private final GuiScreen previousScreen;

    private GuiButton copyButton;
    private GuiButton browserButton;
    private GuiButton cancelButton;

    private volatile MsaAuth.DeviceCode deviceCode;
    private volatile String status = "&7Asking Microsoft for a sign-in code...&r";
    private volatile String signedInAs;
    private volatile long deadline;
    private volatile boolean finished;
    private volatile boolean cancelled;
    private Thread worker;

    public GuiDeviceCodeLogin(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int centerX = width / 2;
        int row = height / 2 + 30;
        buttonList.add(copyButton = new GuiButton(0, centerX - 155, row, 100, 20, "Copy Code"));
        buttonList.add(browserButton = new GuiButton(1, centerX - 50, row, 100, 20, "Open Browser"));
        buttonList.add(cancelButton = new GuiButton(2, centerX + 55, row, 100, 20, "Cancel"));

        if (worker == null) {
            worker = new Thread(new SignInLoop(), "UniversalAccountManager-DeviceCode");
            worker.setDaemon(true);
            worker.start();
        }
    }

    @Override
    public void onGuiClosed() {
        cancelled = true;
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public void updateScreen() {
        if (finished && signedInAs != null) {
            finished = false;
            mc.displayGuiScreen(new GuiAccountManager(
                previousScreen,
                new Notification(TextFormatting.translate("&aLogged in as " + signedInAs + "&r"), 5000L)
            ));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        MsaAuth.DeviceCode code = deviceCode;

        drawCenteredString(fontRendererObj, "Microsoft Device Sign-in", width / 2, height / 2 - 74, 0xFFFFFF);

        if (code != null) {
            drawCenteredString(
                fontRendererObj,
                TextFormatting.translate("&71. Open &b" + code.getVerificationUri() + "&r"),
                width / 2,
                height / 2 - 50,
                0xFFFFFF
            );
            drawCenteredString(fontRendererObj, TextFormatting.translate("&72. Enter this code:&r"), width / 2, height / 2 - 38, 0xFFFFFF);

            String userCode = code.getUserCode();
            int codeWidth = fontRendererObj.getStringWidth(userCode) * 2;
            Gui.drawRect(width / 2 - codeWidth / 2 - 8, height / 2 - 24, width / 2 + codeWidth / 2 + 8, height / 2 + 2, 0x60000000);
            pushCodeScale();
            fontRendererObj.drawStringWithShadow(
                userCode,
                (width / 2 - codeWidth / 2) / 2.0F,
                (height / 2 - 20) / 2.0F,
                0x55FF55
            );
            popCodeScale();

            long remaining = Math.max(0L, (deadline - System.currentTimeMillis()) / 1000L);
            drawCenteredString(
                fontRendererObj,
                TextFormatting.translate("&8Code expires in " + remaining / 60 + "m " + remaining % 60 + "s&r"),
                width / 2,
                height / 2 + 8,
                0xFFFFFF
            );
        }

        String formatted = TextFormatting.translate(status);
        int textWidth = fontRendererObj.getStringWidth(formatted);
        Gui.drawRect(width / 2 - textWidth / 2 - 4, height / 2 + 56, width / 2 + textWidth / 2 + 4, height / 2 + 68, 0x40000000);
        drawCenteredString(fontRendererObj, formatted, width / 2, height / 2 + 58, 0xFFFFFF);

        copyButton.enabled = code != null;
        browserButton.enabled = code != null;
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void pushCodeScale() {
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.scale(2.0F, 2.0F, 1.0F);
    }

    private void popCodeScale() {
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            actionPerformed(cancelButton);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        MsaAuth.DeviceCode code = deviceCode;
        switch (button.id) {
            case 0:
                if (code != null) {
                    SystemUtils.setClipboard(code.getUserCode());
                    status = "&aCode copied to clipboard.&r";
                }
                break;
            case 1:
                if (code != null) {
                    SystemUtils.openWebLink(URI.create(code.getVerificationUriWithCode()));
                    status = "&7Waiting for you to finish signing in...&r";
                }
                break;
            case 2:
                cancelled = true;
                mc.displayGuiScreen(new GuiAddAccount(previousScreen));
                break;
            default:
                break;
        }
    }

    /** Requests a code, then polls Microsoft until the user finishes (or the code dies). */
    private final class SignInLoop implements Runnable {
        @Override
        public void run() {
            MsaAuth.DeviceCode code;
            try {
                code = MsaAuth.requestDeviceCode();
            } catch (Exception error) {
                status = "&cCould not start sign-in: " + AuthHttp.rootMessage(error) + "&r";
                return;
            }
            if (cancelled) {
                return;
            }

            deviceCode = code;
            deadline = System.currentTimeMillis() + code.getExpiresInSeconds() * 1000L;
            status = "&7Waiting for you to finish signing in...&r";
            SystemUtils.setClipboard(code.getUserCode());
            SystemUtils.openWebLink(URI.create(code.getVerificationUriWithCode()));

            long interval = code.getIntervalSeconds() * 1000L;
            while (!cancelled && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (cancelled) {
                    return;
                }
                try {
                    MsaAuth.TokenPair tokens = MsaAuth.pollDeviceCode(code.getDeviceCode());
                    if (tokens == null) {
                        continue;
                    }
                    finish(tokens);
                    return;
                } catch (Exception error) {
                    status = "&c" + AuthHttp.rootMessage(error) + "&r";
                    return;
                }
            }
            if (!cancelled) {
                status = "&cThe sign-in code expired. Press Cancel and try again.&r";
            }
        }

        private void finish(MsaAuth.TokenPair tokens) {
            try {
                status = "&7Signing in to Minecraft...&r";
                Session session = MsaAuth.minecraftLogin(tokens.getAccessToken());
                AccountLogin.Result result = new AccountLogin.Result(
                    session, tokens.getRefreshToken(), session.getToken(), AccountTypes.MSA);
                AccountLogin.upsert(result);
                SessionManager.set(session);
                signedInAs = session.getUsername();
                status = "&aSigned in.&r";
                finished = true;
            } catch (Exception error) {
                status = "&c" + AuthHttp.rootMessage(error) + "&r";
            }
        }
    }
}
