package me.proxycracked.universalaccountmanager.gui;

import java.io.IOException;
import me.proxycracked.universalaccountmanager.gui.theme.*;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;

public final class GuiAppearance extends GuiScreen {
    private final GuiScreen parent;
    private final GuiTextField[] fields = new GuiTextField[5];
    private static final String[] LABELS = {"Accent", "Background", "Surface", "Text", "Muted text"};
    private String status = "Colors use #RRGGBB. Apply to preview and save.";
    private int left, top, panelWidth;
    public GuiAppearance(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        if (!UiTheme.get().modern) { mc.displayGuiScreen(parent); return; }
        Keyboard.enableRepeatEvents(true);
        panelWidth = Math.min(360, width - 24); left = (width - panelWidth) / 2;
        top = Math.max(28, (height - 216) / 2);
        buttonList.clear();
        buttonList.add(new GuiButton(10, left, top, panelWidth, 20, ThemeSettings.PRESET_NAMES[0]));
        int w = (panelWidth - 4) / 2;
        buttonList.add(new GuiButton(11, left, top + 26, w, 20, ThemeSettings.PRESET_NAMES[1]));
        buttonList.add(new GuiButton(12, left + w + 4, top + 26, w, 20, ThemeSettings.PRESET_NAMES[2]));
        int[] colors = colors();
        for (int i = 0; i < fields.length; i++) {
            fields[i] = new GuiTextField(i, fontRendererObj, left + 110, top + 54 + i * 23, panelWidth - 140, 18);
            fields[i].setMaxStringLength(7); fields[i].setText(ThemeSettings.hex(colors[i]));
        }
        buttonList.add(new GuiButton(1, left, top + 177, (panelWidth - 4) / 2, 20, "Apply colors"));
        buttonList.add(new GuiButton(2, left + (panelWidth + 4) / 2, top + 177, (panelWidth - 4) / 2, 20, "Done"));
    }
    private int[] colors() { ThemeSettings t = UiTheme.get(); return new int[]{t.accent, t.background, t.surface, t.text, t.muted}; }
    private boolean save() {
        try { UiTheme.get().save(UiTheme.file()); status = "Appearance saved."; return true; }
        catch (IOException e) { status = "Could not save theme. Check config folder access."; return false; }
    }
    private boolean apply() {
        try {
            int[] p = new int[fields.length];
            for (int i = 0; i < p.length; i++) p[i] = ThemeSettings.parseColor(fields[i].getText());
            ThemeSettings t = UiTheme.get(); t.accent = p[0]; t.background = p[1]; t.surface = p[2]; t.text = p[3]; t.muted = p[4];
            return save();
        } catch (IllegalArgumentException e) { status = "Invalid color. Use six hex digits: #8BA4FF"; return false; }
    }
    @Override protected void actionPerformed(GuiButton b) {
        if (!UiTheme.get().modern) { mc.displayGuiScreen(parent); return; }
        if (b.id >= 10 && b.id <= 12) { UiTheme.get().preset(b.id - 10); save(); initGui(); }
        else if (b.id == 1) apply();
        else if (b.id == 2 && apply()) mc.displayGuiScreen(parent);
    }
    @Override public void drawScreen(int x, int y, float ticks) {
        ThemeSettings t = UiTheme.get(); UiTheme.background(width, height);
        if (!t.modern) return;
        drawCenteredString(fontRendererObj, "Appearance", width / 2, top - 18, t.text);
        for (int i = 0; i < fields.length; i++) {
            fontRendererObj.drawString(LABELS[i], left, top + 59 + i * 23, t.text);
            fields[i].drawTextBox();
            int swatch;
            try { swatch = ThemeSettings.parseColor(fields[i].getText()); } catch (IllegalArgumentException e) { swatch = 0xFFFF5555; }
            // Color samples are content, not panel backgrounds; keep them visible in both styles.
            Gui.drawRect(left + panelWidth - 21, top + 54 + i * 23, left + panelWidth - 3, top + 72 + i * 23, swatch);
        }
        fontRendererObj.drawString("Yes / Success", left, top + 166, t.success);
        fontRendererObj.drawString("No / Error", left + panelWidth / 2, top + 166, t.error);
        drawCenteredString(fontRendererObj, fontRendererObj.trimStringToWidth(status, width - 16), width / 2, top + 204, t.muted);
        super.drawScreen(x, y, ticks);
    }
    @Override public void updateScreen() { for (GuiTextField f : fields) f.updateCursorCounter(); }
    @Override protected void mouseClicked(int x, int y, int b) throws IOException { super.mouseClicked(x, y, b); for (GuiTextField f : fields) f.mouseClicked(x, y, b); }
    @Override protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(parent); return; }
        if (key == Keyboard.KEY_RETURN) { apply(); return; }
        if (key == Keyboard.KEY_TAB) {
            int active = -1;
            for (int i = 0; i < fields.length; i++) { if (fields[i].isFocused()) active = i; fields[i].setFocused(false); }
            fields[Math.floorMod(active + (isShiftKeyDown() ? -1 : 1), fields.length)].setFocused(true); return;
        }
        for (GuiTextField f : fields) f.textboxKeyTyped(c, key);
    }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
