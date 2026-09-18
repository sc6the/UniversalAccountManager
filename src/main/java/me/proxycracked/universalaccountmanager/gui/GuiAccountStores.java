package me.proxycracked.universalaccountmanager.gui;

import java.util.LinkedHashMap;
import java.util.Map;

import me.proxycracked.universalaccountmanager.store.StoreProviders;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** Picks a shop. Each one opens the same {@link GuiStore}, kept alive so a session is reused. */
public class GuiAccountStores extends GuiScreen {
    private static final String[] PROVIDER_IDS = {
        StoreProviders.LOCALTS, StoreProviders.NICEALTS, StoreProviders.FERNAN
    };
    private static final String[] PROVIDER_NAMES = {"Localts", "Nicealts", "Fernan Club"};
    private static final Map<String, GuiStore> OPEN_STORES = new LinkedHashMap<String, GuiStore>();

    private final GuiScreen previousScreen;
    private GuiButton backButton;

    public GuiAccountStores(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int centerX = width / 2;
        int top = height / 2 - 36;
        for (int index = 0; index < PROVIDER_IDS.length; index++) {
            buttonList.add(new GuiButton(index, centerX - 90, top + index * 24, 180, 20, PROVIDER_NAMES[index]));
        }
        buttonList.add(backButton = new GuiButton(9, centerX - 90, height - 24, 180, 20, "Back"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Buy Accounts", width / 2, 4, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Same screen, same import, wherever you buy.", width / 2, 18, 0xAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            actionPerformed(backButton);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        if (button.id == 9) {
            mc.displayGuiScreen(previousScreen);
            return;
        }
        if (button.id < 0 || button.id >= PROVIDER_IDS.length) {
            return;
        }
        String providerId = PROVIDER_IDS[button.id];
        GuiStore store = OPEN_STORES.get(providerId);
        if (store == null) {
            store = new GuiStore(this, StoreProviders.create(providerId));
            OPEN_STORES.put(providerId, store);
        } else {
            store.setPreviousScreen(this);
        }
        mc.displayGuiScreen(store);
    }
}
