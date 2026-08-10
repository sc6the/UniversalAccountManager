package me.proxycracked.universalaccountmanager.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

public final class SkinHeadCache {
   private static final Map<String, ResourceLocation> heads = new HashMap<>();
   private static final Set<String> inFlight = new HashSet<>();
   private static final Map<String, String> usernameToUuid = new HashMap<>();
   private static final ExecutorService EXEC = Executors.newFixedThreadPool(2, new ThreadFactory() {
      private final AtomicInteger n = new AtomicInteger(0);

      @Override
      public Thread newThread(Runnable r) {
         Thread t = new Thread(r, "UniversalAccountManager-Head-" + this.n.incrementAndGet());
         t.setDaemon(true);
         return t;
      }
   });

   private SkinHeadCache() {
   }

   public static ResourceLocation get(String username, String uuid) {
      String key = uuid != null && !uuid.isEmpty() ? uuid : username;
      if (key != null && !key.isEmpty()) {
         synchronized (heads) {
            ResourceLocation rl = heads.get(key);
            if (rl != null) {
               return rl;
            }

            if (inFlight.contains(key)) {
               return null;
            }

            inFlight.add(key);
         }

         fetchAsync(key, username, uuid);
         return null;
      } else {
         return null;
      }
   }

   private static void fetchAsync(String key, String username, String uuid) {
      EXEC.submit(() -> {
         try {
            String resolvedUuid = uuid != null && !uuid.isEmpty() ? uuid.replace("-", "") : null;
            if (resolvedUuid == null) {
               synchronized (usernameToUuid) {
                  resolvedUuid = usernameToUuid.get(username);
               }

               if (resolvedUuid == null) {
                  resolvedUuid = resolveUuid(username);
                  if (resolvedUuid != null) {
                     synchronized (usernameToUuid) {
                        usernameToUuid.put(username, resolvedUuid);
                     }
                  }
               }
            }

            if (resolvedUuid == null) {
               synchronized (heads) {
                  inFlight.remove(key);
                  return;
               }
            }

            String skinUrl = resolveSkinUrl(resolvedUuid);
            BufferedImage head = null;
            if (skinUrl != null) {
               byte[] bytes = HttpUtils.getBytes(skinUrl);
               BufferedImage skin = ImageIO.read(new ByteArrayInputStream(bytes));
               if (skin != null) {
                  head = cropHead(skin);
               }
            }

            if (head == null) {
               synchronized (heads) {
                  inFlight.remove(key);
                  return;
               }
            }

            BufferedImage finalHead = head;
            Minecraft.func_71410_x().func_152344_a(() -> {
               try {
                  DynamicTexture tex = new DynamicTexture(finalHead);
                  ResourceLocation rl = Minecraft.func_71410_x().func_110434_K().func_110578_a("universalaccountmanager_head_" + key.toLowerCase(), tex);
                  synchronized (heads) {
                     heads.put(key, rl);
                     inFlight.remove(key);
                  }
               } catch (Exception var9) {
                  synchronized (heads) {
                     inFlight.remove(key);
                  }
               }
            });
         } catch (Exception var15) {
            synchronized (heads) {
               inFlight.remove(key);
            }
         }
      });
   }

   private static String resolveUuid(String username) {
      try {
         String body = HttpUtils.get("https://api.mojang.com/users/profiles/minecraft/" + username);
         if (body != null && !body.isEmpty()) {
            JsonElement el = new JsonParser().parse(body);
            if (!el.isJsonObject()) {
               return null;
            } else {
               JsonObject json = el.getAsJsonObject();
               return !json.has("id") ? null : json.get("id").getAsString().replace("-", "");
            }
         } else {
            return null;
         }
      } catch (Exception var4) {
         return null;
      }
   }

   private static String resolveSkinUrl(String uuid) {
      try {
         String body = HttpUtils.get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
         JsonObject json = new JsonParser().parse(body).getAsJsonObject();
         if (!json.has("properties")) {
            return null;
         } else {
            String texturesB64 = null;

            for (JsonElement el : json.getAsJsonArray("properties")) {
               JsonObject prop = el.getAsJsonObject();
               if ("textures".equals(prop.get("name").getAsString())) {
                  texturesB64 = prop.get("value").getAsString();
                  break;
               }
            }

            if (texturesB64 == null) {
               return null;
            } else {
               String texturesJson = new String(Base64.getDecoder().decode(texturesB64));
               JsonObject textures = new JsonParser().parse(texturesJson).getAsJsonObject().getAsJsonObject("textures");
               if (textures == null) {
                  return null;
               } else {
                  JsonObject skin = textures.getAsJsonObject("SKIN");
                  return skin != null && skin.has("url") ? skin.get("url").getAsString() : null;
               }
            }
         }
      } catch (Exception var7) {
         return null;
      }
   }

   private static BufferedImage cropHead(BufferedImage skin) {
      BufferedImage face = new BufferedImage(8, 8, 2);
      Graphics2D fg = face.createGraphics();
      fg.drawImage(skin, 0, 0, 8, 8, 8, 8, 16, 16, null);
      if (skin.getWidth() >= 48 && skin.getHeight() >= 16) {
         fg.drawImage(skin, 0, 0, 8, 8, 40, 8, 48, 16, null);
      }

      fg.dispose();
      BufferedImage scaled = new BufferedImage(64, 64, 2);
      Graphics2D sg = scaled.createGraphics();
      sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      sg.drawImage(face, 0, 0, 64, 64, null);
      sg.dispose();
      return scaled;
   }

   public static void invalidate(String key) {
      synchronized (heads) {
         heads.remove(key);
         inFlight.remove(key);
      }
   }

   public static void applyFromSkinUrl(String key, String skinUrl) {
      if (key != null && !key.isEmpty() && skinUrl != null && !skinUrl.isEmpty()) {
         synchronized (heads) {
            heads.remove(key);
            inFlight.add(key);
         }

         EXEC.submit(() -> {
            try {
               byte[] bytes = HttpUtils.getBytes(skinUrl);
               BufferedImage skin = ImageIO.read(new ByteArrayInputStream(bytes));
               if (skin == null) {
                  synchronized (heads) {
                     inFlight.remove(key);
                     return;
                  }
               }

               BufferedImage head = cropHead(skin);
               Minecraft.func_71410_x().func_152344_a(() -> {
                  try {
                     DynamicTexture tex = new DynamicTexture(head);
                     ResourceLocation rl = Minecraft.func_71410_x().func_110434_K().func_110578_a("universalaccountmanager_head_" + key.toLowerCase(), tex);
                     synchronized (heads) {
                        heads.put(key, rl);
                        inFlight.remove(key);
                     }
                  } catch (Exception var9x) {
                     synchronized (heads) {
                        inFlight.remove(key);
                     }
                  }
               });
            } catch (Exception var9) {
               synchronized (heads) {
                  inFlight.remove(key);
               }
            }
         });
      }
   }
}
