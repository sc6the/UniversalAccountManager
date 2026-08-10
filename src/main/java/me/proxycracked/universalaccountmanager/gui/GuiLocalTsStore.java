package me.proxycracked.universalaccountmanager.gui;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient.Order;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient.OrderPage;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient.OrderItem;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient.OrderSummary;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient.Product;
import me.proxycracked.universalaccountmanager.localts.LocalTsClient.User;
import me.proxycracked.universalaccountmanager.localts.LocalTsCredentialStore;
import me.proxycracked.universalaccountmanager.localts.LocalTsImportTracker;
import me.proxycracked.universalaccountmanager.localts.PurchasedAccountImporter;
import me.proxycracked.universalaccountmanager.localts.PurchasedAccountImporter.ImportedAccount;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.util.Session;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

public class GuiLocalTsStore extends GuiScreen implements GuiYesNoCallback {
    private static final DecimalFormat CREDITS_FORMAT = new DecimalFormat("0.##");
    private static final int PURCHASE_CONFIRM_ID = 4100;

    private GuiScreen previousScreen;
    private final ExecutorService storeExecutor = Executors.newSingleThreadExecutor(runnable -> daemonThread(runnable, "Localts-Store"));
    private final ExecutorService authExecutor = Executors.newFixedThreadPool(2, runnable -> daemonThread(runnable, "Localts-Auth"));

    private GuiTextField apiKeyField;
    private GuiButton connectButton;
    private GuiButton previousProductButton;
    private GuiButton nextProductButton;
    private GuiButton decreaseButton;
    private GuiButton increaseButton;
    private GuiButton purchaseButton;
    private GuiButton backButton;
    private GuiButton refreshUnbannedButton;
    private GuiButton refreshBannedButton;
    private GuiButton cookieUnbannedButton;
    private GuiButton cookieBannedButton;
    private GuiButton showAllButton;
    private GuiButton importButton;

    private String apiKey = "";
    private volatile String status = "&7Log in to Localts, create an API key in Settings, then paste it here.&r";
    private User user;
    private List<Product> allProducts = Collections.emptyList();
    private List<Product> products = Collections.emptyList();
    private int productIndex;
    private int amount = 1;
    private Future<?> task;
    private boolean savedApiKeyLoaded;
    private boolean savedApiKeyAttempted;
    private boolean productListOpen;
    private ProductGroup activeGroup = ProductGroup.ALL;
    private String lastOrderId = "";

    public GuiLocalTsStore(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    void setPreviousScreen(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    private static Thread daemonThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        if (user == null) {
            loadSavedApiKey();
            initLoginControls();
            if (!savedApiKeyAttempted && !StringUtils.isBlank(apiKey)) {
                savedApiKeyAttempted = true;
                connect();
            }
        } else if (!productListOpen) {
            initCategoryControls();
        } else {
            initStoreControls();
        }
    }

    private void initLoginControls() {
        int centerX = width / 2;
        int centerY = height / 2;
        apiKeyField = new GuiTextField(0, fontRendererObj, centerX - 150, centerY - 14, 300, 20);
        apiKeyField.setMaxStringLength(512);
        apiKeyField.setText("");
        apiKeyField.setFocused(StringUtils.isBlank(apiKey));
        buttonList.add(connectButton = new GuiButton(0, centerX - 150, centerY + 14, 146, 20, "Connect"));
        buttonList.add(backButton = new GuiButton(2, centerX - 73, height - 24, 146, 20, "Back"));
    }

    private void initCategoryControls() {
        int centerX = width / 2;
        int top = categoryTop();
        buttonList.add(refreshUnbannedButton = new GuiButton(20, centerX - 152, top, 148, 20, "Refresh Token Unbanned"));
        buttonList.add(refreshBannedButton = new GuiButton(21, centerX + 4, top, 148, 20, "Refresh Token Banned"));
        buttonList.add(cookieUnbannedButton = new GuiButton(22, centerX - 152, top + 54, 148, 20, "Cookie Unbanned"));
        buttonList.add(cookieBannedButton = new GuiButton(23, centerX + 4, top + 54, 148, 20, "Cookie Banned"));
        buttonList.add(showAllButton = new GuiButton(24, centerX - 152, top + 90, 304, 20, "Show All Available"));
        buttonList.add(importButton = new GuiButton(27, centerX - 152, top + 114, 304, 20, "Import Previous Purchases"));
        buttonList.add(new GuiButton(25, centerX - 152, height - 24, 148, 20, "Refresh Stock"));
        buttonList.add(backButton = new GuiButton(26, centerX + 4, height - 24, 148, 20, "Back"));
        updateButtonState();
    }

    private void initStoreControls() {
        int centerX = width / 2;
        int centerY = height / 2;
        buttonList.add(previousProductButton = new GuiButton(10, centerX - 150, centerY - 12, 45, 20, "<"));
        buttonList.add(nextProductButton = new GuiButton(11, centerX + 105, centerY - 12, 45, 20, ">"));
        buttonList.add(decreaseButton = new GuiButton(12, centerX - 150, centerY + 16, 45, 20, "-"));
        buttonList.add(increaseButton = new GuiButton(13, centerX - 100, centerY + 16, 45, 20, "+"));
        buttonList.add(purchaseButton = new GuiButton(14, centerX - 50, centerY + 16, 200, 20, "Review Purchase"));
        buttonList.add(new GuiButton(15, centerX - 150, height - 24, 146, 20, "Refresh Store"));
        buttonList.add(backButton = new GuiButton(16, centerX + 4, height - 24, 146, 20, "Categories"));
        updateButtonState();
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
        updateButtonState();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Localts Store", width / 2, 4, 0xFFFFFF);
        if (user == null) {
            drawLoginScreen();
        } else if (!productListOpen) {
            drawCategoryScreen();
        } else {
            drawStoreScreen();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawLoginScreen() {
        int centerX = width / 2;
        int centerY = height / 2;
        String keyLabel = StringUtils.isBlank(apiKey)
            ? "Localts API key:"
            : "Localts API key (saved securely for this Windows user):";
        drawString(fontRendererObj, keyLabel, centerX - 150, centerY - 28, 0xAAAAAA);
        apiKeyField.drawTextBox();
        drawStatus(centerY + 44);
    }

    private void drawCategoryScreen() {
        int centerX = width / 2;
        int top = categoryTop();
        drawCenteredString(fontRendererObj, user.getUsername() + "  |  " + credits(user.getBalance()) + " credits", centerX, 20, 0xA0FFA0);
        drawStock(centerX - 78, top + 24, stockFor(ProductGroup.REFRESH_UNBANNED));
        drawStock(centerX + 78, top + 24, stockFor(ProductGroup.REFRESH_BANNED));
        drawStock(centerX - 78, top + 78, stockFor(ProductGroup.COOKIE_UNBANNED));
        drawStock(centerX + 78, top + 78, stockFor(ProductGroup.COOKIE_BANNED));
        drawStatus(height - 38);
    }

    private int categoryTop() {
        return Math.max(40, height / 2 - 70);
    }

    private void drawStock(int x, int y, int stock) {
        drawCenteredString(fontRendererObj, stock + " in Stock", x, y, stockColor(stock));
    }

    private static int stockColor(int stock) {
        if (stock < 30) return 0xFF5555;
        if (stock < 100) return 0xFFFF55;
        return 0x55FF55;
    }

    private void drawStoreScreen() {
        int centerX = width / 2;
        int centerY = height / 2;
        drawCenteredString(fontRendererObj, user.getUsername() + "  |  Balance: " + credits(user.getBalance()) + " credits", centerX, 20, 0xA0FFA0);
        Product product = selectedProduct();
        if (product == null) {
            drawCenteredString(fontRendererObj, "No refresh-token products are currently available.", centerX, centerY - 38, 0xFF7777);
        } else {
            String position = (productIndex + 1) + " / " + products.size();
            drawCenteredString(fontRendererObj, position + "  " + trim(product.getName(), 290), centerX, centerY - 48, 0xFFFFFF);
            drawCenteredString(fontRendererObj, trim(product.getDescription(), 290), centerX, centerY - 34, 0xAAAAAA);
            drawCenteredString(fontRendererObj,
                "Stock: " + product.getStock() + "  |  Unit: " + credits(product.getPriceInCredits()) + "  |  Quantity: " + amount,
                centerX, centerY - 20, 0xDDDDDD);
            double discount = product.discountFor(amount);
            String price = "Total: " + credits(product.totalFor(amount)) + " credits";
            if (discount > 0.0D) {
                price += " (" + credits(discount) + "% discount)";
            }
            drawCenteredString(fontRendererObj, price, centerX, centerY + 44, 0xFFFF80);
        }
        if (!StringUtils.isBlank(lastOrderId)) {
            drawCenteredString(fontRendererObj, "Last order: " + lastOrderId, centerX, centerY + 58, 0x888888);
        }
        drawStatus(height - 38);
    }

    private void drawStatus(int y) {
        if (!StringUtils.isBlank(status)) {
            String formatted = TextFormatting.translate(status);
            drawCenteredString(fontRendererObj, trim(formatted, Math.max(120, width - 20)), width / 2, y, 0xFFFFFF);
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

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (apiKeyField != null) {
            apiKeyField.textboxKeyTyped(typedChar, keyCode);
        }
        if (keyCode == 1 && backButton != null) {
            actionPerformed(backButton);
        } else if (keyCode == 28 && user == null && connectButton != null) {
            actionPerformed(connectButton);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception ignored) {
        }
        if (apiKeyField != null) {
            apiKeyField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        switch (button.id) {
            case 0:
                connect();
                break;
            case 2:
            case 26:
                mc.displayGuiScreen(previousScreen);
                break;
            case 16:
                productListOpen = false;
                status = "&7Choose an account type.&r";
                initGui();
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
                Product product = selectedProduct();
                amount = Math.min(product == null ? 1 : product.getStock(), amount + 1);
                break;
            case 14:
                showPurchaseConfirmation();
                break;
            case 15:
            case 25:
                refreshStore();
                break;
            case 20:
                openGroup(ProductGroup.REFRESH_UNBANNED);
                break;
            case 21:
                openGroup(ProductGroup.REFRESH_BANNED);
                break;
            case 22:
                openGroup(ProductGroup.COOKIE_UNBANNED);
                break;
            case 23:
                openGroup(ProductGroup.COOKIE_BANNED);
                break;
            case 24:
                openGroup(ProductGroup.ALL);
                break;
            case 27:
                importPreviousPurchases();
                break;
            default:
                break;
        }
        updateButtonState();
    }

    private void loadSavedApiKey() {
        if (savedApiKeyLoaded) return;
        savedApiKeyLoaded = true;
        try {
            apiKey = LocalTsCredentialStore.load().trim();
            if (!StringUtils.isBlank(apiKey)) {
                status = "&7Saved API key found. Connecting to Localts...&r";
            }
        } catch (Exception error) {
            apiKey = "";
            status = "&cCould not load the saved API key: " + safeMessage(error) + "&r";
        }
    }

    private void connect() {
        String enteredApiKey = apiKeyField == null ? "" : apiKeyField.getText().trim();
        if (!StringUtils.isBlank(enteredApiKey)) apiKey = enteredApiKey;
        if (StringUtils.isBlank(apiKey)) {
            status = "&cAPI key is empty.&r";
            return;
        }
        final String connectingApiKey = apiKey;
        status = "&7Connecting to Localts...&r";
        task = storeExecutor.submit(() -> {
            try {
                User fetchedUser = LocalTsClient.getMe(connectingApiKey);
                List<Product> fetchedProducts = supportedProducts(LocalTsClient.getProducts());
                String saveWarning = null;
                try {
                    LocalTsCredentialStore.save(connectingApiKey);
                } catch (Exception saveError) {
                    saveWarning = safeMessage(saveError);
                }
                final String credentialWarning = saveWarning;
                mc.addScheduledTask(() -> {
                    apiKey = connectingApiKey;
                    user = fetchedUser;
                    allProducts = fetchedProducts;
                    products = Collections.emptyList();
                    productIndex = 0;
                    amount = 1;
                    productListOpen = false;
                    if (credentialWarning != null) {
                        status = "&eConnected, but the API key could not be saved: " + credentialWarning + "&r";
                    } else {
                        status = allProducts.isEmpty()
                            ? "&cNo supported accounts are in stock.&r"
                            : "&aConnected. API key saved securely.&r";
                    }
                    initGui();
                });
            } catch (Exception error) {
                status = "&c" + safeMessage(error) + "&r";
            }
        });
    }

    private void refreshStore() {
        if (isBusy()) {
            return;
        }
        status = "&7Refreshing balance and products...&r";
        task = storeExecutor.submit(() -> {
            try {
                User fetchedUser = LocalTsClient.getMe(apiKey);
                List<Product> fetchedProducts = supportedProducts(LocalTsClient.getProducts());
                mc.addScheduledTask(() -> {
                    user = fetchedUser;
                    allProducts = fetchedProducts;
                    products = filterProducts(activeGroup);
                    selectProduct(Math.min(productIndex, Math.max(0, products.size() - 1)));
                    status = "&aStore refreshed.&r";
                    initGui();
                });
            } catch (Exception error) {
                status = "&c" + safeMessage(error) + "&r";
            }
        });
    }

    private static List<Product> supportedProducts(List<Product> allProducts) {
        List<Product> filtered = new ArrayList<>();
        for (Product product : allProducts) {
            if (product.isSupportedAccountProduct()) {
                filtered.add(product);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    private void openGroup(ProductGroup group) {
        activeGroup = group;
        products = filterProducts(group);
        productIndex = 0;
        amount = 1;
        productListOpen = true;
        status = products.isEmpty() ? "&cNo matching accounts are in stock.&r" : "&7Select a product and quantity.&r";
        initGui();
    }

    private List<Product> filterProducts(ProductGroup group) {
        List<Product> filtered = new ArrayList<>();
        for (Product product : allProducts) {
            if (product.getStock() > 0 && (group == ProductGroup.ALL || matchesGroup(product, group))) filtered.add(product);
        }
        return Collections.unmodifiableList(filtered);
    }

    private void importPreviousPurchases() {
        if (isBusy()) return;
        status = "&7Scanning Localts order history...&r";
        task = storeExecutor.submit(() -> {
            try {
                List<OrderSummary> summaries = loadAllOrders();
                List<ImportCandidate> candidates = new ArrayList<>();
                for (OrderSummary summary : summaries) {
                    Product product = findProduct(summary.getProductId());
                    Order order = LocalTsClient.getOrder(apiKey, summary.getId());
                    if (!order.isPackaged()) continue;
                    int itemNumber = 0;
                    for (OrderItem item : order.getItems()) {
                        itemNumber++;
                        if (LocalTsImportTracker.isImported(item.getId())) continue;
                        PurchasedKind kind = purchasedKind(product, item.getContent());
                        if (kind == PurchasedKind.UNSUPPORTED) continue;
                        String productName = StringUtils.isBlank(order.getProductName())
                            ? (product == null ? "Localts Account" : product.getName())
                            : order.getProductName();
                        candidates.add(new ImportCandidate(
                            item.getId(), item.getContent(), kind, productName + " #" + itemNumber, summary.getId()
                        ));
                    }
                }
                mc.addScheduledTask(() -> {
                    if (candidates.isEmpty()) {
                        status = "&7No new supported Localts purchases found.&r";
                        initGui();
                    } else {
                        mc.displayGuiScreen(new GuiLocalTsImportSelection(this, candidates));
                    }
                });
            } catch (Exception error) {
                status = "&c" + safeMessage(error) + "&r";
            }
        });
    }

    void importSelectedPurchases(List<ImportCandidate> candidates) {
        if (candidates == null || candidates.isEmpty() || isBusy()) return;
        status = "&7Importing 0/" + candidates.size() + " selected account(s)...&r";
        task = storeExecutor.submit(() -> {
            List<ImportedAccount> imported = new ArrayList<>();
            List<String> importedItemIds = new ArrayList<>();
            int failed = 0;
            int current = 0;
            for (ImportCandidate candidate : candidates) {
                current++;
                status = "&7Importing " + current + "/" + candidates.size() + " selected account(s)...&r";
                try {
                    ImportedAccount account = candidate.kind == PurchasedKind.COOKIE
                        ? PurchasedAccountImporter.importCookie(candidate.content, authExecutor).get(120, TimeUnit.SECONDS)
                        : PurchasedAccountImporter.importRefreshToken(candidate.content, authExecutor).get(120, TimeUnit.SECONDS);
                    imported.add(account);
                    importedItemIds.add(candidate.itemId);
                } catch (Exception error) {
                    failed++;
                }
            }
            final int failedCount = failed;
            mc.addScheduledTask(() -> finishHistoricalImport(imported, importedItemIds, failedCount));
        });
    }

    private List<OrderSummary> loadAllOrders() throws Exception {
        List<OrderSummary> summaries = new ArrayList<>();
        int page = 0;
        int totalPages = 1;
        while (page < totalPages) {
            OrderPage result = LocalTsClient.getOrders(apiKey, page, 100);
            summaries.addAll(result.getOrders());
            totalPages = Math.max(0, result.getTotalPages());
            page++;
        }
        return summaries;
    }

    private Product findProduct(String productId) {
        for (Product product : allProducts) {
            if (product.getId().equals(productId)) return product;
        }
        return null;
    }

    private static PurchasedKind purchasedKind(Product product, String content) {
        if (product != null) {
            if (product.isCookieProduct()) return PurchasedKind.COOKIE;
            if (product.isRefreshTokenProduct()) return PurchasedKind.REFRESH_TOKEN;
        }
        int colons = 0;
        String value = content == null ? "" : content;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == ':') colons++;
        }
        if (colons == 0 && !value.trim().isEmpty()) return PurchasedKind.COOKIE;
        if (colons == 1) return PurchasedKind.REFRESH_TOKEN;
        return PurchasedKind.UNSUPPORTED;
    }

    private void finishHistoricalImport(List<ImportedAccount> imported, List<String> importedItemIds, int failed) {
        for (ImportedAccount account : imported) saveImportedAccount(account);
        if (!imported.isEmpty()) {
            UniversalAccountManager.resort();
            UniversalAccountManager.save();
            LocalTsImportTracker.markImported(importedItemIds);
        }
        if (imported.isEmpty() && failed == 0) {
            status = "&7No new supported Localts purchases found.&r";
        } else if (failed == 0) {
            status = "&aImported " + imported.size() + " previous purchase(s).&r";
        } else {
            status = "&eImported " + imported.size() + "; " + failed + " failed and can be retried.&r";
        }
        initGui();
    }

    public static final class ImportCandidate {
        private final String itemId;
        private final String content;
        private final PurchasedKind kind;
        private final String label;
        private final String orderId;

        private ImportCandidate(String itemId, String content, PurchasedKind kind, String label, String orderId) {
            this.itemId = itemId;
            this.content = content;
            this.kind = kind;
            this.label = label;
            this.orderId = orderId;
        }

        public String getLabel() { return label; }
        public String getOrderId() { return orderId; }
    }

    private static boolean matchesGroup(Product product, ProductGroup group) {
        switch (group) {
            case REFRESH_UNBANNED: return product.isRefreshTokenProduct() && product.isUnbanned();
            case REFRESH_BANNED: return product.isRefreshTokenProduct() && !product.isUnbanned();
            case COOKIE_UNBANNED: return product.isCookieProduct() && product.isUnbanned();
            case COOKIE_BANNED: return product.isCookieProduct() && !product.isUnbanned();
            default: return true;
        }
    }

    private int stockFor(ProductGroup group) {
        int stock = 0;
        for (Product product : allProducts) {
            if (matchesGroup(product, group)) stock += product.getStock();
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
        amount = Math.max(1, Math.min(amount, selectedProduct().getStock()));
    }

    private Product selectedProduct() {
        return products.isEmpty() || productIndex < 0 || productIndex >= products.size() ? null : products.get(productIndex);
    }

    private void showPurchaseConfirmation() {
        Product product = selectedProduct();
        if (product == null || !canPurchase(product)) {
            return;
        }
        String line1 = "Buy " + amount + "x " + product.getName() + "?";
        String line2 = "This will deduct " + credits(product.totalFor(amount)) + " credits from your Localts balance.";
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
        Product product = selectedProduct();
        if (product == null || !canPurchase(product) || isBusy()) {
            return;
        }
        final int purchaseAmount = amount;
        status = "&7Submitting confirmed purchase...&r";
        task = storeExecutor.submit(() -> runPurchase(product, purchaseAmount));
    }

    private void runPurchase(Product purchasedProduct, int purchaseAmount) {
        try {
            String orderId = LocalTsClient.purchase(apiKey, purchasedProduct.getId(), purchaseAmount);
            lastOrderId = orderId;
            status = "&7Order " + orderId + " is being packaged...&r";
            Order order = waitForOrder(orderId);
            if (order.getItems().isEmpty()) {
                throw new Exception("Packaged order contained no items (order " + orderId + ")");
            }

            List<ImportedAccount> imported = new ArrayList<>();
            List<String> importedItemIds = new ArrayList<>();
            int failed = 0;
            int current = 0;
            for (OrderItem item : order.getItems()) {
                current++;
                status = "&7Importing purchased account " + current + "/" + order.getItems().size() + "...&r";
                try {
                    if (purchasedProduct.isCookieProduct()) {
                        imported.add(PurchasedAccountImporter.importCookie(item.getContent(), authExecutor).get(120, TimeUnit.SECONDS));
                    } else {
                        imported.add(PurchasedAccountImporter.importRefreshToken(item.getContent(), authExecutor).get(120, TimeUnit.SECONDS));
                    }
                    importedItemIds.add(item.getId());
                } catch (Exception error) {
                    failed++;
                }
            }

            final int failedCount = failed;
            User updatedUser = null;
            List<Product> updatedProducts = null;
            try {
                updatedUser = LocalTsClient.getMe(apiKey);
                updatedProducts = supportedProducts(LocalTsClient.getProducts());
            } catch (Exception ignored) {
            }
            final User finalUser = updatedUser;
            final List<Product> finalProducts = updatedProducts;
            mc.addScheduledTask(() -> finishImport(orderId, imported, importedItemIds, failedCount, finalUser, finalProducts));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            status = "&cPurchase monitoring was cancelled. Check Localts order history.&r";
        } catch (Exception error) {
            status = "&c" + safeMessage(error) + "&r";
        }
    }

    private Order waitForOrder(String orderId) throws Exception {
        for (int attempt = 0; attempt < 80; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            Order order = LocalTsClient.getOrder(apiKey, orderId);
            if (order.isPackaged()) {
                return order;
            }
            status = "&7Order " + orderId + ": " + order.getStatus() + "...&r";
            Thread.sleep(1500L);
        }
        throw new Exception("Order packaging timed out. Retrieve order " + orderId + " from Localts order history");
    }

    private void finishImport(String orderId, List<ImportedAccount> imported, List<String> importedItemIds, int failed, User updatedUser, List<Product> updatedProducts) {
        for (ImportedAccount importedAccount : imported) {
            saveImportedAccount(importedAccount);
        }
        if (!imported.isEmpty()) {
            UniversalAccountManager.resort();
            UniversalAccountManager.save();
            LocalTsImportTracker.markImported(importedItemIds);
        }
        if (updatedUser != null) {
            user = updatedUser;
        }
        if (updatedProducts != null) {
            allProducts = updatedProducts;
            products = filterProducts(activeGroup);
            selectProduct(Math.min(productIndex, Math.max(0, products.size() - 1)));
        }
        if (failed == 0) {
            status = "&aPurchased and imported " + imported.size() + " account(s).&r";
        } else {
            status = "&eImported " + imported.size() + "; " + failed + " failed. Retrieve order " + orderId + " to retry.&r";
        }
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
            match = new Account(imported.getType(), imported.getRefreshToken(), imported.getAccessToken(), session.getUsername(), session.getPlayerID(), 0L);
            UniversalAccountManager.accounts.add(match);
        } else {
            match.setType(imported.getType());
            match.setRefreshToken(imported.getRefreshToken());
            match.setAccessToken(imported.getAccessToken());
            match.setUsername(session.getUsername());
            match.setUuid(session.getPlayerID());
        }
    }

    private void updateButtonState() {
        boolean busy = isBusy();
        if (connectButton != null) {
            connectButton.enabled = !busy;
        }
        Product product = selectedProduct();
        if (previousProductButton != null) previousProductButton.enabled = !busy && products.size() > 1;
        if (nextProductButton != null) nextProductButton.enabled = !busy && products.size() > 1;
        if (decreaseButton != null) decreaseButton.enabled = !busy && product != null && amount > 1;
        if (increaseButton != null) increaseButton.enabled = !busy && product != null && amount < product.getStock();
        if (purchaseButton != null) purchaseButton.enabled = !busy && product != null && canPurchase(product);
        if (refreshUnbannedButton != null) refreshUnbannedButton.enabled = !busy && stockFor(ProductGroup.REFRESH_UNBANNED) > 0;
        if (refreshBannedButton != null) refreshBannedButton.enabled = !busy && stockFor(ProductGroup.REFRESH_BANNED) > 0;
        if (cookieUnbannedButton != null) cookieUnbannedButton.enabled = !busy && stockFor(ProductGroup.COOKIE_UNBANNED) > 0;
        if (cookieBannedButton != null) cookieBannedButton.enabled = !busy && stockFor(ProductGroup.COOKIE_BANNED) > 0;
        if (showAllButton != null) showAllButton.enabled = !busy && hasAvailableProducts();
        if (importButton != null) importButton.enabled = !busy;
        if (backButton != null) backButton.enabled = !busy;
    }

    private boolean canPurchase(Product product) {
        return amount > 0 && amount <= product.getStock() && user != null && product.totalFor(amount) <= user.getBalance();
    }

    private boolean hasAvailableProducts() {
        for (Product product : allProducts) {
            if (product.getStock() > 0) return true;
        }
        return false;
    }

    private boolean isBusy() {
        return task != null && !task.isDone();
    }

    private static String safeMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (StringUtils.isBlank(message)) {
            message = current.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private enum ProductGroup {
        REFRESH_UNBANNED,
        REFRESH_BANNED,
        COOKIE_UNBANNED,
        COOKIE_BANNED,
        ALL
    }

    private enum PurchasedKind {
        REFRESH_TOKEN,
        COOKIE,
        UNSUPPORTED
    }
}
