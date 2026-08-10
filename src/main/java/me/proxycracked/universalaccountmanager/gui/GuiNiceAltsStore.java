package me.proxycracked.universalaccountmanager.gui;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.localts.PurchasedAccountImporter;
import me.proxycracked.universalaccountmanager.localts.PurchasedAccountImporter.ImportedAccount;
import me.proxycracked.universalaccountmanager.nicealts.NiceAltsClient;
import me.proxycracked.universalaccountmanager.nicealts.NiceAltsClient.User;
import me.proxycracked.universalaccountmanager.nicealts.NiceAltsCredentialStore;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.util.Session;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

public class GuiNiceAltsStore extends GuiScreen implements GuiYesNoCallback {
    private static final DecimalFormat BALANCE_FORMAT = new DecimalFormat("0.##");
    private static final int CONFIRM_BASE = 6100;
    private static final String[] PRODUCT_NAMES = {
        "", "Unbanned 1-7", "Unbanned 8+", "Unbanned Ranked", "LQ Unbanned", "Donut", "Banned"
    };

    private final ExecutorService storeExecutor = Executors.newSingleThreadExecutor(runnable -> daemon(runnable, "Nicealts-Store"));
    private final ExecutorService authExecutor = Executors.newFixedThreadPool(2, runnable -> daemon(runnable, "Nicealts-Auth"));
    private GuiScreen previousScreen;
    private GuiTextField apiKeyField;
    private GuiButton connectButton;
    private GuiButton retryButton;
    private String apiKey = "";
    private User user;
    private Map<Integer, Integer> stock = Collections.emptyMap();
    private List<String> pendingItems = Collections.emptyList();
    private volatile String status = "&7Enter your Nicealts API key.&r";
    private Future<?> task;
    private boolean savedKeyLoaded;
    private boolean savedKeyAttempted;
    private int pendingProduct;

    public GuiNiceAltsStore(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    void setPreviousScreen(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        if (user == null) {
            loadSavedKey();
            initLogin();
            if (!savedKeyAttempted && !StringUtils.isBlank(apiKey)) {
                savedKeyAttempted = true;
                connect();
            }
        } else {
            initStore();
        }
    }

    private void initLogin() {
        int centerX = width / 2;
        int centerY = height / 2;
        apiKeyField = new GuiTextField(0, fontRendererObj, centerX - 150, centerY - 14, 300, 20);
        apiKeyField.setMaxStringLength(512);
        apiKeyField.setText("");
        apiKeyField.setFocused(StringUtils.isBlank(apiKey));
        buttonList.add(connectButton = new GuiButton(0, centerX - 150, centerY + 14, 146, 20, "Connect"));
        buttonList.add(new GuiButton(2, centerX - 73, height - 24, 146, 20, "Back"));
    }

    private void initStore() {
        int centerX = width / 2;
        int top = storeTop();
        for (int product = 1; product <= 6; product++) {
            int column = (product - 1) % 2;
            int row = (product - 1) / 2;
            GuiButton button = new GuiButton(100 + product, centerX - 152 + column * 156, top + row * 46, 148, 20, PRODUCT_NAMES[product]);
            button.enabled = !isBusy() && stock(product) > 0;
            buttonList.add(button);
        }
        buttonList.add(new GuiButton(10, centerX - 152, height - 24, 96, 20, "Refresh"));
        buttonList.add(retryButton = new GuiButton(11, centerX - 48, height - 24, 96, 20, "Retry Import"));
        buttonList.add(new GuiButton(2, centerX + 56, height - 24, 96, 20, "Back"));
        updateButtons();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        if (apiKeyField != null) apiKeyField.updateCursorCounter();
        updateButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Nicealts", width / 2, 4, 0xFFFFFF);
        if (user == null) drawLogin();
        else drawStore();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawLogin() {
        int centerX = width / 2;
        int centerY = height / 2;
        drawString(fontRendererObj, StringUtils.isBlank(apiKey) ? "Nicealts API key:" : "Nicealts API key (saved securely):", centerX - 150, centerY - 28, 0xAAAAAA);
        apiKeyField.drawTextBox();
        drawStatus(centerY + 44);
    }

    private void drawStore() {
        int centerX = width / 2;
        int top = storeTop();
        String subscription = StringUtils.isBlank(user.getSubscription()) ? "" : "  |  " + user.getSubscription();
        drawCenteredString(fontRendererObj, user.getUsername() + "  |  " + balance(user.getBalance()) + " credits" + subscription, centerX, 20, 0xA0FFA0);
        for (int product = 1; product <= 6; product++) {
            int column = (product - 1) % 2;
            int row = (product - 1) / 2;
            int amount = stock(product);
            drawCenteredString(fontRendererObj, amount + " in Stock", centerX - 78 + column * 156, top + 23 + row * 46, stockColor(amount));
        }
        drawStatus(height - 38);
    }

    private int storeTop() {
        return Math.max(40, height / 2 - 69);
    }

    private void drawStatus(int y) {
        String formatted = TextFormatting.translate(status == null ? "" : status);
        drawCenteredString(fontRendererObj, fontRendererObj.trimStringToWidth(formatted, Math.max(120, width - 20)), width / 2, y, 0xFFFFFF);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (apiKeyField != null) apiKeyField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == 1) mc.displayGuiScreen(previousScreen);
        else if (keyCode == 28 && user == null && connectButton != null) actionPerformed(connectButton);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception ignored) {
        }
        if (apiKeyField != null) apiKeyField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) return;
        if (button.id == 0) connect();
        else if (button.id == 2) mc.displayGuiScreen(previousScreen);
        else if (button.id == 10) refresh();
        else if (button.id == 11) importItems(new ArrayList<>(pendingItems));
        else if (button.id >= 101 && button.id <= 106) confirmPurchase(button.id - 100);
    }

    private void loadSavedKey() {
        if (savedKeyLoaded) return;
        savedKeyLoaded = true;
        try {
            apiKey = NiceAltsCredentialStore.load().trim();
            if (!apiKey.isEmpty()) status = "&7Saved API key found. Connecting...&r";
        } catch (Exception error) {
            status = "&cCould not load saved API key: " + safeMessage(error) + "&r";
        }
    }

    private void connect() {
        String entered = apiKeyField == null ? "" : apiKeyField.getText().trim();
        if (!entered.isEmpty()) apiKey = entered;
        if (apiKey.isEmpty() || isBusy()) {
            if (apiKey.isEmpty()) status = "&cAPI key is empty.&r";
            return;
        }
        final String key = apiKey;
        status = "&7Connecting to Nicealts...&r";
        task = storeExecutor.submit(() -> {
            try {
                User fetchedUser = NiceAltsClient.getBalance(key);
                Map<Integer, Integer> fetchedStock = NiceAltsClient.getStock();
                NiceAltsCredentialStore.save(key);
                mc.addScheduledTask(() -> {
                    apiKey = key;
                    user = fetchedUser;
                    stock = fetchedStock;
                    status = "&aConnected. API key saved securely.&r";
                    initGui();
                });
            } catch (Exception error) {
                status = "&c" + safeMessage(error) + "&r";
            }
        });
    }

    private void refresh() {
        if (isBusy()) return;
        status = "&7Refreshing Nicealts...&r";
        task = storeExecutor.submit(() -> {
            try {
                User fetchedUser = NiceAltsClient.getBalance(apiKey);
                Map<Integer, Integer> fetchedStock = NiceAltsClient.getStock();
                mc.addScheduledTask(() -> {
                    user = fetchedUser;
                    stock = fetchedStock;
                    status = "&aNicealts refreshed.&r";
                    initGui();
                });
            } catch (Exception error) {
                status = "&c" + safeMessage(error) + "&r";
            }
        });
    }

    private void confirmPurchase(int product) {
        pendingProduct = product;
        mc.displayGuiScreen(new GuiYesNo(
            this,
            "Buy 1x " + PRODUCT_NAMES[product] + "?",
            "Nicealts will charge the product's listed price.",
            "Purchase",
            "Cancel",
            CONFIRM_BASE + product
        ));
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        mc.displayGuiScreen(this);
        int product = id - CONFIRM_BASE;
        if (result && product == pendingProduct && product >= 1 && product <= 6) purchase(product);
    }

    private void purchase(int product) {
        if (isBusy()) return;
        status = "&7Purchasing " + PRODUCT_NAMES[product] + "...&r";
        task = storeExecutor.submit(() -> {
            try {
                List<String> items = NiceAltsClient.purchase(apiKey, product);
                pendingItems = new ArrayList<>(items);
                importItems(items);
            } catch (Exception error) {
                status = "&c" + safeMessage(error) + "&r";
            }
        });
    }

    private void importItems(List<String> items) {
        if (items == null || items.isEmpty()) return;
        status = "&7Importing " + items.size() + " Nicealts account(s)...&r";
        task = storeExecutor.submit(() -> {
            List<ImportedAccount> imported = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            for (String item : items) {
                try {
                    imported.add(PurchasedAccountImporter.importRefreshToken(item, authExecutor).get(120, TimeUnit.SECONDS));
                } catch (Exception error) {
                    failed.add(item);
                }
            }
            User fetchedUser = null;
            Map<Integer, Integer> fetchedStock = null;
            try {
                fetchedUser = NiceAltsClient.getBalance(apiKey);
                fetchedStock = NiceAltsClient.getStock();
            } catch (Exception ignored) {
            }
            User finalUser = fetchedUser;
            Map<Integer, Integer> finalStock = fetchedStock;
            mc.addScheduledTask(() -> finishImport(imported, failed, finalUser, finalStock));
        });
    }

    private void finishImport(List<ImportedAccount> imported, List<String> failed, User fetchedUser, Map<Integer, Integer> fetchedStock) {
        for (ImportedAccount account : imported) saveImportedAccount(account);
        if (!imported.isEmpty()) {
            UniversalAccountManager.resort();
            UniversalAccountManager.save();
        }
        pendingItems = new ArrayList<>(failed);
        if (fetchedUser != null) user = fetchedUser;
        if (fetchedStock != null) stock = fetchedStock;
        status = failed.isEmpty()
            ? "&aPurchased and imported " + imported.size() + " account(s).&r"
            : "&eImported " + imported.size() + "; " + failed.size() + " failed. Use Retry Import.&r";
        initGui();
    }

    private static void saveImportedAccount(ImportedAccount imported) {
        Session session = imported.getSession();
        Account match = null;
        for (Account account : UniversalAccountManager.accounts) {
            boolean sameUuid = !StringUtils.isBlank(account.getUuid()) && account.getUuid().equalsIgnoreCase(session.getPlayerID());
            boolean sameName = account.getUsername().equalsIgnoreCase(session.getUsername());
            if (sameUuid || sameName) {
                match = account;
                break;
            }
        }
        if (match == null) {
            UniversalAccountManager.accounts.add(new Account(imported.getType(), imported.getRefreshToken(), imported.getAccessToken(), session.getUsername(), session.getPlayerID(), 0L));
        } else {
            match.setType(imported.getType());
            match.setRefreshToken(imported.getRefreshToken());
            match.setAccessToken(imported.getAccessToken());
            match.setUsername(session.getUsername());
            match.setUuid(session.getPlayerID());
        }
    }

    private void updateButtons() {
        boolean busy = isBusy();
        if (connectButton != null) connectButton.enabled = !busy;
        if (retryButton != null) {
            retryButton.enabled = !busy && !pendingItems.isEmpty();
            retryButton.displayString = pendingItems.isEmpty() ? "Retry Import" : "Retry (" + pendingItems.size() + ")";
        }
        for (GuiButton button : buttonList) {
            if (button.id >= 101 && button.id <= 106) button.enabled = !busy && stock(button.id - 100) > 0;
            if (button.id == 10 || button.id == 2) button.enabled = !busy;
        }
    }

    private boolean isBusy() {
        return task != null && !task.isDone();
    }

    private int stock(int product) {
        Integer amount = stock.get(product);
        return amount == null ? 0 : amount;
    }

    private static int stockColor(int amount) {
        if (amount < 30) return 0xFF5555;
        if (amount < 100) return 0xFFFF55;
        return 0x55FF55;
    }

    private static String balance(double amount) {
        synchronized (BALANCE_FORMAT) {
            return BALANCE_FORMAT.format(amount);
        }
    }

    private static String safeMessage(Throwable error) {
        Throwable cause = error == null ? null : (error.getCause() == null ? error : error.getCause());
        String message = cause == null ? null : cause.getMessage();
        return StringUtils.isBlank(message) ? "Unknown error" : message.replace('\n', ' ').replace('\r', ' ');
    }
}
