package me.proxycracked.universalaccountmanager.skin;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

public final class ForceSkinLoader {
   private static final String TEXTURE_KEY = "uam_force_skin";
   private static ResourceLocation skinLocation = null;
   private static boolean slim = false;
   private static boolean hasSkin = false;

   private ForceSkinLoader() {
   }

   public static void init() {
      File dir = ForceSkinManager.dir();
      if (!dir.exists() && !dir.mkdirs()) {
         System.err.println("[UniversalAccountManager] Couldn't create " + dir.getAbsolutePath());
      }

      reload();
   }

   public static synchronized void reload() {
      Minecraft mc = Minecraft.func_71410_x();
      TextureManager tm = mc.func_110434_K();
      slim = readSlimFlag();
      File skinFile = ForceSkinManager.skinFile();
      if (!skinFile.isFile()) {
         clearTexture(tm);
      } else {
         BufferedImage img = null;

         try (FileInputStream in = new FileInputStream(skinFile)) {
            img = ImageIO.read(in);
         } catch (Exception var19) {
            System.err.println("[UniversalAccountManager] Force Skin: failed to read skin.png: " + var19.getMessage());
         }

         if (img == null) {
            clearTexture(tm);
         } else {
            if (skinLocation != null) {
               try {
                  tm.func_147645_c(skinLocation);
               } catch (Exception var15) {
               }
            }

            skinLocation = tm.func_110578_a("uam_force_skin", new DynamicTexture(img));
            hasSkin = skinLocation != null;
         }
      }
   }

   private static void clearTexture(TextureManager tm) {
      if (skinLocation != null) {
         try {
            tm.func_147645_c(skinLocation);
         } catch (Exception var2) {
         }
      }

      skinLocation = null;
      hasSkin = false;
   }

   private static boolean readSlimFlag() {
      File cfg = ForceSkinManager.cfgFile();
      if (!cfg.isFile()) {
         return false;
      } else {
         Properties props = new Properties();

         try (FileInputStream in = new FileInputStream(cfg)) {
            props.load(in);
         } catch (Exception var15) {
            System.err.println("[UniversalAccountManager] Force Skin: failed to read skin.cfg: " + var15.getMessage());
            return false;
         }

         String v = props.getProperty("slim", "false");
         return Boolean.parseBoolean(v.trim());
      }
   }

   public static void scheduleReload() {
      try {
         Minecraft.func_71410_x().func_152344_a(new Runnable() {
            @Override
            public void run() {
               ForceSkinLoader.reload();
            }
         });
      } catch (Exception var1) {
         System.err.println("[UniversalAccountManager] Force Skin: failed to schedule reload: " + var1.getMessage());
      }
   }

   public static ResourceLocation getSkinLocation() {
      return skinLocation;
   }

   public static boolean isSlim() {
      return slim;
   }

   public static boolean hasSkin() {
      return hasSkin;
   }
}
