package me.proxycracked.universalaccountmanager.gui;

import java.awt.Desktop;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import me.proxycracked.universalaccountmanager.auth.AccountLogin;
import me.proxycracked.universalaccountmanager.auth.AccountTypes;
import me.proxycracked.universalaccountmanager.auth.AuthHttp;
import me.proxycracked.universalaccountmanager.auth.CookieAuth;
import me.proxycracked.universalaccountmanager.auth.CookieFileScanner;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.utils.Notification;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;

import org.apache.commons.lang3.StringUtils;

/**
 * Cookie login: pick one of the cookie files found on disk, paste cookies, or browse for a file.
 *
 * <p>Replaces the bundled screen, which could only open a file dialog. Candidates are found by
 * actually parsing every {@code .txt}/{@code .json} in the usual download folders, so the list
 * shows how many Microsoft cookies each file really holds. A successful login is saved as
 * {@code msa} whenever Microsoft hands back a refresh token, so the account keeps working.</p>
 */
public class GuiCookieAuth extends GuiScreen {
    private static final SimpleDateFormat MODIFIED_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final int LIST_WIDTH = 360;

    private final GuiScreen previousScreen;

    private CookieList list;
    private GuiButton loginButton;
    private GuiButton clipboardButton;
    private GuiButton browseButton;
    private GuiButton rescanButton;
    private GuiButton cancelButton;

    private ExecutorService executor;
    private CompletableFuture<?> task;

    private volatile List<CookieFileScanner.Candidate> candidates = new ArrayList<CookieFileScanner.Candidate>();
    private volatile boolean scanning;
    private volatile boolean busy;
    private volatile String status = "&7Scanning for cookie files...&r";
    private volatile String cause;
    private volatile String successName;
    private volatile boolean success;
    private int selected = -1;

    public GuiCookieAuth(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int centerX = width / 2;
        int firstRow = height - 52;
        int secondRow = height - 28;

        buttonList.add(loginButton = new GuiButton(0, centerX - 154, firstRow, 100, 20, "Login"));
        buttonList.add(clipboardButton = new GuiButton(1, centerX - 50, firstRow, 100, 20, "Paste Cookies"));
        buttonList.add(browseButton = new GuiButton(2, centerX + 54, firstRow, 100, 20, "Browse..."));
        buttonList.add(rescanButton = new GuiButton(3, centerX - 102, secondRow, 100, 20, "Rescan"));
        buttonList.add(cancelButton = new GuiButton(4, centerX + 2, secondRow, 100, 20, "Cancel"));

        list = new CookieList(mc);
        list.registerScrollButtons(7, 8);

        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }
        if (candidates.isEmpty() && !scanning) {
            rescan();
        }
    }

    @Override
    public void onGuiClosed() {
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        if (list != null) {
            list.handleMouseInput();
        }
    }

    @Override
    public void updateScreen() {
        if (success && successName != null) {
            success = false;
            mc.displayGuiScreen(new GuiAccountManager(
                previousScreen,
                new Notification(TextFormatting.translate("&aSuccessful login! (" + successName + ")&r"), 5000L)
            ));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (list != null) {
            list.drawScreen(mouseX, mouseY, partialTicks);
        }

        loginButton.enabled = !busy && selected >= 0 && selected < candidates.size();
        clipboardButton.enabled = !busy;
        browseButton.enabled = !busy;
        rescanButton.enabled = !busy && !scanning;

        super.drawScreen(mouseX, mouseY, partialTicks);

        drawCenteredString(fontRendererObj, "Cookie Login", width / 2, 12, 0xFFFFFF);
        drawCenteredString(
            fontRendererObj,
            TextFormatting.translate("&8Netscape cookies.txt, an extension's JSON, or a pasted Cookie header&r"),
            width / 2,
            24,
            0xFFFFFF
        );

        String formatted = TextFormatting.translate(status);
        int textWidth = fontRendererObj.getStringWidth(formatted);
        Gui.drawRect(width / 2 - textWidth / 2 - 4, height - 70, width / 2 + textWidth / 2 + 4, height - 58, 0x40000000);
        drawCenteredString(fontRendererObj, formatted, width / 2, height - 68, 0xFFFFFF);

        if (cause != null) {
            // These messages name the actual Microsoft verdict, so they need more than one line.
            @SuppressWarnings("unchecked")
            List<String> lines = fontRendererObj.listFormattedStringToWidth(TextFormatting.translate(cause), width - 8);
            int lineHeight = fontRendererObj.FONT_HEIGHT + 1;
            int top = height - 2 - lines.size() * lineHeight;
            Gui.drawRect(0, top - 3, width, height, 0x88000000);
            for (int index = 0; index < lines.size(); index++) {
                drawString(fontRendererObj, lines.get(index), 4, top + index * lineHeight, 0xFFFFFF);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 1 || list == null) {
            return;
        }
        // GuiSlot only reacts to the left button, so the right-click reveal is handled here.
        int slot = list.slotAt(mouseX, mouseY);
        List<CookieFileScanner.Candidate> snapshot = candidates;
        if (slot >= 0 && slot < snapshot.size()) {
            selected = slot;
            reveal(snapshot.get(slot).getFile());
        }
    }

    /** Opens the containing folder, with the file selected where the platform can do that. */
    private void reveal(File file) {
        File folder = file.getParentFile();
        try {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                // One argument, comma and path with nothing between them - a space there and
                // Explorer opens the wrong folder. Its exit code is 1 even on success, so it is
                // not checked; a failure to launch throws instead.
                Runtime.getRuntime().exec(new String[] {"explorer.exe", "/select," + file.getAbsolutePath()});
            } else {
                Desktop.getDesktop().open(folder);
            }
            status = "&aOpened " + (folder == null ? file.getName() : folder.getName()) + " in the file browser.&r";
        } catch (Exception error) {
            try {
                Desktop.getDesktop().open(folder);
                status = "&aOpened " + folder.getName() + " in the file browser.&r";
            } catch (Exception fallbackFailed) {
                status = "&cCould not open that folder: " + AuthHttp.rootMessage(error) + "&r";
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            actionPerformed(cancelButton);
        } else if (keyCode == 28) {
            actionPerformed(loginButton);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        switch (button.id) {
            case 0:
                loginWithSelected();
                break;
            case 1:
                loginFromClipboard();
                break;
            case 2:
                browse();
                break;
            case 3:
                rescan();
                break;
            case 4:
                mc.displayGuiScreen(new GuiAddAccount(previousScreen));
                break;
            default:
                if (list != null) {
                    list.actionPerformed(button);
                }
                break;
        }
    }

    private void rescan() {
        scanning = true;
        selected = -1;
        status = "&7Scanning for cookie files...&r";
        Thread scanner = new Thread(new Runnable() {
            @Override
            public void run() {
                List<CookieFileScanner.Candidate> found = CookieFileScanner.scan(Minecraft.getMinecraft().mcDataDir);
                candidates = found;
                scanning = false;
                if (found.isEmpty()) {
                    status = "&7No cookie files found. Use Paste Cookies or Browse...&r";
                } else {
                    status = "&aFound " + found.size() + " cookie file" + (found.size() == 1 ? "" : "s")
                        + ". &8Right-click one to open its folder.&r";
                }
            }
        }, "UniversalAccountManager-CookieScan");
        scanner.setDaemon(true);
        scanner.start();
    }

    private void loginWithSelected() {
        List<CookieFileScanner.Candidate> snapshot = candidates;
        if (selected < 0 || selected >= snapshot.size()) {
            return;
        }
        login(snapshot.get(selected).getFile());
    }

    private void loginFromClipboard() {
        String clipboard = getClipboardString();
        if (StringUtils.isBlank(clipboard)) {
            status = "&cClipboard is empty.&r";
            return;
        }
        start(CookieAuth.loginFromText(clipboard, progress(), executor));
    }

    private void browse() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The default look and feel is fine too.
        }
        status = "&aFile picker opened in background.&r";
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                FileDialog dialog = new FileDialog((Frame) null, "Select Cookie File", FileDialog.LOAD);
                dialog.setDirectory(System.getProperty("user.home") + File.separator + "Downloads");
                dialog.setFile("*.txt");
                dialog.setModal(true);
                dialog.setVisible(true);
                String name = dialog.getFile();
                if (name == null) {
                    status = "&eFile selection canceled.&r";
                    return;
                }
                File file = new File(dialog.getDirectory(), name);
                if (!file.exists()) {
                    status = "&cSelected file does not exist!&r";
                    return;
                }
                login(file);
            }
        });
    }

    private void login(File cookieFile) {
        start(CookieAuth.loginFromFile(cookieFile, progress(), executor));
    }

    private void start(CompletableFuture<CookieAuth.CookieResult> future) {
        busy = true;
        cause = null;
        task = future
            .thenAccept(new Consumer<CookieAuth.CookieResult>() {
                @Override
                public void accept(CookieAuth.CookieResult result) {
                    save(result);
                }
            })
            .exceptionally(new Function<Throwable, Void>() {
                @Override
                public Void apply(Throwable error) {
                    busy = false;
                    Throwable root = error.getCause() == null ? error : error.getCause();
                    status = "&cCookie login failed.&r";
                    cause = "&c" + AuthHttp.rootMessage(root) + "&r";
                    return null;
                }
            });
    }

    private Consumer<String> progress() {
        return new Consumer<String>() {
            @Override
            public void accept(String message) {
                status = message;
            }
        };
    }

    private void save(CookieAuth.CookieResult result) {
        boolean durable = !StringUtils.isBlank(result.refreshToken);
        AccountLogin.upsert(new AccountLogin.Result(
            result.session,
            result.refreshToken,
            result.accessToken,
            durable ? AccountTypes.MSA : AccountTypes.COOKIE
        ));
        SessionManager.set(result.session);
        successName = result.session.getUsername();
        busy = false;
        success = true;
    }

    /** The list of cookie files the scan turned up. */
    private class CookieList extends GuiSlot {
        CookieList(Minecraft mc) {
            super(mc, GuiCookieAuth.this.width, GuiCookieAuth.this.height, 36, GuiCookieAuth.this.height - 76, 24);
        }

        @Override
        protected int getSize() {
            return GuiCookieAuth.this.candidates.size();
        }

        @Override
        protected void elementClicked(int slot, boolean doubleClick, int mouseX, int mouseY) {
            GuiCookieAuth.this.selected = slot;
            if (doubleClick && !GuiCookieAuth.this.busy) {
                GuiCookieAuth.this.loginWithSelected();
            }
        }

        /** Which row is under the cursor, or -1 outside the list. */
        int slotAt(int mouseX, int mouseY) {
            if (mouseY < this.top || mouseY > this.bottom) {
                return -1;
            }
            return getSlotIndexFromScreenCoords(mouseX, mouseY);
        }

        @Override
        protected boolean isSelected(int slot) {
            return slot == GuiCookieAuth.this.selected;
        }

        @Override
        protected int getContentHeight() {
            return getSize() * 24;
        }

        @Override
        protected void drawBackground() {
            GuiCookieAuth.this.drawDefaultBackground();
        }

        @Override
        public int getListWidth() {
            return LIST_WIDTH;
        }

        @Override
        protected int getScrollBarX() {
            return (GuiCookieAuth.this.width + getListWidth()) / 2 + 2;
        }

        @Override
        protected void drawSlot(int id, int x, int y, int slotHeight, int mouseX, int mouseY) {
            List<CookieFileScanner.Candidate> snapshot = GuiCookieAuth.this.candidates;
            if (id < 0 || id >= snapshot.size()) {
                return;
            }
            CookieFileScanner.Candidate candidate = snapshot.get(id);

            GuiCookieAuth.this.drawString(fontRendererObj, candidate.getName(), x + 2, y + 2, 0xFFFFFF);

            // A file can hold plenty of Microsoft cookies and still be useless: the ones that
            // authenticate are HttpOnly, so JavaScript-made exports miss them entirely.
            String cookies = candidate.hasSignInCookies()
                ? candidate.getMicrosoftCookies() + " MS cookies"
                : "no sign-in cookies";
            GuiCookieAuth.this.drawString(
                fontRendererObj,
                TextFormatting.translate((candidate.hasSignInCookies() ? "&a" : "&c") + cookies + "&r"),
                x + LIST_WIDTH - 14 - fontRendererObj.getStringWidth(cookies),
                y + 2,
                0xFFFFFF
            );

            String subtitle = MODIFIED_FORMAT.format(new Date(candidate.getModified())) + "  " + candidate.getDirectory();
            GuiCookieAuth.this.drawString(
                fontRendererObj,
                TextFormatting.translate("&8" + trim(subtitle, LIST_WIDTH - 18) + "&r"),
                x + 2,
                y + 13,
                0xFFFFFF
            );
        }

        private String trim(String text, int maxWidth) {
            if (fontRendererObj.getStringWidth(text) <= maxWidth) {
                return text;
            }
            String trimmed = text;
            while (trimmed.length() > 4 && fontRendererObj.getStringWidth("..." + trimmed) > maxWidth) {
                trimmed = trimmed.substring(1);
            }
            return "..." + trimmed;
        }
    }
}
