package me.proxycracked.universalaccountmanager.skin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import net.minecraft.client.Minecraft;

public final class ForceSkinManager {
   private ForceSkinManager() {
   }

   public static File dir() {
      return new File(Minecraft.func_71410_x().field_71412_D, "config/skinforce");
   }

   public static File skinFile() {
      return new File(dir(), "skin.png");
   }

   public static File cfgFile() {
      return new File(dir(), "skin.cfg");
   }

   public static File skinDisabledFile() {
      return new File(dir(), "skin.png.disabled");
   }

   public static File cfgDisabledFile() {
      return new File(dir(), "skin.cfg.disabled");
   }

   public static boolean exists() {
      return skinFile().isFile();
   }

   public static boolean isDisabled() {
      return skinDisabledFile().isFile();
   }

   public static boolean disable() {
      boolean moved = false;

      try {
         File s = skinFile();
         File sd = skinDisabledFile();
         if (s.isFile()) {
            Files.move(s.toPath(), sd.toPath(), StandardCopyOption.REPLACE_EXISTING);
            moved = true;
         }

         File c = cfgFile();
         File cd = cfgDisabledFile();
         if (c.isFile()) {
            Files.move(c.toPath(), cd.toPath(), StandardCopyOption.REPLACE_EXISTING);
            moved = true;
         }
      } catch (Exception var5) {
      }

      if (moved) {
         ForceSkinLoader.scheduleReload();
      }

      return moved;
   }

   public static boolean enable() {
      try {
         File sd = skinDisabledFile();
         File s = skinFile();
         if (sd.isFile()) {
            Files.move(sd.toPath(), s.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }

         File cd = cfgDisabledFile();
         File c = cfgFile();
         if (cd.isFile()) {
            Files.move(cd.toPath(), c.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Exception var4) {
      }

      boolean live = skinFile().isFile();
      ForceSkinLoader.scheduleReload();
      return live;
   }

   public static void applyFromUrl(String skinUrl, boolean slim) throws Exception {
      HttpURLConnection conn = (HttpURLConnection)new URL(skinUrl).openConnection();
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(15000);
      conn.setRequestProperty("User-Agent", "UniversalAccountManager");

      try (InputStream in = conn.getInputStream()) {
         ByteArrayOutputStream buf = new ByteArrayOutputStream();
         byte[] chunk = new byte[8192];

         int n;
         while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
         }

         applyFromBytes(buf.toByteArray(), slim);
      }
   }

   public static void applyFromBytes(byte[] pngBytes, boolean slim) throws Exception {
      if (pngBytes != null && pngBytes.length != 0) {
         File d = dir();
         if (!d.isDirectory() && !d.mkdirs()) {
            throw new Exception("Couldn't create " + d.getAbsolutePath());
         } else {
            File tmp = new File(d, "skin.png.tmp");

            try (OutputStream out = new FileOutputStream(tmp)) {
               out.write(pngBytes);
            }

            File target = skinFile();
            if (target.exists() && !target.delete()) {
               throw new Exception("Couldn't replace " + target.getAbsolutePath());
            } else if (!tmp.renameTo(target)) {
               throw new Exception("Couldn't move skin.png into place");
            } else {
               try (OutputStream out = new FileOutputStream(cfgFile())) {
                  out.write(("slim=" + slim).getBytes(StandardCharsets.UTF_8));
               }

               File sd = skinDisabledFile();
               if (sd.isFile()) {
                  sd.delete();
               }

               File cd = cfgDisabledFile();
               if (cd.isFile()) {
                  cd.delete();
               }

               ForceSkinLoader.scheduleReload();
            }
         }
      } else {
         throw new Exception("Empty skin data");
      }
   }
}
