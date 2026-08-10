package me.proxycracked.universalaccountmanager.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class GuiAccountStores extends GuiScreen {
    private static GuiLocalTsStore localtsStore;
    private static GuiNiceAltsStore niceAltsStore;

    private final GuiScreen previousScreen;
    private GuiButton backButton;

    public GuiAccountStores(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int centerX = width / 2;
        int centerY = height / 2;
        buttonList.add(new GuiButton(0, centerX - 90, centerY - 24, 180, 20, "Localts"));
        buttonList.add(new GuiButton(2, centerX - 90, centerY, 180, 20, "Nicealts"));
        buttonList.add(backButton = new GuiButton(1, centerX - 90, height - 24, 180, 20, "Back"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Buy Accounts", width / 2, 4, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) actionPerformed(backButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) return;
        if (button.id == 0) {
            if (localtsStore == null) localtsStore = new GuiLocalTsStore(this);
            else localtsStore.setPreviousScreen(this);
            mc.displayGuiScreen(localtsStore);
        }
        else if (button.id == 2) {
            if (niceAltsStore == null) niceAltsStore = new GuiNiceAltsStore(this);
            else niceAltsStore.setPreviousScreen(this);
            mc.displayGuiScreen(niceAltsStore);
        }
        else if (button.id == 1) mc.displayGuiScreen(previousScreen);
    }
}
