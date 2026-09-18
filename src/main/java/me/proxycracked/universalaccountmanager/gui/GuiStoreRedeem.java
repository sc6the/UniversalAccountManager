package me.proxycracked.universalaccountmanager.gui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import me.proxycracked.universalaccountmanager.fernan.FernanClient;
import me.proxycracked.universalaccountmanager.store.FernanProvider;
import me.proxycracked.universalaccountmanager.store.StoreAccount;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

/** Fernan Club's Peso keys: 24 characters in, credits on the balance out. */
public class GuiStoreRedeem extends GuiScreen {
    private final GuiStore parent;
    private final FernanProvider provider;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Fernan-Redeem");
            thread.setDaemon(true);
            return thread;
        }
    });

    private GuiTextField keyField;
    private GuiButton redeemButton;
    private GuiButton backButton;
    private volatile String status = "&7Paste a Peso key to top up your balance.&r";
    private Future<?> task;

    public GuiStoreRedeem(GuiStore parent, FernanProvider provider) {
        this.parent = parent;
        this.provider = provider;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int centerX = width / 2;
        int centerY = height / 2;
        String previous = keyField == null ? "" : keyField.getText();
        keyField = new GuiTextField(0, fontRendererObj, centerX - 150, centerY - 14, 300, 20);
        keyField.setMaxStringLength(64);
        keyField.setText(previous);
        keyField.setFocused(true);
        buttonList.add(redeemButton = new GuiButton(0, centerX - 150, centerY + 14, 146, 20, "Redeem"));
        buttonList.add(backButton = new GuiButton(1, centerX + 4, centerY + 14, 146, 20, "Back"));
        updateButtons();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        if (keyField != null) {
            keyField.updateCursorCounter();
        }
        updateButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int centerX = width / 2;
        drawCenteredString(fontRendererObj, "Redeem a Peso Key", centerX, 8, 0xFFFFFF);
        drawString(fontRendererObj, "Peso key:", centerX - 150, height / 2 - 28, 0xAAAAAA);
        keyField.drawTextBox();
        drawCenteredString(fontRendererObj,
            fontRendererObj.trimStringToWidth(TextFormatting.translate(status), Math.max(120, width - 20)),
            centerX, height / 2 + 48, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyField != null) {
            keyField.textboxKeyTyped(typedChar, keyCode);
        }
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
        } else if (keyCode == 28) {
            actionPerformed(redeemButton);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception ignored) {
        }
        if (keyField != null) {
            keyField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        if (button.id == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        redeem();
    }

    private void redeem() {
        if (isBusy()) {
            return;
        }
        final String key = keyField.getText().trim();
        if (StringUtils.isBlank(key)) {
            status = "&cEnter a key first.&r";
            return;
        }
        status = "&7Redeeming...&r";
        task = executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final FernanClient.Redemption redemption = provider.redeem(key);
                    final StoreAccount updated = provider.refreshAccount();
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            parent.onBalanceChanged(updated, "&aRedeemed " + (int) redemption.getValue()
                                + " credits. Balance: " + (int) redemption.getBalanceAfter() + ".&r");
                        }
                    });
                } catch (Exception error) {
                    status = "&c" + GuiStore.safeMessage(error) + "&r";
                }
            }
        });
    }

    private void updateButtons() {
        boolean busy = isBusy();
        if (redeemButton != null) {
            redeemButton.enabled = !busy && keyField != null && !StringUtils.isBlank(keyField.getText());
        }
        if (backButton != null) {
            backButton.enabled = !busy;
        }
    }

    private boolean isBusy() {
        return task != null && !task.isDone();
    }
}
