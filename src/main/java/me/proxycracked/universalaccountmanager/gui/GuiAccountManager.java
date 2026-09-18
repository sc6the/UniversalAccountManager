package me.proxycracked.universalaccountmanager.gui;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.*;
import me.proxycracked.universalaccountmanager.gui.theme.*;
import me.proxycracked.universalaccountmanager.hypixel.HypixelBanCheck;
import me.proxycracked.universalaccountmanager.skin.SkinHeadCache;
import me.proxycracked.universalaccountmanager.utils.Notification;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** One layout and controller for both editions. Only UiTheme changes their appearance. */
public class GuiAccountManager extends GuiScreen {
    private static final int ROW = 28;
    private final GuiScreen previousScreen;
    // Kept compatible with the existing unified LoginController.
    private volatile Notification notification;
    private int selectedAccount = -1;
    private ExecutorService executor;
    private CompletableFuture<?> task;
    private Account selected, pendingDelete, deleted;
    private int deletedIndex;
    private GuiTextField search;
    private String query = "";
    private boolean pinnedOnly, alphabetical;
    private final List<Account> visible = new ArrayList<>();
    private int left, contentWidth, top, bottom, first, rows;
    private long lastClick;
    private Account lastClicked;
    private final List<GuiButton> contextButtons = new ArrayList<>();

    public GuiAccountManager(GuiScreen previousScreen) { this(previousScreen, null); }
    public GuiAccountManager(GuiScreen previousScreen, Notification notification) {
        this.previousScreen = previousScreen;
        this.notification = notification;
    }

    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true);
        contentWidth = Math.min(540, width - 20);
        left = (width - contentWidth) / 2;
        top = 84; bottom = height - 54;
        rows = Math.max(1, (bottom - top) / ROW);
        bottom = top + rows * ROW;
        buttonList.clear();
        contextButtons.clear();
        add(5, left, 37, 76, "Changer");
        add(11, left + 80, 37, 86, "Buy Accounts");
        if (UiTheme.get().modern) add(12, left + contentWidth - 88, 37, 88, "Appearance");
        search = new GuiTextField(0, fontRendererObj, left + 5, 61, contentWidth - 159, 20) {
            @Override public void drawTextBox() {
                // Borderless text fields draw at yPosition, without vanilla's vertical padding.
                // Keep the full-height click target while centering text, selection and cursor.
                int originalY = yPosition;
                yPosition += (height - 8) / 2;
                try { super.drawTextBox(); } finally { yPosition = originalY; }
            }
        };
        search.setMaxStringLength(80); search.setText(query);
        search.setTextColor(UiTheme.get().text); search.setEnableBackgroundDrawing(false);
        add(13, left + contentWidth - 148, 61, 66, pinnedOnly ? "Pinned" : "All");
        add(14, left + contentWidth - 78, 61, 78, alphabetical ? "Sort: A-Z" : "Sort: saved");
        add(0, left, height - 27, 88, "Log in");
        add(1, left + 92, height - 27, 96, "+ Add Account");
        add(3, left + contentWidth - 60, height - 27, 60, "Done");
        add(15, left + contentWidth - 60, height - 50, 60, "Undo");
        rebuild();
        ExpiredAccountCleaner.schedule();
    }

    private void add(int id, int x, int y, int w, String label) {
        buttonList.add(new GuiButton(id, x, y, w, 20, label));
    }

    private boolean busy() { return task != null && !task.isDone(); }
    private void rebuild() {
        visible.clear();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (Account a : UniversalAccountManager.accounts) {
            if ((!pinnedOnly || a.isPinned()) && (name(a).toLowerCase(Locale.ROOT).contains(needle)
                || (a.getType() != null && a.getType().toLowerCase(Locale.ROOT).contains(needle)))) visible.add(a);
        }
        if (alphabetical) Collections.sort(visible, Comparator.comparing(GuiAccountManager::name, String.CASE_INSENSITIVE_ORDER));
        if (selected != null && !visible.contains(selected)) selected = null;
        selectedAccount = UniversalAccountManager.accounts.indexOf(selected);
        first = Math.max(0, Math.min(first, Math.max(0, visible.size() - rows)));
        for (GuiButton b : buttonList) {
            if (b.id == 0 || b.id == 2 || b.id == 4 || b.id == 7) b.enabled = selected != null && !busy();
            if (b.id == 1 || b.id == 5 || b.id == 11) b.enabled = !busy();
            if (b.id == 4) b.displayString = selected != null && selected.isPinned() ? "Unpin" : "Pin";
            if (b.id == 15) { b.visible = deleted != null; b.enabled = !busy(); }
            if (b.id == 9) { b.visible = activeLauncher() != null; b.enabled = !busy(); }
            if (b.id == 0) b.displayString = busy() ? "Logging in..." : "Log in";
        }
    }

    @Override public void updateScreen() { search.updateCursorCounter(); rebuild(); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ThemeSettings t = UiTheme.get();
        UiTheme.background(width, height);
        fontRendererObj.drawString("Accounts", left, 13, t.text);
        String count = visible.size() + " / " + UniversalAccountManager.accounts.size();
        fontRendererObj.drawString(count, left + contentWidth - fontRendererObj.getStringWidth(count), 13, t.muted);
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth("Playing as " + mc.getSession().getUsername(), contentWidth), left, 25, t.muted);
        UiTheme.panel(left, 61, contentWidth - 154, 20, t.surface);
        search.drawTextBox();
        if (query.isEmpty() && !search.isFocused()) fontRendererObj.drawString("Search name or type...  Ctrl+F", left + 5, 67, t.muted);
        UiTheme.panel(left, top, contentWidth, bottom - top, UiTheme.blend(t.background, t.surface, .45f));
        for (int i = first; i < Math.min(visible.size(), first + rows); i++) {
            Account a = visible.get(i);
            int y = top + (i - first) * ROW;
            boolean hover = mouseX >= left && mouseX < left + contentWidth && mouseY >= y && mouseY < y + ROW;
            int fill = a == selected ? UiTheme.blend(t.surface, t.accent, .20f) : hover ? t.surface : UiTheme.blend(t.background, t.surface, .45f);
            UiTheme.panel(left, y, contentWidth - 5, ROW - 2, fill);
            if (a == selected) Gui.drawRect(left, y, left + 2, y + ROW - 2, t.accent);
            ResourceLocation head = SkinHeadCache.get(a.getUsername(), a.getUuid());
            if (head != null) {
                GlStateManager.color(1, 1, 1, 1);
                mc.getTextureManager().bindTexture(head);
                Gui.drawScaledCustomSizeModalRect(left + 8, y + 3, 0, 0, 64, 64, 22, 22, 64, 64);
            } else UiTheme.panel(left + 8, y + 3, 22, 22, t.surface);
            boolean active = name(a).equals(mc.getSession().getUsername());
            String label = (a.isPinned() ? "* " : "") + name(a) + (active ? "  [active]" : "");
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(label, contentWidth - 130), left + 38, y + 5, active ? t.success : t.text);
            String detail = AccountTypes.badge(a) + "  " + (AccountTypes.isOffline(a) ? "Offline mode" : Boolean.FALSE.equals(a.getAvailable()) ? "Sign-in needed" : a.getAvailable() == null ? "Not checked" : "Ready to sign in");
            fontRendererObj.drawString(TextFormatting.translate(detail), left + 38, y + 16, t.muted);
            String ban = AccountTypes.isOffline(a) ? "" : TextFormatting.translate(HypixelBanCheck.renderStatus(a));
            ban = fontRendererObj.trimStringToWidth(ban, 85);
            fontRendererObj.drawString(ban, left + contentWidth - 12 - fontRendererObj.getStringWidth(ban), y + 6, t.muted);
        }
        if (visible.isEmpty()) {
            drawCenteredString(fontRendererObj, UniversalAccountManager.accounts.isEmpty() ? "Add an account to get started" : "No matching accounts", width / 2, top + (bottom - top) / 2 - 4, t.muted);
        } else if (visible.size() > rows) {
            int track = bottom - top, thumb = Math.max(8, track * rows / visible.size());
            int y = top + (track - thumb) * first / (visible.size() - rows);
            Gui.drawRect(left + contentWidth - 3, y, left + contentWidth, y + thumb, t.accent);
        }
        String status = notification != null && !notification.isExpired() ? notification.getMessage() : "Enter: log in   |   Ctrl+F: search   |   Right-click: actions";
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(status, contentWidth - (deleted != null ? 66 : 0)), left, height - 40, t.muted);
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (GuiButton b : contextButtons) b.drawButton(mc, mouseX, mouseY);
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) return;
        switch (button.id) {
            case 0: doLogin(); break;
            case 1: mc.displayGuiScreen(new GuiAddAccount(previousScreen)); break;
            case 2:
                if (selected == null || busy()) break;
                pendingDelete = selected;
                mc.displayGuiScreen(new GuiYesNo(this, "Remove " + name(selected) + "?", "You can undo this while this account screen is open.", "Remove", "Cancel", 1));
                break;
            case 3: mc.displayGuiScreen(previousScreen); break;
            case 4: pin(); break;
            case 5: mc.displayGuiScreen(new GuiChanger(this)); break;
            case 7: if (selected != null) { setClipboardString(name(selected)); message("Copied username."); } break;
            case 9:
                Account launcher = activeLauncher();
                if (launcher != null) { launcher.setType(Account.TYPE_TOKEN); UniversalAccountManager.save(); message("Launcher account saved."); }
                break;
            case 11: mc.displayGuiScreen(new GuiAccountStores(this)); break;
            case 12: if (UiTheme.get().modern) mc.displayGuiScreen(new GuiAppearance(this)); break;
            case 13: pinnedOnly = !pinnedOnly; first = 0; button.displayString = pinnedOnly ? "Pinned" : "All"; break;
            case 14: alphabetical = !alphabetical; button.displayString = alphabetical ? "Sort: A-Z" : "Sort: saved"; break;
            case 15:
                if (deleted != null) {
                    UniversalAccountManager.accounts.add(Math.min(deletedIndex, UniversalAccountManager.accounts.size()), deleted);
                    selected = deleted; deleted = null; UniversalAccountManager.save(); message("Account restored.");
                }
                break;
            default: break;
        }
        rebuild();
    }

    @Override public void confirmClicked(boolean result, int id) {
        if (id == 1 && result && pendingDelete != null) {
            int index = UniversalAccountManager.accounts.indexOf(pendingDelete);
            if (index >= 0) {
                deleted = pendingDelete; deletedIndex = index;
                UniversalAccountManager.accounts.remove(index); UniversalAccountManager.save(); selected = null;
                message("Account removed. Undo is available below.");
            }
        }
        pendingDelete = null;
        mc.displayGuiScreen(this);
    }

    private void doLogin() {
        if (selected == null || busy()) return;
        selectedAccount = UniversalAccountManager.accounts.indexOf(selected);
        LoginController.start(this);
        rebuild();
    }
    private void pin() {
        if (selected == null || busy()) return;
        selected.setPinned(!selected.isPinned()); UniversalAccountManager.resort(); UniversalAccountManager.save(); rebuild();
    }
    private Account activeLauncher() {
        for (Account a : UniversalAccountManager.accounts) if (a.isLauncher() && name(a).equals(mc.getSession().getUsername())) return a;
        return null;
    }
    private void message(String message) { notification = new Notification(message, 4000L); }
    private static String name(Account a) { return a.getUsername() == null || a.getUsername().isEmpty() ? "Unknown" : a.getUsername(); }

    private void openContext(int x, int y) {
        contextButtons.clear();
        if (selected == null || busy()) return;
        boolean launcher = activeLauncher() == selected;
        int menuX = Math.max(2, Math.min(x, width - 114));
        int menuY = Math.max(2, Math.min(y, height - (launcher ? 110 : 88) - 2));
        int[] ids = {0, 4, 7, 2, 9};
        String[] labels = {"Log in", selected.isPinned() ? "Unpin" : "Pin", "Copy name", "Delete...", "Save launcher"};
        for (int i = 0; i < (launcher ? 5 : 4); i++)
            contextButtons.add(new GuiButton(ids[i], menuX, menuY + i * 22, 112, 20, labels[i]));
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (!contextButtons.isEmpty()) {
            GuiButton clicked = null;
            for (GuiButton b : contextButtons) if (b.mousePressed(mc, x, y)) clicked = b;
            contextButtons.clear();
            if (button == 0 && clicked != null && !busy() && UniversalAccountManager.accounts.contains(selected)) {
                clicked.playPressSound(mc.getSoundHandler());
                actionPerformed(clicked);
            }
            return; // Clicking outside dismisses the menu without activating a control underneath.
        }
        search.mouseClicked(x, y, button);
        super.mouseClicked(x, y, button);
        if (x < left || x >= left + contentWidth || y < top || y >= bottom || busy()) return;
        int index = first + (y - top) / ROW;
        if (index >= visible.size()) return;
        selected = visible.get(index); search.setFocused(false);
        if (button == 1) { lastClicked = null; openContext(x, y); }
        else if (button == 0) {
            long now = System.currentTimeMillis();
            if (selected == lastClicked && now - lastClick < 300) { doLogin(); lastClicked = null; }
            else { lastClicked = selected; lastClick = now; }
        }
        rebuild();
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) { contextButtons.clear(); first += wheel > 0 ? -3 : 3; rebuild(); }
    }
    @Override protected void keyTyped(char c, int key) {
        if (!contextButtons.isEmpty()) {
            contextButtons.clear();
            if (key == Keyboard.KEY_ESCAPE) return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (search.isFocused() || !query.isEmpty()) { query = ""; search.setText(""); search.setFocused(false); rebuild(); }
            else mc.displayGuiScreen(previousScreen);
            return;
        }
        if (isCtrlKeyDown() && key == Keyboard.KEY_F) { search.setFocused(true); return; }
        if (search.isFocused() && key != Keyboard.KEY_RETURN && key != Keyboard.KEY_TAB) {
            if (search.textboxKeyTyped(c, key)) { query = search.getText(); first = 0; rebuild(); }
            return;
        }
        if (key == Keyboard.KEY_TAB) { search.setFocused(!search.isFocused()); return; }
        if (key == Keyboard.KEY_RETURN) { search.setFocused(false); if (selected == null && !visible.isEmpty()) selected = visible.get(0); doLogin(); return; }
        if (busy()) return;
        if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN || key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT || key == Keyboard.KEY_HOME || key == Keyboard.KEY_END) {
            int index = visible.indexOf(selected);
            if (isCtrlKeyDown() && !alphabetical && !pinnedOnly && query.isEmpty() && index >= 0 && (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN)) {
                int target = index + (key == Keyboard.KEY_UP ? -1 : 1);
                if (target >= 0 && target < visible.size() && visible.get(target).isPinned() == selected.isPinned()) {
                    Collections.swap(UniversalAccountManager.accounts, index, target); UniversalAccountManager.save(); rebuild();
                }
            } else if (!visible.isEmpty()) {
                if (key == Keyboard.KEY_HOME) index = 0;
                else if (key == Keyboard.KEY_END) index = visible.size() - 1;
                else index += key == Keyboard.KEY_UP ? -1 : key == Keyboard.KEY_PRIOR ? -rows : key == Keyboard.KEY_NEXT ? rows : 1;
                index = Math.max(0, Math.min(visible.size() - 1, index)); selected = visible.get(index);
            }
            int indexNow = visible.indexOf(selected);
            if (indexNow < first) first = indexNow;
            if (indexNow >= first + rows) first = indexNow - rows + 1;
        }
        if (isKeyComboCtrlC(key) && selected != null) { setClipboardString(name(selected)); message("Copied username."); }
        if (key == Keyboard.KEY_DELETE) actionPerformed(new GuiButton(2, 0, 0, "Delete"));
        if (isCtrlKeyDown() && key == Keyboard.KEY_Z) actionPerformed(new GuiButton(15, 0, 0, "Undo"));
        rebuild();
    }
    @Override public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        // Let an in-flight token refresh finish; cancellation can lose a rotated refresh token.
        if (executor != null) executor.shutdown();
    }
}
