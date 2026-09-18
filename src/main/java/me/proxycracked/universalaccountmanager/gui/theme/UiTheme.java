package me.proxycracked.universalaccountmanager.gui.theme;

import java.io.File;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public final class UiTheme {
    private static final boolean MODERN_EDITION = UiTheme.class.getResource("/uam-modern.marker") != null;
    private static ThemeSettings settings;
    private UiTheme() {}

    public static ThemeSettings get() {
        if (settings == null) {
            settings = new ThemeSettings(MODERN_EDITION);
            if (MODERN_EDITION) {
                try { settings.load(file()); } catch (IOException e) { System.err.println("[UAM] Could not read appearance: " + e.getMessage()); }
                settings.modern = true;
            }
        }
        if (!MODERN_EDITION) settings.forceLegacy();
        return settings;
    }

    public static File file() {
        String variant = MODERN_EDITION ? "modern" : "legacy";
        return new File(Minecraft.getMinecraft().mcDataDir, "config/universalaccountmanager-theme-" + variant + ".properties");
    }

    public static boolean owns(GuiScreen screen) {
        return screen != null && screen.getClass().getName().startsWith("me.proxycracked.universalaccountmanager.gui.");
    }

    public static int blend(int a, int b, float amount) {
        int r = (int) (((a >> 16) & 255) * (1 - amount) + ((b >> 16) & 255) * amount);
        int g = (int) (((a >> 8) & 255) * (1 - amount) + ((b >> 8) & 255) * amount);
        int bl = (int) ((a & 255) * (1 - amount) + (b & 255) * amount);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    public static void panel(int x, int y, int w, int h, int color) {
        if (get().modern) Gui.drawRect(x, y, x + w, y + h, color);
    }

    public static void background(int width, int height) {
        ThemeSettings t = get();
        if (!t.modern) {
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            if (screen != null) {
                screen.drawDefaultBackground();
                return;
            }
        }
        Gui.drawRect(0, 0, width, height, t.background);
        Gui.drawRect(0, 0, width, 2, t.accent);
    }

    public static void button(GuiButton b, Minecraft mc, int mouseX, int mouseY) {
        if (!b.visible) return;
        ThemeSettings t = get();
        boolean hover = mouseX >= b.xPosition && mouseX < b.xPosition + b.width && mouseY >= b.yPosition && mouseY < b.yPosition + b.height;
        int fill = b.enabled && hover ? blend(t.surface, t.accent, .24f) : t.surface;
        panel(b.xPosition, b.yPosition, b.width, b.height, fill);
        if (b.enabled && hover) Gui.drawRect(b.xPosition, b.yPosition + b.height - 1, b.xPosition + b.width, b.yPosition + b.height, t.accent);
        String label = mc.fontRendererObj.trimStringToWidth(b.displayString, Math.max(1, b.width - 8));
        mc.fontRendererObj.drawString(label, b.xPosition + (b.width - mc.fontRendererObj.getStringWidth(label)) / 2,
            b.yPosition + (b.height - 8) / 2, b.enabled ? t.text : blend(t.surface, t.muted, .55f), !t.modern);
    }
}
