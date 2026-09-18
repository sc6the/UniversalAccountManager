package me.proxycracked.universalaccountmanager.gui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import me.proxycracked.universalaccountmanager.store.NiceAltsProvider;
import me.proxycracked.universalaccountmanager.store.StoreOrder;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/**
 * Nicealts' subscription generator.
 *
 * <p>Costs no credits, but the account arrives as a bare Minecraft token, so it is a one-day
 * account rather than a refreshable one.</p>
 */
public class GuiStoreGenerate extends GuiScreen {
    private static final String[] CATEGORIES = {"Unbanned", "DonutSMP", "Banned"};

    private final GuiStore parent;
    private final NiceAltsProvider provider;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Nicealts-Generate");
            thread.setDaemon(true);
            return thread;
        }
    });

    private volatile String status;
    private Future<?> task;

    public GuiStoreGenerate(GuiStore parent, NiceAltsProvider provider) {
        this.parent = parent;
        this.provider = provider;
        this.status = provider.hasGeneratorAccess()
            ? "&7Pick a category. Generated accounts cost no credits.&r"
            : "&cThis API key has no generator subscription.&r";
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int centerX = width / 2;
        int top = Math.max(46, height / 2 - 40);
        for (int index = 0; index < CATEGORIES.length; index++) {
            int stock = provider.generatorStock(CATEGORIES[index]);
            GuiButton button = new GuiButton(index, centerX - 80, top + index * 24, 160, 20,
                CATEGORIES[index] + ": " + stock);
            button.enabled = provider.hasGeneratorAccess() && stock > 0 && !isBusy();
            buttonList.add(button);
        }
        buttonList.add(new GuiButton(9, centerX - 74, height - 24, 148, 20, "Back"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Nicealts Generator", width / 2, 8, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Included with your subscription; no credits are spent.",
            width / 2, 22, 0xAAAAAA);
        drawCenteredString(fontRendererObj,
            fontRendererObj.trimStringToWidth(TextFormatting.translate(status), Math.max(120, width - 20)),
            width / 2, height - 42, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        if (button.id == 9) {
            mc.displayGuiScreen(parent);
            return;
        }
        generate(CATEGORIES[button.id]);
    }

    private void generate(final String category) {
        if (isBusy()) {
            return;
        }
        status = "&7Generating a " + category + " account...&r";
        setButtonsEnabled(false);
        task = executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final StoreOrder order = provider.generate(category);
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            parent.acceptOrder(order, "Generated and imported");
                        }
                    });
                } catch (Exception error) {
                    status = "&c" + GuiStore.safeMessage(error) + "&r";
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            setButtonsEnabled(true);
                        }
                    });
                }
            }
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        for (GuiButton button : buttonList) {
            if (button.id == 9) {
                continue;
            }
            button.enabled = enabled && provider.hasGeneratorAccess() && provider.generatorStock(CATEGORIES[button.id]) > 0;
        }
    }

    private boolean isBusy() {
        return task != null && !task.isDone();
    }
}
