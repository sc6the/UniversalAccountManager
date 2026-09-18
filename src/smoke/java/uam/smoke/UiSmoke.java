package uam.smoke;

import java.lang.reflect.*;
import java.util.List;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.gui.*;
import me.proxycracked.universalaccountmanager.gui.theme.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid = "uamsmoke", name = "UAM isolated UI verification", version = "1", clientSideOnly = true)
public final class UiSmoke {
    private int ticks, stage;
    private GuiAccountManager accounts;
    private GuiScreen menu;
    @Mod.EventHandler public void init(FMLInitializationEvent e) {
        if (Boolean.getBoolean("blank.ui.smoke")) return;
        net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(this);
    }
    @SuppressWarnings("unchecked") private List<GuiButton> buttons(GuiScreen screen) throws Exception {
        Field f = GuiScreen.class.getDeclaredField("field_146292_n"); f.setAccessible(true);
        return (List<GuiButton>) f.get(screen);
    }
    private void click(int id) throws Exception {
        GuiScreen s = Minecraft.getMinecraft().currentScreen;
        GuiButton found = null;
        for (GuiButton b : buttons(s)) if (b.id == id) found = b;
        if (found == null && s instanceof GuiAccountManager && (id == 2 || id == 4 || id == 7 || id == 9)) {
            Field left = GuiAccountManager.class.getDeclaredField("left"); left.setAccessible(true);
            Field top = GuiAccountManager.class.getDeclaredField("top"); top.setAccessible(true);
            Method mouse = GuiAccountManager.class.getDeclaredMethod("func_73864_a", int.class, int.class, int.class); mouse.setAccessible(true);
            mouse.invoke(s, left.getInt(s) + 40, top.getInt(s) + 5, 1);
            Field menu = GuiAccountManager.class.getDeclaredField("contextButtons"); menu.setAccessible(true);
            for (Object item : (List<?>) menu.get(s)) {
                GuiButton b = (GuiButton) item;
                if (b.id == id) {
                    shot("context-menu");
                    mouse.invoke(s, b.xPosition + 5, b.yPosition + 5, 0);
                    return;
                }
            }
        }
        if (found == null || !found.enabled) throw new AssertionError("Missing/enabled button " + id);
        Method action = s.getClass().getDeclaredMethod("func_146284_a", GuiButton.class);
        action.setAccessible(true); action.invoke(s, found);
    }
    private void shot(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % 35 != 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            switch (stage++) {
                case 0:
                    if (!(mc.currentScreen instanceof GuiMainMenu)) throw new AssertionError("Main menu did not open");
                    boolean entry = false;
                    for (GuiButton b : buttons(mc.currentScreen)) if (b.displayString.equals("Accounts")) entry = true;
                    if (!entry) throw new AssertionError("Accounts menu entry missing");
                    shot("00-main-menu"); menu = mc.currentScreen;
                    UniversalAccountManager.accounts.clear();
                    for (int i = 0; i < 18; i++) {
                        Account a = new Account("offline", "", "", "PreviewUser" + (i < 10 ? "0" : "") + i, "", 0L);
                        a.setPinned(i < 3); UniversalAccountManager.accounts.add(a);
                    }
                    accounts = new GuiAccountManager(menu); mc.displayGuiScreen(accounts); break;
                case 1:
                    for (GuiButton b : buttons(accounts))
                        if (b.id == 2 || b.id == 4 || b.id == 7) throw new AssertionError("Secondary action still in footer");
                    shot("01-default-accounts");
                    if (UiTheme.get().modern) click(12);
                    else {
                        for (GuiButton b : buttons(accounts)) if (b.id == 12) throw new AssertionError("Legacy exposes Appearance");
                        UiTheme.get().preset(0);
                        if (UiTheme.get().text != 0xFFFFFFFF || UiTheme.get().presetIndex != 1) throw new AssertionError("Legacy accepts custom palette");
                        mc.displayGuiScreen(new GuiAppearance(accounts));
                        if (mc.currentScreen != accounts) throw new AssertionError("Legacy theme editor not blocked");
                    }
                    break;
                case 2: if (UiTheme.get().modern) { shot("02-mocha"); click(11); } break;
                case 3: if (UiTheme.get().modern) { shot("03-vanilla"); click(12); } break;
                case 4:
                    if (UiTheme.get().modern) { shot("04-oled"); click(2); }
                    click(13); break;
                case 5:
                    Field visible = GuiAccountManager.class.getDeclaredField("visible"); visible.setAccessible(true);
                    if (((List<?>) visible.get(accounts)).size() != 3) throw new AssertionError("Pinned filter failed");
                    shot("05-pinned"); click(13);
                    Method key = GuiAccountManager.class.getDeclaredMethod("func_73869_a", char.class, int.class); key.setAccessible(true);
                    key.invoke(accounts, '\0', 208); // Down selects the first row.
                    click(2); break;
                case 6: accounts.confirmClicked(true, 1); break;
                case 7:
                    if (UniversalAccountManager.accounts.size() != 17) throw new AssertionError("Delete failed");
                    click(15);
                    if (UniversalAccountManager.accounts.size() != 18) throw new AssertionError("Undo failed");
                    click(1); break;
                case 8: shot("06-add-account"); mc.displayGuiScreen(accounts); click(11); break;
                case 9: shot("07-stores"); mc.displayGuiScreen(accounts); click(5); break;
                case 10: shot("08-changer"); mc.displayGuiScreen(accounts); click(0); break;
                case 11:
                    if (!mc.getSession().getUsername().equals("PreviewUser00")) throw new AssertionError("Offline login did not switch session");
                    shot("09-offline-login");
                    Field search = GuiAccountManager.class.getDeclaredField("search"); search.setAccessible(true);
                    ((GuiTextField) search.get(accounts)).setFocused(true);
                    Method type = GuiAccountManager.class.getDeclaredMethod("func_73869_a", char.class, int.class); type.setAccessible(true);
                    for (char c : "previewuser17".toCharArray()) type.invoke(accounts, c, 0);
                    break;
                case 12:
                    Field filtered = GuiAccountManager.class.getDeclaredField("visible"); filtered.setAccessible(true);
                    List<?> matches = (List<?>) filtered.get(accounts);
                    if (matches.size() != 1 || !((Account) matches.get(0)).getUsername().equals("PreviewUser17")) throw new AssertionError("Search failed");
                    shot("10-search");
                    System.out.println("UAM_UI_SMOKE_PASS: menu, editions, presets, pin filter, delete/undo, add, stores, changer, offline login, search");
                    mc.shutdown(); break;
                default: break;
            }
        } catch (Throwable failure) {
            failure.printStackTrace(); System.err.println("UAM_UI_SMOKE_FAIL stage=" + (stage - 1)); mc.shutdown();
        }
    }
}
