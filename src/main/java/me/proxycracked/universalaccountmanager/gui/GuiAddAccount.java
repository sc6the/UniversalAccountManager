package me.proxycracked.universalaccountmanager.gui;

import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class GuiAddAccount extends GuiScreen {
    private final GuiScreen previousScreen;
    private GuiButton cancelButton;

    public GuiAddAccount(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int width = 160;
        int centerX = this.width / 2;
        int startY = this.height / 2 - 42;

        buttonList.add(new GuiButton(0, centerX - width / 2, startY, width, 20, "Microsoft Login"));
        buttonList.add(new GuiButton(1, centerX - width / 2, startY + 24, width, 20, "Cookie Login"));
        buttonList.add(new GuiButton(2, centerX - width / 2, startY + 48, width, 20, "Token Login"));
        buttonList.add(cancelButton = new GuiButton(3, centerX - width / 2, startY + 78, width, 20, "Cancel"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            TextFormatting.translate("&fAdd Account&r"),
            width / 2,
            height / 2 - 42 - fontRendererObj.FONT_HEIGHT - 8,
            0xFFFFFF
        );
        super.drawScreen(mouseX, mouseY, partialTicks);
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
                mc.displayGuiScreen(new GuiMicrosoftAuth(previousScreen));
                break;
            case 1:
                mc.displayGuiScreen(new GuiCookieAuth(previousScreen));
                break;
            case 2:
                mc.displayGuiScreen(new GuiTokenLogin(previousScreen));
                break;
            case 3:
                mc.displayGuiScreen(new GuiAccountManager(previousScreen));
                break;
            default:
                break;
        }
    }
}
