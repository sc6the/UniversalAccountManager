package uam.smoke;

import java.lang.reflect.*;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Opt-in integration checks; isolated game directory and synthetic local world only. */
@Mod(modid = "blankuismoke", name = "Blank UI verification", version = "1", clientSideOnly = true)
public final class BlankUiSmoke {
    private int ticks, stage, waits, rowCount;
    private GuiScreen clickGui;
    private Object skyModule;
    private Object capeModule;
    private Class<?> capes;
    private Class<?> sky, renderer;
    private int[] header;

    @Mod.EventHandler public void init(FMLInitializationEvent e) {
        if (Boolean.getBoolean("blank.ui.smoke")) net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(this);
    }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static Object invoke(Object object, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = object.getClass().getMethod(method, types); return m.invoke(object, args);
    }
    private void mouse(int x, int y, int button) throws Exception {
        Method m = clickGui.getClass().getDeclaredMethod("func_73864_a", int.class, int.class, int.class);
        m.setAccessible(true); m.invoke(clickGui, x, y, button);
    }
    private Object setting(String name) throws Exception { return sky.getField(name).get(null); }
    private void bool(String name, boolean value) throws Exception { invoke(setting(name), "set", new Class<?>[]{boolean.class}, value); }
    private void imageName(String name) throws Exception { invoke(setting("image"), "selectName", new Class<?>[]{String.class}, name); }
    private void shot(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, "blank-" + name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }
    private void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private GuiScreen packs() throws Exception {
        return (GuiScreen)Class.forName("me.txb1.forge.gui.EsdeathResourcePackGui").getConstructor(GuiScreen.class).newInstance(clickGui);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % 35 != 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            switch (stage++) {
                case 0:
                    check(mc.currentScreen instanceof GuiMainMenu, "Missing main menu");
                    mc.displayGuiScreen(new GuiMultiplayer(mc.currentScreen)); break;
                case 1:
                    Field buttons = GuiScreen.class.getDeclaredField("field_146292_n"); buttons.setAccessible(true);
                    for (Object item : (List<?>)buttons.get(mc.currentScreen)) {
                        GuiButton b = (GuiButton)item;
                        check(b.id != 1791 && b.id != 9301 && !b.displayString.toLowerCase().contains("account"), "Multiplayer account button remains");
                    }
                    shot("01-multiplayer");
                    clickGui = (GuiScreen)Class.forName("me.txb1.forge.gui.BlankClickGui").newInstance();
                    mc.displayGuiScreen(clickGui); break;
                case 2:
                    check(mc.getLimitFramerate() == mc.gameSettings.limitFramerate, "Blank menu still capped at vanilla menu FPS");
                    List<?> rows = (List<?>)field(clickGui, "rowModule");
                    rowCount = rows.size(); check(rowCount > 20, "ClickGUI modules missing");
                    sky = Class.forName("me.txb1.player.modulesystem.modules.render.CustomSkybox");
                    renderer = Class.forName("me.txb1.forge.sky.SkyboxRenderer");
                    int index = -1;
                    for (int i = 0; i < rows.size(); i++) if (sky.isInstance(rows.get(i))) { skyModule = rows.get(i); index = i; }
                    check(index >= 0, "Custom Skybox module missing");
                    shot("02-modules");
                    int[] r = (int[])((List<?>)field(clickGui, "rowRect")).get(index);
                    mouse(r[0] + 12, r[1] + 5, 1); break;
                case 3:
                    check(((List<?>)field(clickGui, "setSetting")).size() >= 10, "Sky settings do not expand");
                    shot("03-sky-settings");
                    header = (int[])((List<?>)field(clickGui, "hdrRect")).get(2);
                    mouse(header[0] + 8, header[1] + 5, 1); break;
                case 4:
                    check(((List<?>)field(clickGui, "rowModule")).size() < rowCount, "Collapse does not update layout");
                    mouse(header[0] + 8, header[1] + 5, 1); break;
                case 5:
                    check(((List<?>)field(clickGui, "rowModule")).size() == rowCount, "Expand does not restore modules");
                    mouse(header[0] + 8, header[1] + 5, 0);
                    Method directDrag = clickGui.getClass().getDeclaredMethod("moveDraggedFrame", int.class, int.class);
                    directDrag.setAccessible(true);
                    Method layout = clickGui.getClass().getDeclaredMethod("rebuildLayout"); layout.setAccessible(true);
                    for (int offset = 1; offset <= 120; offset++) {
                        directDrag.invoke(clickGui, header[0] + 8 + offset, header[1] + 5);
                        layout.invoke(clickGui);
                        List<?> headers = (List<?>)field(clickGui, "hdrRect");
                        int[] live = (int[])headers.get(headers.size() - 1);
                        check(live[0] == header[0] + offset && live[1] == header[1], "Drag lagged or eased between ticks");
                    }
                    Method drag = clickGui.getClass().getDeclaredMethod("func_146273_a", int.class, int.class, int.class, long.class);
                    drag.setAccessible(true); drag.invoke(clickGui, header[0] + 28, header[1] + 25, 0, 50L);
                    Method release = clickGui.getClass().getDeclaredMethod("func_146286_b", int.class, int.class, int.class);
                    release.setAccessible(true); release.invoke(clickGui, header[0] + 28, header[1] + 25, 0); break;
                case 6:
                    List<?> headers = (List<?>)field(clickGui, "hdrRect");
                    int[] moved = (int[])headers.get(headers.size() - 1);
                    check(moved[0] == header[0] + 20 && moved[1] == header[1] + 20, "Drag does not update layout");
                    mc.displayGuiScreen(packs()); break;
                case 7:
                    shot("04-pack-menu");
                    mc.launchIntegratedServer("blank-ui-smoke", "Blank UI Smoke", new WorldSettings(1L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT)); break;
                case 8:
                    if (mc.theWorld == null || mc.thePlayer == null) { check(++waits < 40, "World load timeout"); stage--; break; }
                    mc.displayGuiScreen(null); mc.thePlayer.setPositionAndRotation(0, 100, 0, 0, -25);
                    mc.thePlayer.capabilities.isFlying = true; mc.gameSettings.renderDistanceChunks = 6;
                    mc.theWorld.setWorldTime(6000); invoke(skyModule, "toggle", new Class<?>[0]);
                    bool("imageEnabled", false); bool("fog", true);
                    invoke(setting("color"), "set", new Class<?>[]{int.class}, 0xFFFF3355); break;
                case 9:
                    Vec3 color = mc.theWorld.getSkyColor(mc.thePlayer, 0);
                    check(Math.abs(color.xCoord - 1) < .001 && Math.abs(color.yCoord - 0.2) < .001, "Sky tint hook failed");
                    Vec3 fog = mc.theWorld.getFogColor(0);
                    check(Math.abs(fog.xCoord - .75) < .001, "Fog tint hook failed");
                    shot("05-tinted-sky");
                    File folder = (File)renderer.getMethod("directory").invoke(null); folder.mkdirs();
                    BufferedImage atlas = new BufferedImage(96, 64, BufferedImage.TYPE_INT_RGB);
                    int[] colors = {0x222222, 0x7799FF, 0x44AAFF, 0x8855FF, 0x55DDFF, 0xFF99CC};
                    for (int y = 0; y < 64; y++) for (int x = 0; x < 96; x++) atlas.setRGB(x, y, colors[y / 32 * 3 + x / 32]);
                    ImageIO.write(atlas, "png", new File(folder, "test-cubemap.png"));
                    ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "png", new File(folder, "invalid.png"));
                    renderer.getMethod("rescan").invoke(null);
                    imageName("test-cubemap.png");
                    check(invoke(setting("image"), "serialize", new Class<?>[0]).equals("test-cubemap.png"), "Sky filename not persisted");
                    bool("imageEnabled", true); bool("fog", false); break;
                case 10:
                    Field loaded = renderer.getDeclaredField("LOADED"); loaded.setAccessible(true);
                    check(((Map<?,?>)loaded.get(null)).get("test-cubemap.png") != null, "renderSky hook did not load cubemap");
                    check(org.lwjgl.opengl.GL11.glGetError() == 0, "Sky rendering caused GL error");
                    shot("06-image-sky");
                    mc.displayGuiScreen(packs()); break;
                case 11:
                    shot("07-transparent-packs");
                    imageName("invalid.png");
                    check(!(Boolean)renderer.getMethod("renderReplacement").invoke(null), "Malformed cubemap must fall back");
                    imageName("../outside.png");
                    check(!(Boolean)renderer.getMethod("renderReplacement").invoke(null), "Sky path traversal accepted");
                    invoke(skyModule, "toggle", new Class<?>[0]);
                    check(!(Boolean)renderer.getMethod("active").invoke(null), "Sky disable failed");
                    check(!(Boolean)renderer.getMethod("renderReplacement").invoke(null), "Disabled image still active");
                    mc.displayGuiScreen(clickGui); break;
                case 12:
                    shot("08-clickgui-world");
                    for (Object module : (List<?>)field(clickGui, "rowModule")) if (module.getClass().getSimpleName().equals("CapeModule")) capeModule = module;
                    check(capeModule != null, "Capes module missing");
                    capes = capeModule.getClass();
                    invoke(field(capeModule, "cape"), "setIndex", new Class<?>[]{int.class}, 1);
                    invoke(capeModule, "toggle", new Class<?>[0]);
                    mc.gameSettings.thirdPersonView = 1; mc.thePlayer.rotationPitch = 0; mc.displayGuiScreen(null);
                    waits = 0; break;
                case 13:
                    Object official = capes.getMethod("activeCapeLocation").invoke(null);
                    if (official == null) { check(++waits < 15, "Official cape failed to load"); stage--; break; }
                    check(official.equals(mc.thePlayer.getLocationCape()), "Cape texture not returned by render hook");
                    shot("09-official-cape");
                    Class<?> custom = Class.forName("me.txb1.player.capesystem.CustomCapes");
                    File capeFolder = (File)custom.getMethod("directory").invoke(null); capeFolder.mkdirs();
                    BufferedImage artwork = new BufferedImage(22, 17, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < 17; y++) for (int x = 0; x < 22; x++) artwork.setRGB(x, y, (y / 3 % 2 == 0) ? 0xFFFF33AA : 0xFF55FFFF);
                    ImageIO.write(artwork, "png", new File(capeFolder, "test-cape.png"));
                    custom.getMethod("rescan").invoke(null);
                    invoke(field(capeModule, "custom"), "selectName", new Class<?>[]{String.class}, "test-cape.png"); break;
                case 14:
                    Object texture = capes.getMethod("activeCapeLocation").invoke(null);
                    check(texture != null && texture.toString().contains("blank_custom_cape"), "Custom cape not loaded");
                    check(texture.equals(mc.thePlayer.getLocationCape()), "Custom cape render hook failed");
                    net.minecraft.client.entity.EntityOtherPlayerMP other = new net.minecraft.client.entity.EntityOtherPlayerMP(mc.theWorld, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "OtherPreview"));
                    check(!texture.equals(other.getLocationCape()), "Custom cape leaked to other players");
                    shot("10-custom-cape");
                    invoke(capeModule, "toggle", new Class<?>[0]);
                    check(capes.getMethod("activeCapeLocation").invoke(null) == null, "Cape disable failed");
                    waits = 0; break;
                case 15:
                    Class<?> screenshots = Class.forName("me.txb1.forge.performance.AsyncScreenshots");
                    if (!(Boolean)screenshots.getMethod("idle").invoke(null)) { check(++waits < 15, "Screenshot writer did not finish"); stage--; break; }
                    BufferedImage saved = ImageIO.read(new File(mc.mcDataDir, "screenshots/blank-10-custom-cape.png"));
                    check(saved != null && saved.getWidth() == mc.displayWidth && saved.getHeight() == mc.displayHeight, "Async screenshot dimensions incorrect");
                    Class<?> resourceCache = Class.forName("me.txb1.forge.performance.PackResourceCache");
                    System.out.println("PACK_CACHE: hits=" + resourceCache.getMethod("hits").invoke(null) + " bytes=" + resourceCache.getMethod("cachedBytes").invoke(null));
                    System.out.println("BLANK_UI_SMOKE_PASS: multiplayer buttons, module rows, inline settings, collapse/expand, drag, packs, sky tint/fog, cubemap render, fallback, official/custom capes, async screenshots");
                    mc.shutdown(); break;
                default: break;
            }
        } catch (Throwable error) { error.printStackTrace(); System.err.println("BLANK_UI_SMOKE_FAIL stage=" + (stage - 1)); mc.shutdown(); }
    }
}
