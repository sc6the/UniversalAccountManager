package me.proxycracked.universalaccountmanager.gui;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.NameChanger;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.skin.SkinFavoritesManager;
import me.proxycracked.universalaccountmanager.skin.SkinFavoritesManager.Favorite;
import me.proxycracked.universalaccountmanager.skin.SessionSkinCache;
import me.proxycracked.universalaccountmanager.skin.SkinHeadCache;
import me.proxycracked.universalaccountmanager.skin.SkinPreview3D;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import net.minecraft.util.Session.Type;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

public class GuiChanger extends GuiScreen implements GuiYesNoCallback {
    private static final int FAVORITES_PER_PAGE = 4;
    private static final int CONFIRM_NAME_ID = 5200;
    private static final int MIN_CONTENT_HALF_WIDTH = 152;
    private static final int MAX_CONTENT_HALF_WIDTH = 200;
    private static final int COLUMN_GAP = 4;

    private final GuiScreen previousScreen;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "UniversalAccountManager-Changer");
        thread.setDaemon(true);
        return thread;
    });

    private Tab tab = Tab.SKIN;
    private GuiTextField nameField;
    private GuiButton saveNameButton;
    private GuiButton applyButton;
    private GuiButton defaultButton;
    private List<Favorite> favorites = Collections.emptyList();
    private int selectedIndex = -1;
    private int page;
    private String importVariant = "classic";
    private volatile boolean busy;
    private volatile String status = "";
    private boolean openingConfirmation;
    private String pendingName = "";

    public GuiChanger(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int centerX = width / 2;
        buttonList.add(new GuiButton(0, centerX - 114, 16, 110, 20, tab == Tab.SKIN ? "[Skinchanger]" : "Skinchanger"));
        buttonList.add(new GuiButton(1, centerX + 4, 16, 110, 20, tab == Tab.NAME ? "[Namechanger]" : "Namechanger"));
        buttonList.add(new GuiButton(2, centerX - 48, height - 24, 96, 20, "Back"));
        if (tab == Tab.SKIN) initSkinControls();
        else initNameControls();
    }

    private void initSkinControls() {
        int centerX = width / 2;
        int top = panelTop();
        int halfWidth = contentHalfWidth();
        int columnWidth = halfWidth - COLUMN_GAP;
        int leftX = centerX - halfWidth;
        int rightX = centerX + COLUMN_GAP;
        String selectedId = selectedFavorite() == null ? null : selectedFavorite().getId();
        favorites = SkinFavoritesManager.getFavorites();
        if (selectedId != null) selectedIndex = indexOf(selectedId);
        if (selectedIndex >= favorites.size()) selectedIndex = favorites.isEmpty() ? -1 : favorites.size() - 1;
        int pages = pageCount();
        page = Math.max(0, Math.min(page, pages - 1));

        int start = page * FAVORITES_PER_PAGE;
        int end = Math.min(favorites.size(), start + FAVORITES_PER_PAGE);
        for (int index = start; index < end; index++) {
            buttonList.add(new GuiButton(100 + index - start, rightX, top + 49 + (index - start) * 26,
                columnWidth, 20, favoriteLabel(index, columnWidth - 10)));
        }
        int pageButtonWidth = (columnWidth - COLUMN_GAP) / 2;
        GuiButton previous = new GuiButton(20, rightX, top + 157, pageButtonWidth, 20, "< Previous");
        GuiButton next = new GuiButton(21, rightX + pageButtonWidth + COLUMN_GAP, top + 157,
            columnWidth - pageButtonWidth - COLUMN_GAP, 20, "Next >");
        previous.enabled = page > 0;
        next.enabled = page + 1 < pages;
        buttonList.add(previous);
        buttonList.add(next);

        buttonList.add(new GuiButton(10, leftX, height - 48, columnWidth, 20, "Import Skin PNG"));
        buttonList.add(applyButton = new GuiButton(12, rightX, height - 48, columnWidth, 20, "Apply to Active Account"));
        buttonList.add(new GuiButton(11, leftX, height - 24, 96, 20, modelLabel()));
        buttonList.add(defaultButton = new GuiButton(13, centerX + halfWidth - 96, height - 24, 96, 20, "Set Default"));
        updateButtons();
    }

    private void initNameControls() {
        int centerX = width / 2;
        int halfWidth = contentHalfWidth();
        int fieldY = panelTop() + 69;
        nameField = new GuiTextField(0, fontRendererObj, centerX - halfWidth, fieldY, halfWidth * 2, 20);
        nameField.setMaxStringLength(16);
        nameField.setText("");
        nameField.setFocused(true);
        buttonList.add(saveNameButton = new GuiButton(30, centerX - 48, fieldY + 26, 96, 20, "Save"));
        updateButtons();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (!openingConfirmation) executor.shutdownNow();
    }

    @Override
    public void updateScreen() {
        if (nameField != null) nameField.updateCursorCounter();
        updateButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Changer", width / 2, 4, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "|", width / 2, 22, 0x777777);
        if (tab == Tab.SKIN) drawSkinScreen();
        else drawNameScreen();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSkinScreen() {
        int centerX = width / 2;
        int top = panelTop();
        int halfWidth = contentHalfWidth();
        int columnWidth = halfWidth - COLUMN_GAP;
        int previewX = centerX - COLUMN_GAP - columnWidth / 2;
        int favoritesX = centerX + COLUMN_GAP + columnWidth / 2;
        Gui.drawRect(centerX - halfWidth, top + 44, centerX - COLUMN_GAP, top + 181, 0x50000000);
        drawCenteredString(fontRendererObj, "Preview", previewX, top + 50, 0xAAAAAA);
        Favorite favorite = selectedFavorite();
        if (favorite == null) {
            drawCenteredString(fontRendererObj, "No favorite selected", previewX, top + 98, 0x777777);
        } else {
            ResourceLocation texture = SkinFavoritesManager.getTexture(favorite);
            if (texture == null) {
                drawCenteredString(fontRendererObj, "Preview unavailable", previewX, top + 98, 0xFF7777);
            } else {
                SkinPreview3D.draw(previewX, top + 171, 48, 0.0F, 0.0F, texture, "slim".equals(favorite.getVariant()));
            }
        }

        drawCenteredString(fontRendererObj, "Favorites (" + favorites.size() + ")", favoritesX, top + 38, 0xAAAAAA);
        if (favorites.isEmpty()) drawCenteredString(fontRendererObj, "Import a PNG to add one", favoritesX, top + 89, 0x777777);
        drawStatus(top + 186);
    }

    private void drawNameScreen() {
        int centerX = width / 2;
        int top = panelTop();
        drawString(fontRendererObj, "Minecraft name:", centerX - contentHalfWidth(), top + 56, 0xAAAAAA);
        nameField.drawTextBox();
        drawStatus(top + 123);
    }

    private int panelTop() {
        return Math.max(7, (height - 224) / 2);
    }

    private int contentHalfWidth() {
        return Math.max(MIN_CONTENT_HALF_WIDTH, Math.min(MAX_CONTENT_HALF_WIDTH, width / 2 - 8));
    }

    private void drawStatus(int y) {
        String formatted = TextFormatting.translate(status == null ? "" : status);
        drawCenteredString(fontRendererObj, trim(formatted, Math.max(120, width - 20)), width / 2, y, 0xFFFFFF);
    }

    private String trim(String value, int maxWidth) {
        return fontRendererObj.trimStringToWidth(value == null ? "" : value, maxWidth);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (nameField != null) nameField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == 1) mc.displayGuiScreen(previousScreen);
        else if (keyCode == 28 && tab == Tab.NAME && saveNameButton != null) actionPerformed(saveNameButton);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception ignored) {
        }
        if (nameField != null) nameField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) return;
        if (button.id >= 100 && button.id < 100 + FAVORITES_PER_PAGE) {
            int index = page * FAVORITES_PER_PAGE + button.id - 100;
            if (index < favorites.size()) {
                selectedIndex = index;
                initGui();
            }
            return;
        }
        switch (button.id) {
            case 0:
                tab = Tab.SKIN;
                status = "";
                initGui();
                break;
            case 1:
                tab = Tab.NAME;
                status = "";
                initGui();
                break;
            case 2:
                mc.displayGuiScreen(previousScreen);
                break;
            case 10:
                importSkin();
                break;
            case 11:
                importVariant = "slim".equals(importVariant) ? "classic" : "slim";
                button.displayString = modelLabel();
                break;
            case 12:
                applyFavorite();
                break;
            case 13:
                setDefault();
                break;
            case 20:
                page--;
                initGui();
                break;
            case 21:
                page++;
                initGui();
                break;
            case 30:
                confirmNameChange();
                break;
            default:
                break;
        }
    }

    private void importSkin() {
        status = "&7Opening skin picker...&r";
        Thread picker = new Thread(() -> {
            try {
                FileDialog dialog = new FileDialog((Frame) null, "Import a Minecraft skin", FileDialog.LOAD);
                dialog.setFile("*.png");
                dialog.setVisible(true);
                if (dialog.getDirectory() == null || dialog.getFile() == null) {
                    status = "&7Import cancelled.&r";
                    return;
                }
                File file = new File(dialog.getDirectory(), dialog.getFile());
                Favorite favorite = SkinFavoritesManager.addFavorite(Files.readAllBytes(file.toPath()), file.getName(), importVariant);
                mc.addScheduledTask(() -> {
                    favorites = SkinFavoritesManager.getFavorites();
                    selectedIndex = indexOf(favorite.getId());
                    page = Math.max(0, selectedIndex / FAVORITES_PER_PAGE);
                    status = "&aSaved " + favorite.getName() + " as a favorite.&r";
                    initGui();
                });
            } catch (Exception error) {
                status = "&cImport failed: " + safeMessage(error) + "&r";
            }
        }, "UniversalAccountManager-SkinPicker");
        picker.setDaemon(true);
        picker.start();
    }

    private void applyFavorite() {
        Favorite favorite = selectedFavorite();
        if (favorite == null || busy) return;
        String token = mc.getSession().getToken();
        if (StringUtils.isBlank(token)) {
            status = "&cLog in to a Microsoft account first.&r";
            return;
        }
        busy = true;
        status = "&7Applying " + favorite.getName() + "...&r";
        executor.submit(() -> {
            try {
                int code = SkinFavoritesManager.applyFavorite(favorite, token);
                status = skinResult(code);
            } catch (Exception error) {
                status = "&cSkin change failed: " + safeMessage(error) + "&r";
            } finally {
                busy = false;
            }
        });
    }

    private void setDefault() {
        Favorite favorite = selectedFavorite();
        if (favorite == null) return;
        try {
            SkinFavoritesManager.setDefault(favorite.getId());
            status = "&a" + favorite.getName() + " will be used for newly added accounts.&r";
            initGui();
        } catch (Exception error) {
            status = "&cCould not save default: " + safeMessage(error) + "&r";
        }
    }

    private void confirmNameChange() {
        if (busy || nameField == null) return;
        String name = nameField.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            status = "&cNames must be 3-16 letters, numbers, or underscores.&r";
            return;
        }
        pendingName = name;
        openingConfirmation = true;
        mc.displayGuiScreen(new GuiYesNo(
            this,
            "Change your Minecraft username to " + name + "?",
            "This will update the active account's username.",
            "Save",
            "Cancel",
            CONFIRM_NAME_ID
        ));
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        openingConfirmation = false;
        mc.displayGuiScreen(this);
        if (id == CONFIRM_NAME_ID && result) saveName(pendingName);
    }

    private void saveName(String name) {
        if (busy || StringUtils.isBlank(name)) return;
        String token = mc.getSession().getToken();
        if (StringUtils.isBlank(token)) {
            status = "&cLog in to a Microsoft account before changing names.&r";
            return;
        }
        busy = true;
        status = "&7Saving username " + name + "...&r";
        executor.submit(() -> {
            try {
                int code = NameChanger.changeName(name, token);
                if (code == 200) {
                    applyNameToSession(name, token);
                    status = "&aUsername changed to " + name + ".&r";
                } else if (code == 400) status = "&cThat username is invalid.&r";
                else if (code == 401) status = "&cSession expired. Log in again.&r";
                else if (code == 403) status = "&cThat name is unavailable or the account is on cooldown.&r";
                else if (code == 429) status = "&cToo many requests. Try again later.&r";
                else status = "&cMinecraft returned HTTP " + code + ".&r";
            } catch (Exception error) {
                status = "&cUsername change failed: " + safeMessage(error) + "&r";
            } finally {
                busy = false;
            }
        });
    }

    private void applyNameToSession(String newName, String token) {
        Session current = SessionManager.get();
        SessionManager.set(new Session(newName, current.getPlayerID(), current.getToken(), Type.MOJANG.toString()));
        for (Account account : UniversalAccountManager.accounts) {
            if (!token.equals(account.getAccessToken())) continue;
            String oldName = account.getUsername();
            account.setUsername(newName);
            String key = StringUtils.isBlank(account.getUuid()) ? oldName : account.getUuid();
            if (!StringUtils.isBlank(key)) SkinHeadCache.invalidate(key);
            SessionSkinCache.invalidate(oldName, account.getUuid());
            SessionSkinCache.invalidate(newName, account.getUuid());
        }
        UniversalAccountManager.save();
    }

    private void updateButtons() {
        Favorite selected = selectedFavorite();
        if (saveNameButton != null) saveNameButton.enabled = !busy && nameField != null && !nameField.getText().trim().isEmpty();
        if (applyButton != null) applyButton.enabled = !busy && selected != null;
        if (defaultButton != null) {
            boolean isDefault = selected != null && selected.getId().equals(SkinFavoritesManager.getDefaultId());
            defaultButton.enabled = !busy && selected != null && !isDefault;
            defaultButton.displayString = isDefault ? "Default Skin" : "Set Default";
        }
    }

    private Favorite selectedFavorite() {
        return selectedIndex >= 0 && selectedIndex < favorites.size() ? favorites.get(selectedIndex) : null;
    }

    private int indexOf(String id) {
        for (int i = 0; i < favorites.size(); i++) if (favorites.get(i).getId().equals(id)) return i;
        return -1;
    }

    private String favoriteLabel(int index, int maxWidth) {
        Favorite favorite = favorites.get(index);
        String prefix = index == selectedIndex ? "> " : "";
        String suffix = favorite.getId().equals(SkinFavoritesManager.getDefaultId()) ? " *" : "";
        return trim(prefix + favorite.getName() + suffix, maxWidth);
    }

    private String modelLabel() {
        return "slim".equals(importVariant) ? "Model: Slim" : "Model: Classic";
    }

    private int pageCount() {
        return Math.max(1, (favorites.size() + FAVORITES_PER_PAGE - 1) / FAVORITES_PER_PAGE);
    }

    private static String skinResult(int code) {
        if (code == 200) return "&aSkin applied to the active account.&r";
        if (code == 401) return "&cSession expired. Log in to the account again.&r";
        if (code == 429) return "&cToo many requests. Try again later.&r";
        return "&cMinecraft returned HTTP " + code + ".&r";
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return StringUtils.isBlank(message) ? "Unknown error" : message.replace('\n', ' ').replace('\r', ' ');
    }

    private enum Tab {
        SKIN,
        NAME
    }
}
