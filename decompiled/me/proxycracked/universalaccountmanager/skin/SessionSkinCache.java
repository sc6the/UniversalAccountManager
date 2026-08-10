package me.proxycracked.universalaccountmanager.skin;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import me.proxycracked.universalaccountmanager.utils.HttpUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

public final class SessionSkinCache {
   public static final SessionSkinCache.CachedSkin UNAVAILABLE = new SessionSkinCache.CachedSkin(null, "default");
   private static final Map<String, SessionSkinCache.CachedSkin> cache = new HashMap<>();
   private static final Set<String> loading = new HashSet<>();
   private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(new ThreadFactory() {
      private final AtomicInteger n = new AtomicInteger(0);

      @Override
      public Thread newThread(Runnable r) {
         Thread t = new Thread(r, "UniversalAccountManager-SessionSkin-" + this.n.incrementAndGet());
         t.setDaemon(true);
         return t;
      }
   });

   private SessionSkinCache() {
   }

   public static SessionSkinCache.CachedSkin get(String username, String uuid) {
      String key = key(username, uuid);
      if (key == null) {
         return UNAVAILABLE;
      } else {
         synchronized (cache) {
            SessionSkinCache.CachedSkin c = cache.get(key);
            if (c != null) {
               return c;
            }

            if (loading.contains(key)) {
               return null;
            }

            loading.add(key);
         }

         EXEC.submit(() -> fetch(key, username, uuid));
         return null;
      }
   }

   public static void invalidate(String username, String uuid) {
      String key = key(username, uuid);
      if (key != null) {
         synchronized (cache) {
            cache.remove(key);
            loading.remove(key);
         }
      }
   }

   public static void putFromUrl(String username, String uuid, String url, String variant) {
      String key = key(username, uuid);
      if (key != null && url != null && !url.isEmpty()) {
         synchronized (cache) {
            cache.remove(key);
            loading.add(key);
         }

         EXEC.submit(() -> fetchFromUrl(key, url, variant));
      }
   }

   private static String key(String username, String uuid) {
      if (uuid != null && !uuid.isEmpty()) {
         return uuid.replace("-", "").toLowerCase();
      } else {
         return username != null && !username.isEmpty() ? "name:" + username.toLowerCase() : null;
      }
   }

   private static void fetch(String key, String username, String uuid) {
      try {
         SkinChanger.SkinInfo info = SkinChanger.resolveSkin(username);
         if (info == null) {
            markUnavailable(key);
            return;
         }

         loadAndStore(key, info.url, info.variant);
      } catch (Exception var4) {
         markUnavailable(key);
      }
   }

   private static void fetchFromUrl(String key, String url, String variant) {
      try {
         loadAndStore(key, url, variant);
      } catch (Exception var4) {
         markUnavailable(key);
      }
   }

   private static void loadAndStore(String key, String url, String variant) throws Exception {
      byte[] bytes = HttpUtils.getBytes(url);
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
      if (img == null) {
         markUnavailable(key);
      } else {
         BufferedImage processed = new ImageBufferDownload().func_78432_a(img);
         BufferedImage finalImg = processed != null ? processed : img;
         String type = "slim".equals(variant) ? "slim" : "default";
         Minecraft.func_71410_x().func_152344_a(() -> {
            try {
               DynamicTexture tex = new DynamicTexture(finalImg);
               ResourceLocation rl = Minecraft.func_71410_x().func_110434_K().func_110578_a("uam_session_skin_" + key, tex);
               synchronized (cache) {
                  cache.put(key, new SessionSkinCache.CachedSkin(rl, type));
                  loading.remove(key);
               }
            } catch (Exception var8) {
               markUnavailable(key);
            }
         });
      }
   }

   private static void markUnavailable(String key) {
      synchronized (cache) {
         cache.put(key, UNAVAILABLE);
         loading.remove(key);
      }
   }

   public static final class CachedSkin {
      public final ResourceLocation rl;
      public final String type;

      public CachedSkin(ResourceLocation rl, String type) {
         this.rl = rl;
         this.type = type;
      }

      public boolean isUnavailable() {
         return this.rl == null;
      }
   }
}
