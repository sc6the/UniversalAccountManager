package me.proxycracked.universalaccountmanager.gui;

import java.net.URI;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.AccountLogin;
import me.proxycracked.universalaccountmanager.store.DeliveryLog;
import me.proxycracked.universalaccountmanager.store.FernanProvider;
import me.proxycracked.universalaccountmanager.store.NiceAltsProvider;
import me.proxycracked.universalaccountmanager.store.RateLimitSignal;
import me.proxycracked.universalaccountmanager.store.StoreAccount;
import me.proxycracked.universalaccountmanager.store.StoreCapabilities;
import me.proxycracked.universalaccountmanager.store.StoreCredentialStore;
import me.proxycracked.universalaccountmanager.store.StoreImportCandidate;
import me.proxycracked.universalaccountmanager.store.StoreImportTracker;
import me.proxycracked.universalaccountmanager.store.StoreImporter;
import me.proxycracked.universalaccountmanager.store.StoreItem;
import me.proxycracked.universalaccountmanager.store.StoreOrder;
import me.proxycracked.universalaccountmanager.store.StoreOrderSummary;
import me.proxycracked.universalaccountmanager.store.StoreProduct;
import me.proxycracked.universalaccountmanager.store.StoreProvider;
import me.proxycracked.universalaccountmanager.store.StoreStatus;
import me.proxycracked.universalaccountmanager.utils.SystemUtils;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.util.Session;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

/**
 * One store screen for every shop.
 *
 * <p>Localts, Nicealts and Fernan Club used to mean two different screens with two different sets
 * of features; the shop is now a {@link StoreProvider} and everything here - connecting, browsing
 * categories, quantity, the confirmation, importing, retrying and re-importing past purchases -
 * works the same wherever the accounts came from. The only per-shop difference is which extra
 * buttons {@link StoreCapabilities} lights up.</p>
 */
public class GuiStore extends GuiScreen implements GuiYesNoCallback {
    private static final DecimalFormat CREDITS_FORMAT = new DecimalFormat("0.##");
    private static final int PURCHASE_CONFIRM_ID = 4100;
    private static final int MAX_CATEGORY_BUTTONS = 12;
    /** Order reads run in parallel; these two together pace them under the shops' rate limits. */
    private static final int HISTORY_FETCH_THREADS = 4;
    private static final long HISTORY_MIN_INTERVAL_MILLIS = 200L;
    /** Uncached orders read per round before the barren check decides whether to keep going. */
    private static final int HISTORY_WINDOW = 8;
    /**
     * Consecutive freshly-read orders with nothing importable before a normal scan stops.
     *
     * <p>History is read newest first and orders are imported in order, so a run this long with
     * nothing new means everything older was imported too. Shops rate limit hard - Localts starts
     * throttling around the twentieth read - so the only real speed-up is not reading orders whose
     * answer is already known.</p>
     */
    private static final int BARREN_ORDER_LIMIT = 12;
    /** Orders cached per file write during a scan, so a cancelled scan keeps most of its progress. */
    private static final int HISTORY_FLUSH_EVERY = 25;

    private final StoreProvider provider;
    private final ExecutorService storeExecutor;
    private final ExecutorService authExecutor;

    private GuiScreen previousScreen;
    private GuiTextField apiKeyField;
    private GuiTextField orderIdField;
    private GuiButton connectButton;
    private GuiButton backButton;
    private GuiButton retryButton;
    private GuiButton refundButton;
    private GuiButton importOrderButton;
    private GuiButton previousProductButton;
    private GuiButton nextProductButton;
    private GuiButton decreaseButton;
    private GuiButton increaseButton;
    private GuiButton purchaseButton;

    private String apiKey = "";
    private volatile String status;
    private StoreAccount account;
    private List<StoreProduct> allProducts = Collections.emptyList();
    private List<String> categories = Collections.emptyList();
    private List<StoreProduct> products = Collections.emptyList();
    private String activeCategory = "";
    private int productIndex;
    private int amount = 1;
    private Future<?> task;
    private boolean savedKeyLoaded;
    private boolean savedKeyAttempted;
    private boolean productListOpen;
    private String lastOrderId = "";
    private int orderIdFieldY;
    private GuiButton importPreviousButton;
    private volatile boolean scanning;
    private volatile boolean scanCancelled;
    private List<StoreItem> retryItems = Collections.emptyList();
    private List<String> refundableUuids = Collections.emptyList();
    private String refundableOrderId = "";

    public GuiStore(GuiScreen previousScreen, StoreProvider provider) {
        this.previousScreen = previousScreen;
        this.provider = provider;
        this.storeExecutor = Executors.newSingleThreadExecutor(daemonFactory(provider.displayName() + "-Store"));
        this.authExecutor = Executors.newFixedThreadPool(2, daemonFactory(provider.displayName() + "-Auth"));
        this.status = "&7Paste your " + provider.displayName() + " API key.&r";
    }

    public StoreProvider getProvider() {
        return provider;
    }

    void setPreviousScreen(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    private static ThreadFactory daemonFactory(final String name) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, name);
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    // ------------------------------------------------------------------ layout

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        // The screen is rebuilt on every state change, so no reference may outlive the button list.
        connectButton = null;
        importPreviousButton = null;
        retryButton = null;
        refundButton = null;
        importOrderButton = null;
        previousProductButton = null;
        nextProductButton = null;
        decreaseButton = null;
        increaseButton = null;
        purchaseButton = null;
        if (account == null) {
            loadSavedKey();
            initLoginControls();
            if (!savedKeyAttempted && !StringUtils.isBlank(apiKey)) {
                savedKeyAttempted = true;
                connect();
            }
        } else if (!productListOpen) {
            initCategoryControls();
        } else {
            initProductControls();
        }
        updateButtonState();
    }

    private void initLoginControls() {
        int centerX = width / 2;
        int centerY = height / 2;
        apiKeyField = new GuiTextField(0, fontRendererObj, centerX - 150, centerY - 14, 300, 20);
        apiKeyField.setMaxStringLength(512);
        apiKeyField.setText("");
        apiKeyField.setFocused(StringUtils.isBlank(apiKey));
        buttonList.add(connectButton = new GuiButton(0, centerX - 150, centerY + 14, 146, 20, "Connect"));
        buttonList.add(new GuiButton(1, centerX + 4, centerY + 14, 146, 20, "Open " + provider.displayName()));
        buttonList.add(backButton = new GuiButton(2, centerX - 73, height - 24, 146, 20, "Back"));
    }

    private void initCategoryControls() {
        int centerX = width / 2;
        StoreCapabilities capabilities = provider.capabilities();

        List<String> extras = new ArrayList<String>();
        extras.add("Import Previous");
        if (capabilities.supportsCustomPurchase()) {
            extras.add("Custom Purchase");
        }
        if (capabilities.supportsGenerate()) {
            extras.add("Generate");
        }
        if (capabilities.supportsRedeem()) {
            extras.add("Redeem Key");
        }
        extras.add("Retry Import");
        if (capabilities.supportsRefunds()) {
            extras.add("Request Refund");
        }

        int categoryRows = (Math.min(categories.size(), MAX_CATEGORY_BUTTONS) + 1) / 2;
        int extraRows = (extras.size() + 2) / 3;
        int needed = categoryRows * 22 + extraRows * 22 + 30 + 30;
        int top = Math.max(34, Math.min(40, height - 30 - needed));

        int y = top;
        for (int index = 0; index < categories.size() && index < MAX_CATEGORY_BUTTONS; index++) {
            int column = index % 2;
            int row = index / 2;
            String category = categories.get(index);
            buttonList.add(new GuiButton(
                200 + index, centerX - 152 + column * 156, y + row * 22, 148, 20,
                trim(category + " (" + stockFor(category) + ")", 140)
            ));
        }
        y += categoryRows * 22 + 4;

        for (int index = 0; index < extras.size(); index++) {
            int column = index % 3;
            int row = index / 3;
            String label = extras.get(index);
            GuiButton button = new GuiButton(extraId(label), centerX - 152 + column * 102, y + row * 22, 100, 20, label);
            buttonList.add(button);
            if ("Import Previous".equals(label)) {
                importPreviousButton = button;
            } else if ("Retry Import".equals(label)) {
                retryButton = button;
            } else if ("Request Refund".equals(label)) {
                refundButton = button;
            }
        }
        y += extraRows * 22 + 6;

        String previousOrderId = orderIdField == null ? lastOrderId : orderIdField.getText();
        orderIdFieldY = y + 10;
        orderIdField = new GuiTextField(1, fontRendererObj, centerX - 152, orderIdFieldY, 196, 20);
        orderIdField.setMaxStringLength(64);
        orderIdField.setText(previousOrderId == null ? "" : previousOrderId);
        buttonList.add(importOrderButton = new GuiButton(46, centerX + 48, y + 10, 104, 20, "Import " + provider.orderIdLabel()));

        buttonList.add(new GuiButton(47, centerX - 152, height - 24, 148, 20, "Refresh"));
        buttonList.add(backButton = new GuiButton(2, centerX + 4, height - 24, 148, 20, "Back"));
    }

    private static int extraId(String label) {
        if ("Import Previous".equals(label)) {
            return 40;
        }
        if ("Custom Purchase".equals(label)) {
            return 41;
        }
        if ("Generate".equals(label)) {
            return 42;
        }
        if ("Redeem Key".equals(label)) {
            return 43;
        }
        if ("Request Refund".equals(label)) {
            return 45;
        }
        return 44;
    }

    private void initProductControls() {
        int centerX = width / 2;
        int centerY = height / 2;
        boolean quantity = provider.capabilities().supportsQuantity();
        buttonList.add(previousProductButton = new GuiButton(10, centerX - 150, centerY - 12, 45, 20, "<"));
        buttonList.add(nextProductButton = new GuiButton(11, centerX + 105, centerY - 12, 45, 20, ">"));
        if (quantity) {
            buttonList.add(decreaseButton = new GuiButton(12, centerX - 150, centerY + 16, 45, 20, "-"));
            buttonList.add(increaseButton = new GuiButton(13, centerX - 100, centerY + 16, 45, 20, "+"));
            buttonList.add(purchaseButton = new GuiButton(14, centerX - 50, centerY + 16, 200, 20, "Review Purchase"));
        } else {
            buttonList.add(purchaseButton = new GuiButton(14, centerX - 150, centerY + 16, 300, 20, "Review Purchase"));
        }
        buttonList.add(new GuiButton(15, centerX - 150, height - 24, 146, 20, "Refresh"));
        buttonList.add(backButton = new GuiButton(16, centerX + 4, height - 24, 146, 20, "Categories"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        if (apiKeyField != null) {
            apiKeyField.updateCursorCounter();
        }
        if (orderIdField != null) {
            orderIdField.updateCursorCounter();
        }
        updateButtonState();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, provider.displayName(), width / 2, 4, 0xFFFFFF);
        if (account == null) {
            drawLoginScreen();
        } else if (!productListOpen) {
            drawCategoryScreen();
        } else {
            drawProductScreen();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawLoginScreen() {
        int centerX = width / 2;
        int centerY = height / 2;
        String label = StringUtils.isBlank(apiKey)
            ? provider.displayName() + " API key:"
            : provider.displayName() + " API key (saved securely for this Windows user):";
        drawString(fontRendererObj, label, centerX - 150, centerY - 28, 0xAAAAAA);
        apiKeyField.drawTextBox();
        drawStatus(centerY + 44);
    }

    private void drawCategoryScreen() {
        drawCenteredString(fontRendererObj, accountLine(), width / 2, 20, 0xA0FFA0);
        if (orderIdField != null) {
            drawString(fontRendererObj, "Re-import one delivery by its " + provider.orderIdLabel().toLowerCase() + ":",
                width / 2 - 152, orderIdFieldY - 10, 0xAAAAAA);
            orderIdField.drawTextBox();
        }
        drawStatus(height - 38);
    }

    private String accountLine() {
        StringBuilder line = new StringBuilder(account.getUsername());
        line.append("  |  ").append(credits(account.getBalance())).append(" credits");
        if (!StringUtils.isBlank(account.getDetail())) {
            line.append("  |  ").append(account.getDetail());
        }
        return line.toString();
    }

    private void drawProductScreen() {
        int centerX = width / 2;
        int centerY = height / 2;
        drawCenteredString(fontRendererObj, accountLine(), centerX, 20, 0xA0FFA0);
        StoreProduct product = selectedProduct();
        if (product == null) {
            drawCenteredString(fontRendererObj, "Nothing in this category is in stock.", centerX, centerY - 38, 0xFF7777);
        } else {
            String position = (productIndex + 1) + " / " + products.size();
            drawCenteredString(fontRendererObj, position + "  " + trim(product.getName(), 290), centerX, centerY - 48, 0xFFFFFF);
            drawCenteredString(fontRendererObj, trim(product.getDescription(), 290), centerX, centerY - 34, 0xAAAAAA);
            drawCenteredString(fontRendererObj,
                "Stock: " + product.getStock() + "  |  Unit: " + credits(product.getPrice()) + "  |  Quantity: " + amount,
                centerX, centerY - 20, 0xDDDDDD);
            double discount = product.discountFor(amount);
            String price = "Total: " + credits(product.totalFor(amount)) + " credits";
            if (discount > 0.0D) {
                price += " (" + credits(discount) + "% discount)";
            }
            drawCenteredString(fontRendererObj, price, centerX, centerY + 44, 0xFFFF80);
        }
        if (!StringUtils.isBlank(lastOrderId)) {
            drawCenteredString(fontRendererObj, "Last " + provider.orderIdLabel().toLowerCase() + ": " + lastOrderId,
                centerX, centerY + 58, 0x888888);
        }
        drawStatus(height - 38);
    }

    private void drawStatus(int y) {
        if (!StringUtils.isBlank(status)) {
            drawCenteredString(fontRendererObj, trim(TextFormatting.translate(status), Math.max(120, width - 20)),
                width / 2, y, 0xFFFFFF);
        }
    }

    private String trim(String text, int maxWidth) {
        return fontRendererObj.trimStringToWidth(text == null ? "" : text, maxWidth);
    }

    private static String credits(double amount) {
        synchronized (CREDITS_FORMAT) {
            return CREDITS_FORMAT.format(amount);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (apiKeyField != null && account == null) {
            apiKeyField.textboxKeyTyped(typedChar, keyCode);
        }
        if (orderIdField != null && account != null && !productListOpen) {
            orderIdField.textboxKeyTyped(typedChar, keyCode);
        }
        if (keyCode == 1) {
            actionPerformed(backButton);
        } else if (keyCode == 28 && account == null && connectButton != null) {
            actionPerformed(connectButton);
        } else if (keyCode == 28 && orderIdField != null && orderIdField.isFocused() && importOrderButton != null) {
            actionPerformed(importOrderButton);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception ignored) {
        }
        if (apiKeyField != null && account == null) {
            apiKeyField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (orderIdField != null && account != null && !productListOpen) {
            orderIdField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        if (button.id >= 200 && button.id < 200 + MAX_CATEGORY_BUTTONS) {
            int categoryIndex = button.id - 200;
            if (categoryIndex < categories.size()) {
                openCategory(categories.get(categoryIndex));
            }
            return;
        }
        switch (button.id) {
            case 0:
                connect();
                break;
            case 1:
                openWebsite();
                break;
            case 2:
                mc.displayGuiScreen(previousScreen);
                break;
            case 10:
                selectProduct(productIndex - 1);
                break;
            case 11:
                selectProduct(productIndex + 1);
                break;
            case 12:
                amount = Math.max(1, amount - 1);
                break;
            case 13:
                StoreProduct product = selectedProduct();
                amount = Math.min(product == null ? 1 : product.maxAmount(), amount + 1);
                break;
            case 14:
                showPurchaseConfirmation();
                break;
            case 15:
            case 47:
                refresh();
                break;
            case 16:
                productListOpen = false;
                status = "&7Choose an account type.&r";
                initGui();
                break;
            case 40:
                if (scanning) {
                    scanCancelled = true;
                    status = "&7Stopping the scan...&r";
                } else {
                    importPreviousPurchases();
                }
                break;
            case 41:
                openCustomPurchase();
                break;
            case 42:
                openGenerate();
                break;
            case 43:
                openRedeem();
                break;
            case 44:
                importItems(new ArrayList<StoreItem>(retryItems), refundableOrderId, "Retried");
                break;
            case 45:
                requestRefund();
                break;
            case 46:
                importByOrderId();
                break;
            default:
                break;
        }
        updateButtonState();
    }

    private void openWebsite() {
        try {
            SystemUtils.openWebLink(new URI(provider.websiteUrl()));
        } catch (Exception error) {
            status = "&cCould not open " + provider.websiteUrl() + "&r";
        }
    }

    // ------------------------------------------------------------------ shop calls

    private void loadSavedKey() {
        if (savedKeyLoaded) {
            return;
        }
        savedKeyLoaded = true;
        try {
            apiKey = StoreCredentialStore.load(provider.id()).trim();
            if (!StringUtils.isBlank(apiKey)) {
                status = "&7Saved API key found. Connecting to " + provider.displayName() + "...&r";
            }
        } catch (Exception error) {
            apiKey = "";
            status = "&cCould not load the saved API key: " + safeMessage(error) + "&r";
        }
    }

    private void connect() {
        String entered = apiKeyField == null ? "" : apiKeyField.getText().trim();
        if (!StringUtils.isBlank(entered)) {
            apiKey = entered;
        }
        if (StringUtils.isBlank(apiKey) || isBusy()) {
            if (StringUtils.isBlank(apiKey)) {
                status = "&cAPI key is empty.&r";
            }
            return;
        }
        final String connectingKey = apiKey;
        status = "&7Connecting to " + provider.displayName() + "...&r";
        task = storeExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final StoreAccount connected = provider.connect(connectingKey);
                    final List<StoreProduct> fetched = provider.products();
                    String warning = null;
                    try {
                        StoreCredentialStore.save(provider.id(), connectingKey);
                    } catch (Exception saveError) {
                        warning = safeMessage(saveError);
                    }
                    final String credentialWarning = warning;
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            apiKey = connectingKey;
                            account = connected;
                            setProducts(fetched);
                            productListOpen = false;
                            if (credentialWarning != null) {
                                status = "&eConnected, but the API key could not be saved: " + credentialWarning + "&r";
                            } else {
                                status = fetched.isEmpty()
                                    ? "&cNothing is in stock right now.&r"
                                    : "&aConnected. API key saved securely.&r";
                            }
                            initGui();
                        }
                    });
                } catch (Exception error) {
                    status = "&c" + safeMessage(error) + "&r";
                }
            }
        });
    }

    private void refresh() {
        if (isBusy()) {
            return;
        }
        status = "&7Refreshing " + provider.displayName() + "...&r";
        task = storeExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final StoreAccount refreshed = provider.refreshAccount();
                    final List<StoreProduct> fetched = provider.products();
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            account = refreshed;
                            setProducts(fetched);
                            status = "&aRefreshed.&r";
                            initGui();
                        }
                    });
                } catch (Exception error) {
                    status = "&c" + safeMessage(error) + "&r";
                }
            }
        });
    }

    private void setProducts(List<StoreProduct> fetched) {
        allProducts = fetched;
        Set<String> names = new LinkedHashSet<String>();
        for (StoreProduct product : fetched) {
            names.add(product.getCategory());
        }
        categories = new ArrayList<String>(names);
        Collections.sort(categories, String.CASE_INSENSITIVE_ORDER);
        if (!StringUtils.isBlank(activeCategory)) {
            products = filterProducts(activeCategory);
            selectProduct(Math.min(productIndex, Math.max(0, products.size() - 1)));
        }
    }

    private void openCategory(String category) {
        activeCategory = category;
        products = filterProducts(category);
        productIndex = 0;
        amount = 1;
        productListOpen = true;
        status = products.isEmpty() ? "&cNothing in this category is in stock.&r" : "&7Select a product and quantity.&r";
        initGui();
    }

    private List<StoreProduct> filterProducts(String category) {
        List<StoreProduct> filtered = new ArrayList<StoreProduct>();
        for (StoreProduct product : allProducts) {
            if (product.getStock() > 0 && product.getCategory().equals(category)) {
                filtered.add(product);
            }
        }
        return filtered;
    }

    private int stockFor(String category) {
        int stock = 0;
        for (StoreProduct product : allProducts) {
            if (product.getCategory().equals(category)) {
                stock += product.getStock();
            }
        }
        return stock;
    }

    private void selectProduct(int index) {
        if (products.isEmpty()) {
            productIndex = 0;
            amount = 1;
            return;
        }
        productIndex = (index % products.size() + products.size()) % products.size();
        amount = Math.max(1, Math.min(amount, Math.max(1, selectedProduct().maxAmount())));
    }

    private StoreProduct selectedProduct() {
        return products.isEmpty() || productIndex < 0 || productIndex >= products.size() ? null : products.get(productIndex);
    }

    // ------------------------------------------------------------------ buying

    private void showPurchaseConfirmation() {
        StoreProduct product = selectedProduct();
        if (product == null || !canPurchase(product)) {
            return;
        }
        String line1 = "Buy " + amount + "x " + product.getName() + "?";
        String line2 = "This will deduct " + credits(product.totalFor(amount)) + " credits from your "
            + provider.displayName() + " balance.";
        mc.displayGuiScreen(new GuiYesNo(this, line1, line2, "Purchase", "Cancel", PURCHASE_CONFIRM_ID));
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        mc.displayGuiScreen(this);
        if (id == PURCHASE_CONFIRM_ID && result) {
            startPurchase();
        }
    }

    private void startPurchase() {
        final StoreProduct product = selectedProduct();
        if (product == null || !canPurchase(product) || isBusy()) {
            return;
        }
        final int purchaseAmount = amount;
        status = "&7Submitting confirmed purchase...&r";
        task = storeExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    StoreOrder order = provider.purchase(product, purchaseAmount, statusSink());
                    lastOrderId = order.getId();
                    DeliveryLog.record(provider.id(), order);
                    runImport(order.getItems(), order.getId(), "Purchased and imported");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    status = "&cPurchase monitoring was cancelled. Check your order history.&r";
                } catch (Exception error) {
                    status = "&c" + safeMessage(error) + "&r";
                }
            }
        });
    }

    private boolean canPurchase(StoreProduct product) {
        return amount > 0
            && amount <= Math.max(1, product.maxAmount())
            && account != null
            && product.totalFor(amount) <= account.getBalance();
    }

    /** Entry point for the Nicealts custom purchase and generator screens. */
    public void acceptOrder(final StoreOrder order, final String verb) {
        lastOrderId = order.getId();
        DeliveryLog.record(provider.id(), order);
        mc.displayGuiScreen(this);
        if (isBusy()) {
            status = "&cThe store is busy; retry from " + provider.orderIdLabel() + " " + order.getId() + ".&r";
            return;
        }
        task = storeExecutor.submit(new Runnable() {
            @Override
            public void run() {
                runImport(order.getItems(), order.getId(), verb);
            }
        });
    }

    // ------------------------------------------------------------------ importing

    /** Import from the client thread: hands the work to the store executor. */
    private void importItems(final List<StoreItem> items, final String orderId, final String verb) {
        if (items.isEmpty() || isBusy()) {
            return;
        }
        task = storeExecutor.submit(new Runnable() {
            @Override
            public void run() {
                runImport(items, orderId, verb);
            }
        });
    }

    public void importCandidates(List<StoreImportCandidate> candidates) {
        List<StoreItem> items = new ArrayList<StoreItem>();
        String orderId = "";
        for (StoreImportCandidate candidate : candidates) {
            items.add(candidate.getItem());
            orderId = candidate.getOrderId();
        }
        importItems(items, candidates.size() == 1 ? orderId : "", "Imported");
    }

    /** Runs on the store executor. Every shop's deliveries go through exactly this path. */
    private void runImport(List<StoreItem> items, String orderId, String verb) {
        final List<AccountLogin.Result> imported = new ArrayList<AccountLogin.Result>();
        final List<String> importedKeys = new ArrayList<String>();
        final List<StoreItem> failedItems = new ArrayList<StoreItem>();
        final List<String> failedUuids = new ArrayList<String>();
        String firstFailure = null;

        int index = 0;
        for (StoreItem item : items) {
            index++;
            status = "&7Importing " + index + "/" + items.size() + "...&r";
            String key = StoreImportTracker.key(provider.id(), item.getId());
            try {
                imported.add(StoreImporter.importItem(item, authExecutor));
                importedKeys.add(key);
            } catch (Exception error) {
                String reason = safeMessage(error);
                if (firstFailure == null) {
                    firstFailure = reason;
                }
                failedItems.add(item);
                if (!StringUtils.isBlank(item.getUuid())) {
                    failedUuids.add(item.getUuid());
                }
                System.err.println("[UniversalAccountManager] " + provider.displayName()
                    + " import failed (" + orderId + " / " + item.getId() + "): " + reason);
                StoreImportTracker.markFailed(key, reason);
            }
        }

        StoreAccount updatedAccount = null;
        List<StoreProduct> updatedProducts = null;
        try {
            updatedAccount = provider.refreshAccount();
            updatedProducts = provider.products();
        } catch (Exception ignored) {
            // The purchase already happened; a stale balance is not worth failing over.
        }

        final StoreAccount finalAccount = updatedAccount;
        final List<StoreProduct> finalProducts = updatedProducts;
        final String failure = firstFailure;
        final String finalOrderId = orderId;
        final String finalVerb = verb;
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                finishImport(imported, importedKeys, failedItems, failedUuids, failure,
                    finalAccount, finalProducts, finalOrderId, finalVerb);
            }
        });
    }

    private void finishImport(List<AccountLogin.Result> imported, List<String> importedKeys,
                              List<StoreItem> failedItems, List<String> failedUuids, String failureReason,
                              StoreAccount updatedAccount, List<StoreProduct> updatedProducts,
                              String orderId, String verb) {
        for (int index = 0; index < imported.size(); index++) {
            AccountLogin.Result result = imported.get(index);
            AccountLogin.upsert(result);
            Session session = result.getSession();
            if (index < importedKeys.size()) {
                StoreImportTracker.markImported(importedKeys.get(index), session.getPlayerID(), session.getUsername());
            }
        }
        if (!imported.isEmpty()) {
            UniversalAccountManager.resort();
            UniversalAccountManager.save();
        }

        retryItems = failedItems;
        refundableUuids = failedUuids;
        refundableOrderId = orderId;
        if (updatedAccount != null) {
            account = updatedAccount;
        }
        if (updatedProducts != null) {
            setProducts(updatedProducts);
        }

        if (imported.isEmpty() && failedItems.isEmpty()) {
            status = "&7Nothing new to import.&r";
        } else if (failedItems.isEmpty()) {
            status = "&a" + verb + " " + imported.size() + " account(s).&r";
        } else {
            status = "&e" + verb + " " + imported.size() + "; " + failedItems.size() + " failed: "
                + (StringUtils.isBlank(failureReason) ? "unknown error" : failureReason) + "&r";
        }
        initGui();
    }

    // ------------------------------------------------------------------ history

    private void importPreviousPurchases() {
        if (isBusy()) {
            return;
        }
        // Shift means "dig": read the whole history and re-offer items cached as dead.
        final boolean deep = isShiftKeyDown();
        scanCancelled = false;
        scanning = true;
        status = "&7Looking for past purchases...&r";
        task = storeExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    runHistoryScan(deep);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                } catch (Exception error) {
                    status = "&c" + safeMessage(error) + "&r";
                } finally {
                    scanning = false;
                }
            }
        });
    }

    /**
     * Reads the shop's history and offers whatever has not become an account yet.
     *
     * <p>Scanning used to be one blocking order read after another with a fixed 400ms sleep between
     * them, and a single failed read ended the run - a Localts account with a few hundred orders
     * took minutes and could still come back with nothing. Now orders already in the delivery log
     * cost nothing, the rest are fetched on a small pool behind a shared pacer that widens when a
     * shop pushes back, a read that fails is counted rather than fatal, and every order that is
     * read is cached, so a second scan - or resuming a cancelled one - is nearly free.</p>
     */
    private void runHistoryScan(boolean deep) throws Exception {
        List<StoreOrderSummary> summaries = mergedHistory();
        Collections.sort(summaries, new Comparator<StoreOrderSummary>() {
            @Override
            public int compare(StoreOrderSummary left, StoreOrderSummary right) {
                return Long.compare(right.getTimestamp(), left.getTimestamp());
            }
        });

        // Cached orders are free, so they are never a reason to stop and never counted as barren.
        List<StoreOrderSummary> missing = new ArrayList<StoreOrderSummary>();
        if (provider.capabilities().supportsOrderLookup()) {
            for (StoreOrderSummary summary : summaries) {
                if (!DeliveryLog.contains(provider.id(), summary.getId())) {
                    missing.add(summary);
                }
            }
        }

        int failures = 0;
        int read = 0;
        int barren = 0;
        boolean stoppedEarly = false;
        if (!missing.isEmpty()) {
            Pacer pacer = new Pacer(HISTORY_MIN_INTERVAL_MILLIS);
            for (int offset = 0; offset < missing.size() && !scanCancelled; offset += HISTORY_WINDOW) {
                List<StoreOrderSummary> window =
                    missing.subList(offset, Math.min(missing.size(), offset + HISTORY_WINDOW));
                failures += fetchOrders(window, pacer, offset, missing.size());
                read += window.size();

                for (StoreOrderSummary summary : window) {
                    if (hasImportable(DeliveryLog.order(provider.id(), summary.getId()), deep, false)) {
                        barren = 0;
                    } else {
                        barren++;
                    }
                }
                if (!deep && barren >= BARREN_ORDER_LIMIT) {
                    stoppedEarly = true;
                    break;
                }
            }
        }

        // One ordered pass so the offered accounts stay newest first whatever order they arrived in.
        final List<StoreImportCandidate> candidates = new ArrayList<StoreImportCandidate>();
        int skipped = 0;
        for (StoreOrderSummary summary : summaries) {
            skipped += collect(DeliveryLog.order(provider.id(), summary.getId()), candidates, deep, false);
        }

        final int deadItems = skipped;
        final int unreadable = failures;
        final boolean cancelled = scanCancelled;
        final boolean partial = stoppedEarly;
        final int ordersRead = read;
        final int ordersLeft = missing.size() - read;
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                String note = "";
                if (cancelled) {
                    note = " Stopped after " + ordersRead + " order(s); run it again to carry on.";
                } else if (partial) {
                    note = " Read the newest " + ordersRead + " order(s) and stopped; "
                        + ordersLeft + " older one(s) look imported already - hold Shift for a full scan.";
                } else if (unreadable > 0) {
                    note = " " + unreadable + " order(s) could not be read; run it again to retry those.";
                }
                if (candidates.isEmpty()) {
                    if (deadItems > 0) {
                        status = "&7No new purchases. " + deadItems
                            + " dead item(s) skipped; hold Shift to retry those too." + note + "&r";
                    } else {
                        status = "&7No new purchases to import." + note + "&r";
                    }
                    initGui();
                } else {
                    if (!note.isEmpty()) {
                        status = "&e" + note.trim() + "&r";
                    }
                    mc.displayGuiScreen(new GuiStoreImportSelection(
                        GuiStore.this, "Import " + provider.displayName() + " Accounts", candidates));
                }
            }
        });
    }

    /** Whether an order still holds anything worth offering, without consuming it. */
    private boolean hasImportable(StoreOrder order, boolean includeFailed, boolean includeImported) {
        if (order == null || !order.isReady()) {
            return false;
        }
        for (StoreItem item : order.getItems()) {
            if (!item.isSupported()) {
                continue;
            }
            String key = StoreImportTracker.key(provider.id(), item.getId());
            if (StoreImportTracker.isSatisfied(key) && !includeImported) {
                continue;
            }
            if (StoreImportTracker.isFailed(key) && !includeFailed) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** Fetches and caches one window of orders. Returns how many could not be read. */
    private int fetchOrders(final List<StoreOrderSummary> window, final Pacer pacer,
                            final int alreadyRead, final int total) throws Exception {
        final AtomicInteger failures = new AtomicInteger();
        final AtomicInteger done = new AtomicInteger(alreadyRead);

        ExecutorService pool = Executors.newFixedThreadPool(
            Math.min(HISTORY_FETCH_THREADS, window.size()), daemonFactory(provider.displayName() + "-History"));
        for (final StoreOrderSummary summary : window) {
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    if (scanCancelled) {
                        return;
                    }
                    try {
                        pacer.await();
                        if (scanCancelled) {
                            return;
                        }
                        StoreOrder order = provider.order(summary.getId());
                        if (order != null && order.isReady() && !order.getItems().isEmpty()) {
                            DeliveryLog.recordDeferred(provider.id(), order, summary.getTimestamp());
                        }
                        if (RateLimitSignal.consume() < 0L) {
                            pacer.onSuccess();
                        }
                    } catch (Exception error) {
                        if (isRateLimited(error)) {
                            pacer.slowDown(0L);
                        }
                        failures.incrementAndGet();
                    } finally {
                        // A throttled read that the client retried its way out of still counts.
                        long retryAfter = RateLimitSignal.consume();
                        if (retryAfter >= 0L) {
                            pacer.slowDown(retryAfter);
                        }
                        int finished = done.incrementAndGet();
                        status = "&7Reading order " + finished + "/" + total + "...&r";
                        if (finished % HISTORY_FLUSH_EVERY == 0) {
                            DeliveryLog.flush();
                        }
                    }
                }
            });
        }
        pool.shutdown();
        while (!pool.awaitTermination(200L, TimeUnit.MILLISECONDS)) {
            if (scanCancelled) {
                pool.shutdownNow();
                break;
            }
        }
        DeliveryLog.flush();
        return failures.get();
    }

    private static boolean isRateLimited(Throwable error) {
        String message = safeMessage(error).toLowerCase(Locale.ROOT);
        return message.contains("429") || message.contains("rate limit");
    }

    /**
     * Spaces the start of every request by a shared interval, and widens that interval when a shop
     * pushes back, so several threads can read history at once without tripping a rate limit.
     */
    private static final class Pacer {
        private static final long MAX_INTERVAL_MILLIS = 4000L;
        private static final long MIN_INTERVAL_MILLIS = 100L;
        private static final int SUCCESSES_BEFORE_SPEEDUP = 12;
        private long interval;
        private long nextSlot;
        private int successes;

        private Pacer(long interval) {
            this.interval = interval;
        }

        private void await() throws InterruptedException {
            long wait;
            synchronized (this) {
                long now = System.currentTimeMillis();
                long slot = Math.max(now, nextSlot);
                nextSlot = slot + interval;
                wait = slot - now;
            }
            // Slept outside the lock so the threads overlap; only their start times are spaced.
            if (wait > 0L) {
                Thread.sleep(wait);
            }
        }

        /**
          * A rate limit widens the gap and pushes every queued slot back, not just this thread's.
          *
          * @param retryAfterMillis what the shop asked for, or 0 when it did not say
          */
        private synchronized void slowDown(long retryAfterMillis) {
            successes = 0;
            interval = Math.min(MAX_INTERVAL_MILLIS, interval * 2L);
            nextSlot = Math.max(nextSlot, System.currentTimeMillis() + Math.max(retryAfterMillis, interval));
        }

        /** Creeps back towards the floor once a shop has stopped complaining. */
        private synchronized void onSuccess() {
            if (++successes >= SUCCESSES_BEFORE_SPEEDUP) {
                successes = 0;
                interval = Math.max(MIN_INTERVAL_MILLIS, interval * 3L / 4L);
            }
        }
    }

    /**
     * Everything the shop remembers plus everything this machine has been delivered. Shops without
     * a history endpoint are carried entirely by the local log, which is what makes re-importing
     * work the same on all three.
     */
    private List<StoreOrderSummary> mergedHistory() throws Exception {
        List<StoreOrderSummary> merged = new ArrayList<StoreOrderSummary>();
        Set<String> seen = new LinkedHashSet<String>();
        for (StoreOrderSummary summary : DeliveryLog.summaries(provider.id())) {
            if (seen.add(summary.getId())) {
                merged.add(summary);
            }
        }
        if (provider.capabilities().supportsServerHistory()) {
            for (StoreOrderSummary summary : provider.orderHistory(statusSink())) {
                if (seen.add(summary.getId())) {
                    merged.add(summary);
                }
            }
        }
        return merged;
    }

    /** Returns how many items were skipped because a previous import failed for good. */
    private int collect(StoreOrder order, List<StoreImportCandidate> candidates,
                        boolean includeFailed, boolean includeImported) {
        if (order == null || !order.isReady()) {
            return 0;
        }
        int skipped = 0;
        for (StoreItem item : order.getItems()) {
            if (!item.isSupported()) {
                continue;
            }
            String key = StoreImportTracker.key(provider.id(), item.getId());
            if (StoreImportTracker.isSatisfied(key)) {
                if (!includeImported) {
                    continue;
                }
                StoreImportTracker.clearImported(key);
            }
            if (StoreImportTracker.isFailed(key)) {
                if (!includeFailed) {
                    skipped++;
                    continue;
                }
                StoreImportTracker.clearFailure(key);
            }
            candidates.add(new StoreImportCandidate(item, order.getId()));
        }
        return skipped;
    }

    private void importByOrderId() {
        if (isBusy()) {
            return;
        }
        final String orderId = orderIdField == null ? "" : orderIdField.getText().trim();
        if (StringUtils.isBlank(orderId)) {
            status = "&cEnter the " + provider.orderIdLabel().toLowerCase() + " to re-import.&r";
            return;
        }
        // One order asked for by id, with Shift held, re-offers its dead AND already-imported items.
        // That is the only way back once an account has been imported, since the record never lapses.
        final boolean force = isShiftKeyDown();
        status = "&7Reading " + orderId + "...&r";
        task = storeExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    StoreOrder order = DeliveryLog.order(provider.id(), orderId);
                    if (order == null && provider.capabilities().supportsOrderLookup()) {
                        order = provider.order(orderId);
                    }
                    if (order == null) {
                        status = "&cNothing on this machine matches " + orderId + ".&r";
                        return;
                    }
                    if (!order.isReady()) {
                        status = "&e" + orderId + " is not packaged yet (" + order.getStatus() + "). Try again shortly.&r";
                        return;
                    }
                    final List<StoreImportCandidate> candidates = new ArrayList<StoreImportCandidate>();
                    final int dead = collect(order, candidates, force, force);
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (candidates.isEmpty()) {
                                status = dead > 0
                                    ? "&e" + orderId + ": " + dead + " item(s) failed before and are cached as dead. "
                                        + "Hold Shift and click again to force a retry.&r"
                                    : "&7" + orderId + " has nothing left to import.&r";
                                initGui();
                            } else {
                                mc.displayGuiScreen(new GuiStoreImportSelection(
                                    GuiStore.this, "Import " + provider.displayName() + " Accounts", candidates));
                            }
                        }
                    });
                } catch (Exception error) {
                    status = "&c" + safeMessage(error) + "&r";
                }
            }
        });
    }

    // ------------------------------------------------------------------ shop extras

    private void openCustomPurchase() {
        if (provider instanceof NiceAltsProvider) {
            mc.displayGuiScreen(new GuiCustomPurchase(this, (NiceAltsProvider) provider));
        }
    }

    private void openGenerate() {
        if (provider instanceof NiceAltsProvider) {
            mc.displayGuiScreen(new GuiStoreGenerate(this, (NiceAltsProvider) provider));
        }
    }

    private void openRedeem() {
        if (provider instanceof FernanProvider) {
            mc.displayGuiScreen(new GuiStoreRedeem(this, (FernanProvider) provider));
        }
    }

    private void requestRefund() {
        if (!(provider instanceof FernanProvider) || refundableUuids.isEmpty() || isBusy()) {
            return;
        }
        final FernanProvider fernan = (FernanProvider) provider;
        final List<String> uuids = new ArrayList<String>(refundableUuids);
        final String purchaseId = refundableOrderId;
        status = "&7Requesting a refund for " + uuids.size() + " dead item(s)...&r";
        task = storeExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String refundId = fernan.requestRefund(purchaseId, uuids, "Account would not log in");
                    status = "&aRefund " + refundId + " requested for " + uuids.size() + " item(s).&r";
                    refundableUuids = Collections.emptyList();
                } catch (Exception error) {
                    status = "&c" + safeMessage(error) + "&r";
                }
            }
        });
    }

    /** Lets the account line and balance catch up after a Peso key is redeemed. */
    public void onBalanceChanged(StoreAccount updated, String message) {
        account = updated;
        status = message;
        mc.displayGuiScreen(this);
        initGui();
    }

    // ------------------------------------------------------------------ plumbing

    private StoreStatus statusSink() {
        return new StoreStatus() {
            @Override
            public void update(String message) {
                status = message;
            }
        };
    }

    private void updateButtonState() {
        boolean busy = isBusy();
        StoreProduct product = selectedProduct();
        if (connectButton != null) {
            connectButton.enabled = !busy;
        }
        if (previousProductButton != null) {
            previousProductButton.enabled = !busy && products.size() > 1;
        }
        if (nextProductButton != null) {
            nextProductButton.enabled = !busy && products.size() > 1;
        }
        if (decreaseButton != null) {
            decreaseButton.enabled = !busy && product != null && amount > 1;
        }
        if (increaseButton != null) {
            increaseButton.enabled = !busy && product != null && amount < product.maxAmount();
        }
        if (purchaseButton != null) {
            purchaseButton.enabled = !busy && product != null && canPurchase(product);
        }
        if (importPreviousButton != null) {
            // The one button that stays live while the store is busy, so a long scan can be stopped.
            importPreviousButton.enabled = scanning || !busy;
            importPreviousButton.displayString = scanning ? "Cancel Scan" : "Import Previous";
        }
        if (retryButton != null) {
            retryButton.enabled = !busy && !retryItems.isEmpty();
            retryButton.displayString = retryItems.isEmpty() ? "Retry Import" : "Retry (" + retryItems.size() + ")";
        }
        if (refundButton != null) {
            refundButton.enabled = !busy && !refundableUuids.isEmpty() && !StringUtils.isBlank(refundableOrderId);
        }
        if (importOrderButton != null) {
            importOrderButton.enabled = !busy && orderIdField != null && !StringUtils.isBlank(orderIdField.getText());
        }
        for (GuiButton button : buttonList) {
            if (button.id >= 200 && button.id < 200 + MAX_CATEGORY_BUTTONS) {
                int categoryIndex = button.id - 200;
                button.enabled = !busy && categoryIndex < categories.size() && stockFor(categories.get(categoryIndex)) > 0;
            } else if (button.id == 41 || button.id == 42 || button.id == 43
                || button.id == 15 || button.id == 47 || button.id == 2 || button.id == 16) {
                button.enabled = !busy;
            }
        }
    }

    private boolean isBusy() {
        return task != null && !task.isDone();
    }

    static String safeMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (StringUtils.isBlank(message)) {
            message = current.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ');
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
