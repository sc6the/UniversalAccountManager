package me.proxycracked.universalaccountmanager.gui;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import me.proxycracked.universalaccountmanager.store.NiceAltsProvider;
import me.proxycracked.universalaccountmanager.store.StoreOrder;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

/**
 * Nicealts' custom purchase: one alt that the shop has just checked against a named server.
 *
 * <p>Nicealts only, and the one place the three shops deliberately differ. The endpoint has no
 * quantity field - one call is one account - and Minemen is only ever sold with the chat ban
 * check, so picking it forces chat mode and the extra credit that comes with it.</p>
 */
public class GuiCustomPurchase extends GuiScreen implements GuiYesNoCallback {
    private static final int CONFIRM_ID = 6200;
    private static final String[] PROTOCOLS = {"1.8", "1.21", "26.1"};

    private final GuiStore parent;
    private final NiceAltsProvider provider;
    private final ExecutorService executor =
        Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "Nicealts-CustomPurchase");
                thread.setDaemon(true);
                return thread;
            }
        });

    private GuiTextField serverField;
    private GuiButton protocolButton;
    private GuiButton banCheckButton;
    private GuiButton purchaseButton;
    private GuiButton backButton;
    private int protocolIndex;
    private boolean chatBanCheck;
    private volatile String status = "&7Enter the server this alt has to be unbanned on.&r";
    private Future<?> task;

    public GuiCustomPurchase(GuiStore parent, NiceAltsProvider provider) {
        this.parent = parent;
        this.provider = provider;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int centerX = width / 2;
        int top = Math.max(40, height / 2 - 56);

        String previous = serverField == null ? "" : serverField.getText();
        serverField = new GuiTextField(0, fontRendererObj, centerX - 152, top + 10, 304, 20);
        serverField.setMaxStringLength(128);
        serverField.setText(previous);
        serverField.setFocused(true);

        buttonList.add(protocolButton = new GuiButton(1, centerX - 152, top + 46, 148, 20, ""));
        buttonList.add(banCheckButton = new GuiButton(2, centerX + 4, top + 46, 148, 20, ""));
        buttonList.add(purchaseButton = new GuiButton(3, centerX - 152, top + 92, 304, 20, "Review Purchase"));
        buttonList.add(backButton = new GuiButton(4, centerX - 74, height - 24, 148, 20, "Back"));
        updateButtons();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        if (serverField != null) {
            serverField.updateCursorCounter();
        }
        updateButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int centerX = width / 2;
        int top = Math.max(40, height / 2 - 56);
        drawCenteredString(fontRendererObj, "Nicealts Custom Purchase", centerX, 4, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "One alt, prechecked against a server you name.", centerX, 18, 0xAAAAAA);
        drawString(fontRendererObj, "Server (host or host:port, port defaults to 25565):", centerX - 152, top, 0xAAAAAA);
        serverField.drawTextBox();
        drawCenteredString(fontRendererObj, "Cost: " + cost() + " credits", centerX, top + 74, 0xFFFF80);
        if (isMinemen()) {
            drawCenteredString(fontRendererObj, "Minemen only sells with the chat ban check.", centerX, top + 118, 0xFFAA00);
        }
        drawCenteredString(fontRendererObj,
            fontRendererObj.trimStringToWidth(TextFormatting.translate(status), Math.max(120, width - 20)),
            centerX, height - 40, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private double cost() {
        return NiceAltsProvider.CUSTOM_PURCHASE_CREDITS
            + (useChat() ? NiceAltsProvider.CUSTOM_PURCHASE_CHAT_SURCHARGE : 0.0D);
    }

    private boolean useChat() {
        return chatBanCheck || isMinemen();
    }

    private boolean isMinemen() {
        String server = serverField == null ? "" : serverField.getText().toLowerCase(Locale.ROOT);
        return server.contains("minemen");
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (serverField != null) {
            serverField.textboxKeyTyped(typedChar, keyCode);
        }
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
        } else if (keyCode == 28) {
            actionPerformed(purchaseButton);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception ignored) {
        }
        if (serverField != null) {
            serverField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        switch (button.id) {
            case 1:
                protocolIndex = (protocolIndex + 1) % PROTOCOLS.length;
                break;
            case 2:
                chatBanCheck = !chatBanCheck;
                break;
            case 3:
                confirmPurchase();
                break;
            case 4:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
        updateButtons();
    }

    private void confirmPurchase() {
        String server = serverField.getText().trim();
        if (StringUtils.isBlank(server)) {
            status = "&cEnter a server first.&r";
            return;
        }
        mc.displayGuiScreen(new GuiYesNo(
            this,
            "Buy 1 alt prechecked on " + server + "?",
            "Protocol " + PROTOCOLS[protocolIndex] + ", " + (useChat() ? "chat" : "normal")
                + " ban check. This will deduct " + (int) cost() + " credits.",
            "Purchase",
            "Cancel",
            CONFIRM_ID
        ));
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        mc.displayGuiScreen(this);
        if (result && id == CONFIRM_ID) {
            purchase();
        }
    }

    private void purchase() {
        if (isBusy()) {
            return;
        }
        final String server = serverField.getText().trim();
        final String protocol = PROTOCOLS[protocolIndex];
        final boolean chat = useChat();
        status = "&7Asking Nicealts for an alt unbanned on " + server + "...&r";
        task = executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final StoreOrder order = provider.customPurchase(server, protocol, chat);
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            parent.acceptOrder(order, "Purchased and imported");
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
        if (protocolButton != null) {
            protocolButton.displayString = "Protocol: " + PROTOCOLS[protocolIndex];
            protocolButton.enabled = !busy;
        }
        if (banCheckButton != null) {
            banCheckButton.displayString = "Ban check: " + (useChat() ? "Chat (+1)" : "Normal");
            // Minemen is chat-only, so the toggle stops pretending it has a choice.
            banCheckButton.enabled = !busy && !isMinemen();
        }
        if (purchaseButton != null) {
            purchaseButton.enabled = !busy && serverField != null && !StringUtils.isBlank(serverField.getText());
        }
        if (backButton != null) {
            backButton.enabled = !busy;
        }
    }

    private boolean isBusy() {
        return task != null && !task.isDone();
    }
}
