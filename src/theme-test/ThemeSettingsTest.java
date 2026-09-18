import java.io.*;
import java.nio.file.*;
import me.proxycracked.universalaccountmanager.gui.theme.ThemeSettings;

public final class ThemeSettingsTest {
    public static void main(String[] args) throws Exception {
        check(ThemeSettings.parseColor("#8bA4ff") == 0xFF8BA4FF, "case-insensitive hex");
        check(ThemeSettings.parseColor("000000") == 0xFF000000, "opaque black");
        for (String invalid : new String[]{"", "#123", "#12345678", "red", "#GG0000", null}) {
            boolean rejected = false;
            try { ThemeSettings.parseColor(invalid); } catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "invalid hex rejected");
        }
        Path temp = Files.createTempDirectory("uam-theme-test-");
        File file = temp.resolve("theme.properties").toFile();
        try {
            ThemeSettings original = new ThemeSettings(false);
            original.preset(2); original.accent = 0xFF123456; original.save(file);
            ThemeSettings loaded = new ThemeSettings(true); loaded.load(file);
            check(!loaded.modern && loaded.accent == 0xFF123456 && loaded.surface == original.surface, "round trip");
            Files.write(file.toPath(), "accent=broken\nsurface=#ABCDEF\n".getBytes("UTF-8"));
            ThemeSettings recovered = new ThemeSettings(true); recovered.load(file);
            check(recovered.accent == 0xFFCBA6F7 && recovered.surface == 0xFFABCDEF, "recover individual corrupt colors");
            recovered.load(temp.resolve("missing").toFile());
            check(recovered.modern, "missing config keeps edition default");
            recovered.preset(0);
            check(recovered.background == 0xFF1E1E2E && recovered.text == 0xFFCDD6F4, "official Mocha base and text");
            check(recovered.formattingColors()[10] == 0xA6E3A1 && recovered.formattingColors()[12] == 0xF38BA8, "Mocha success/error codes");
            recovered.preset(1);
            check(recovered.text == 0xFFFFFFFF && recovered.success == 0xFF55FF55 && recovered.error == 0xFFFF5555, "Vanilla semantics");
            recovered.preset(2);
            check(recovered.background == 0xFF000000, "OLED pure black");
            for (int color : recovered.formattingColors()) check(gray(color), "OLED formatting is monochrome");
            check(gray(recovered.textColor(0xFF55FF55)) && gray(recovered.textColor(0xFFFF5555)), "OLED plain statuses are monochrome");
            recovered.save(file);
            ThemeSettings oled = new ThemeSettings(true); oled.load(file);
            check(oled.presetIndex == 2 && oled.background == 0xFF000000, "preset persistence");
            recovered.forceLegacy();
            check(!recovered.modern && recovered.presetIndex == 1 && recovered.text == 0xFFFFFFFF, "Legacy overrides custom theme");
            System.out.println("ThemeSettings: all checks passed");
        } finally { Files.deleteIfExists(file.toPath()); Files.deleteIfExists(temp); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static boolean gray(int color) { return ((color >> 16) & 255) == ((color >> 8) & 255) && ((color >> 8) & 255) == (color & 255); }
}
