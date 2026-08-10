package me.proxycracked.universalaccountmanager.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import me.proxycracked.universalaccountmanager.utils.HttpUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public final class SkinChanger {
   private SkinChanger() {
   }

   public static SkinChanger.SkinInfo resolveSkin(String username) {
      if (username != null && !username.trim().isEmpty()) {
         try {
            String profile = HttpUtils.get("https://api.mojang.com/users/profiles/minecraft/" + username.trim());
            if (profile != null && !profile.isEmpty()) {
               JsonElement profileEl = new JsonParser().parse(profile);
               if (!profileEl.isJsonObject()) {
                  return null;
               } else {
                  JsonObject profileJson = profileEl.getAsJsonObject();
                  if (!profileJson.has("error") && profileJson.has("id")) {
                     String uuid = profileJson.get("id").getAsString();
                     String session = HttpUtils.get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                     JsonObject sessionJson = new JsonParser().parse(session).getAsJsonObject();
                     if (!sessionJson.has("properties")) {
                        return null;
                     } else {
                        String texturesB64 = null;

                        for (JsonElement el : sessionJson.getAsJsonArray("properties")) {
                           JsonObject prop = el.getAsJsonObject();
                           if ("textures".equals(prop.get("name").getAsString())) {
                              texturesB64 = prop.get("value").getAsString();
                              break;
                           }
                        }

                        if (texturesB64 == null) {
                           return null;
                        } else {
                           String texturesJsonStr = new String(Base64.getDecoder().decode(texturesB64));
                           JsonObject textures = new JsonParser().parse(texturesJsonStr).getAsJsonObject().getAsJsonObject("textures");
                           if (textures == null) {
                              return null;
                           } else {
                              JsonObject skin = textures.getAsJsonObject("SKIN");
                              if (skin != null && skin.has("url")) {
                                 String url = skin.get("url").getAsString();
                                 String variant = "classic";
                                 if (skin.has("metadata")) {
                                    JsonObject meta = skin.getAsJsonObject("metadata");
                                    if (meta.has("model") && "slim".equals(meta.get("model").getAsString())) {
                                       variant = "slim";
                                    }
                                 }

                                 return new SkinChanger.SkinInfo(url, variant);
                              } else {
                                 return null;
                              }
                           }
                        }
                     }
                  } else {
                     return null;
                  }
               }
            } else {
               return null;
            }
         } catch (Exception var14) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static int applySkinUrl(String skinUrl, String variant, String mcAccessToken) throws Exception {
      if (skinUrl != null && !skinUrl.isEmpty()) {
         String v = "slim".equalsIgnoreCase(variant) ? "slim" : "classic";
         CloseableHttpClient client = HttpClients.createDefault();
         Throwable var5 = null;

         int var8;
         try {
            HttpPost req = new HttpPost("https://api.minecraftservices.com/minecraft/profile/skins");
            req.setHeader("Authorization", "Bearer " + mcAccessToken);
            req.setHeader("Content-Type", "application/json");
            String body = String.format("{\"variant\":\"%s\",\"url\":\"%s\"}", v, skinUrl);
            req.setEntity(new StringEntity(body));
            var8 = client.execute(req).getStatusLine().getStatusCode();
         } catch (Throwable var17) {
            var5 = var17;
            throw var17;
         } finally {
            if (client != null) {
               if (var5 != null) {
                  try {
                     client.close();
                  } catch (Throwable var16) {
                     var5.addSuppressed(var16);
                  }
               } else {
                  client.close();
               }
            }
         }

         return var8;
      } else {
         return -1;
      }
   }

   public static int applySkinFile(byte[] pngBytes, String variant, String mcAccessToken) throws Exception {
      if (pngBytes != null && pngBytes.length != 0) {
         String v = "slim".equalsIgnoreCase(variant) ? "slim" : "classic";
         String boundary = "----UAM" + Long.toHexString(System.nanoTime());
         String CRLF = "\r\n";
         HttpURLConnection conn = (HttpURLConnection)new URL("https://api.minecraftservices.com/minecraft/profile/skins").openConnection();
         conn.setDoOutput(true);
         conn.setRequestMethod("POST");
         conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
         conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
         conn.setConnectTimeout(10000);
         conn.setReadTimeout(20000);

         try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            out.writeBytes("--" + boundary + CRLF);
            out.writeBytes("Content-Disposition: form-data; name=\"variant\"" + CRLF + CRLF);
            out.write(v.getBytes(StandardCharsets.UTF_8));
            out.writeBytes(CRLF);
            out.writeBytes("--" + boundary + CRLF);
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"" + CRLF);
            out.writeBytes("Content-Type: image/png" + CRLF + CRLF);
            out.write(pngBytes);
            out.writeBytes(CRLF);
            out.writeBytes("--" + boundary + "--" + CRLF);
         }

         return conn.getResponseCode();
      } else {
         return -1;
      }
   }

   public static final class SkinInfo {
      public final String url;
      public final String variant;

      public SkinInfo(String url, String variant) {
         this.url = url;
         this.variant = variant;
      }
   }
}
