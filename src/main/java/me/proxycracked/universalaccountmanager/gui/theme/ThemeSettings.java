package me.proxycracked.universalaccountmanager.gui.theme;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Small, dependency-free preferences file; it never contains account credentials. */
public final class ThemeSettings {
    public boolean modern;
    public int accent, background, surface, text, muted, success, error, warning;
    public int presetIndex;
    public static final String[] PRESET_NAMES = {"Catppuccin Mocha", "Vanilla", "OLED Black & White"};
    private static final int[][] PRESETS = {
        // Official Catppuccin Mocha: mauve, base, surface0, text, subtext0, green, red, yellow.
        {0xCBA6F7, 0x1E1E2E, 0x313244, 0xCDD6F4, 0xA6ADC8, 0xA6E3A1, 0xF38BA8, 0xF9E2AF},
        {0xFFFFFF, 0x101010, 0x303030, 0xFFFFFF, 0xAAAAAA, 0x55FF55, 0xFF5555, 0xFFFF55},
        {0xFFFFFF, 0x000000, 0x101010, 0xFFFFFF, 0xAAAAAA, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF}
    };
    private int[] cachedCodes;
    private int cachedPreset = -1, cachedText, cachedMuted, cachedAccent, cachedSuccess, cachedError, cachedWarning;

    public ThemeSettings(boolean modern) { this.modern = modern; preset(modern ? 0 : 1); }

    public static int parseColor(String value) {
        if (value == null || !value.matches("#?[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Use six hex digits, for example #8BA4FF");
        return 0xFF000000 | Integer.parseInt(value.replace("#", ""), 16);
    }

    public static String hex(int color) { return String.format("#%06X", color & 0xFFFFFF); }

    public void preset(int index) {
        presetIndex = Math.floorMod(index, PRESETS.length);
        int[] p = PRESETS[presetIndex];
        accent = 0xFF000000 | p[0]; background = 0xFF000000 | p[1];
        surface = 0xFF000000 | p[2]; text = 0xFF000000 | p[3]; muted = 0xFF000000 | p[4];
        success = 0xFF000000 | p[5]; error = 0xFF000000 | p[6]; warning = 0xFF000000 | p[7];
    }

    public void forceLegacy() { modern = false; preset(1); }

    /** Map plain text colors as well as inline Minecraft formatting codes. */
    public int textColor(int color) {
        int rgb = color & 0xFFFFFF;
        if ((text & 0xFFFFFF) == rgb || (muted & 0xFFFFFF) == rgb || (accent & 0xFFFFFF) == rgb
            || (success & 0xFFFFFF) == rgb || (error & 0xFFFFFF) == rgb || (warning & 0xFFFFFF) == rgb) return color;
        int r = rgb >> 16, g = (rgb >> 8) & 255, b = rgb & 255;
        int mapped;
        if (r == g && g == b) mapped = r >= 220 ? text : muted;
        else if (presetIndex == 2) mapped = text;
        else if (r > b * 1.2 && g > b * 1.2) mapped = warning;
        else if (r > g * 1.2 && r > b * 1.1) mapped = error;
        else if (g > r * 1.2 && g > b * 1.2) mapped = success;
        else mapped = accent;
        return (color & 0xFF000000) | (mapped & 0xFFFFFF);
    }

    public int[] formattingColors() {
        if (cachedCodes != null && cachedPreset == presetIndex && cachedText == text && cachedMuted == muted
            && cachedAccent == accent && cachedSuccess == success && cachedError == error && cachedWarning == warning) return cachedCodes;
        int[] base;
        if (presetIndex == 0) base = new int[]{0x6C7086, 0x89B4FA, success, 0x94E2D5, error, 0xCBA6F7,
            warning, muted, 0x7F849C, 0x89B4FA, success, 0x89DCEB, error, 0xF5C2E7, warning, text};
        else if (presetIndex == 2) base = new int[]{muted, text, success, text, error, text, warning,
            muted, muted, text, success, text, error, text, warning, text};
        else base = new int[]{0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
            0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
        cachedCodes = new int[32];
        for (int i = 0; i < 16; i++) {
            cachedCodes[i] = base[i] & 0xFFFFFF;
            cachedCodes[i + 16] = (cachedCodes[i] & 0xFCFCFC) >> 2;
        }
        cachedPreset = presetIndex; cachedText = text; cachedMuted = muted; cachedAccent = accent;
        cachedSuccess = success; cachedError = error; cachedWarning = warning;
        return cachedCodes;
    }

    public void load(File file) throws IOException {
        if (!file.isFile()) return;
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(file)) { p.load(in); }
        modern = Boolean.parseBoolean(p.getProperty("modern", Boolean.toString(modern)));
        try { preset(Integer.parseInt(p.getProperty("preset", Integer.toString(presetIndex)))); }
        catch (NumberFormatException ignored) { /* Keep defaults for older preferences. */ }
        accent = color(p, "accent", accent); background = color(p, "background", background);
        surface = color(p, "surface", surface); text = color(p, "text", text); muted = color(p, "muted", muted);
        success = color(p, "success", success); error = color(p, "error", error); warning = color(p, "warning", warning);
    }

    private static int color(Properties p, String key, int fallback) {
        try { return parseColor(p.getProperty(key)); } catch (IllegalArgumentException e) { return fallback; }
    }

    public void save(File file) throws IOException {
        Properties p = new Properties();
        p.setProperty("modern", Boolean.toString(modern));
        p.setProperty("preset", Integer.toString(presetIndex));
        p.setProperty("accent", hex(accent)); p.setProperty("background", hex(background));
        p.setProperty("surface", hex(surface)); p.setProperty("text", hex(text)); p.setProperty("muted", hex(muted));
        p.setProperty("success", hex(success)); p.setProperty("error", hex(error)); p.setProperty("warning", hex(warning));
        Files.createDirectories(file.toPath().toAbsolutePath().getParent());
        Path temp = Files.createTempFile(file.toPath().toAbsolutePath().getParent(), "uam-theme-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) { p.store(out, "UAM appearance - #RRGGBB colors"); }
            Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
