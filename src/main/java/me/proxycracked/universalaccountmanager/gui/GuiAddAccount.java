package me.proxycracked.universalaccountmanager.gui;

import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class GuiAddAccount extends GuiScreen {
    private static final int WIDTH = 220;
    private static final int ROW_HEIGHT = 23;

    /** First button to the bottom of Cancel, used to keep the block centred on short screens. */
    private static final int BLOCK_HEIGHT = ROW_HEIGHT * 6 + 8 + 20;

    private final GuiScreen previousScreen;
    private GuiButton cancelButton;
    private String hint = "";
    private int startY;

    public GuiAddAccount(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int left = this.width / 2 - WIDTH / 2;
        startY = Math.max(26, (this.height - BLOCK_HEIGHT) / 2 + 6);

        buttonList.add(new GuiButton(0, left, startY, WIDTH, 20, "Microsoft Login (Device Code)"));
        buttonList.add(new GuiButton(1, left, startY + ROW_HEIGHT, WIDTH, 20, "Microsoft Login (Browser)"));
        buttonList.add(new GuiButton(2, left, startY + ROW_HEIGHT * 2, WIDTH, 20, "Import Refresh Token(s)"));
        buttonList.add(new GuiButton(3, left, startY + ROW_HEIGHT * 3, WIDTH, 20, "Cookie File Login"));
        buttonList.add(new GuiButton(4, left, startY + ROW_HEIGHT * 4, WIDTH, 20, "Minecraft Token Login"));
        buttonList.add(new GuiButton(5, left, startY + ROW_HEIGHT * 5, WIDTH, 20, "Offline Account"));
        buttonList.add(cancelButton = new GuiButton(6, left, startY + ROW_HEIGHT * 6 + 8, WIDTH, 20, "Cancel"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            TextFormatting.translate("&fAdd Account&r"),
            width / 2,
            startY - 16,
            0xFFFFFF
        );
        super.drawScreen(mouseX, mouseY, partialTicks);
        updateHint(mouseX, mouseY);
        if (!hint.isEmpty() && cancelButton != null) {
            // Below Cancel, not on top of it.
            drawCenteredString(
                fontRendererObj,
                TextFormatting.translate(hint),
                width / 2,
                cancelButton.yPosition + cancelButton.height + 8,
                0xFFFFFF
            );
        }
    }

    /** One line of "what does this button actually do" under the list. */
    private void updateHint(int mouseX, int mouseY) {
        hint = "";
        for (Object entry : buttonList) {
            GuiButton button = (GuiButton) entry;
            boolean hovered = mouseX >= button.xPosition && mouseX < button.xPosition + button.width
                && mouseY >= button.yPosition && mouseY < button.yPosition + button.height;
            if (!hovered) {
                continue;
            }
            switch (button.id) {
                case 0:
                    hint = "&7Sign in on microsoft.com/link. Keeps working - stores a refresh token.&r";
                    break;
                case 1:
                    hint = "&7Opens a browser to a local callback. Needs port 25575 free.&r";
                    break;
                case 2:
                    hint = "&7Paste or load alt-shop refresh tokens, one per line.&r";
                    break;
                case 3:
                    hint = "&7Netscape cookies.txt export from a signed-in Microsoft browser.&r";
                    break;
                case 4:
                    hint = "&7A raw Minecraft access token. Expires in about a day.&r";
                    break;
                case 5:
                    hint = "&7Cracked account - a username only, for offline-mode servers.&r";
                    break;
                default:
                    hint = "";
                    break;
            }
        }
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

        switch (button.id) {
            case 0:
                mc.displayGuiScreen(new GuiDeviceCodeLogin(previousScreen));
                break;
            case 1:
                mc.displayGuiScreen(new GuiMicrosoftAuth(previousScreen));
                break;
            case 2:
                mc.displayGuiScreen(new GuiRefreshTokenLogin(previousScreen));
                break;
            case 3:
                mc.displayGuiScreen(new GuiCookieAuth(previousScreen));
                break;
            case 4:
                mc.displayGuiScreen(new GuiTokenLogin(previousScreen));
                break;
            case 5:
                mc.displayGuiScreen(new GuiOfflineAccount(previousScreen));
                break;
            case 6:
                mc.displayGuiScreen(new GuiAccountManager(previousScreen));
                break;
            default:
                break;
        }
    }
}
