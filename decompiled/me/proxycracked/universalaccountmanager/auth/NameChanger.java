package me.proxycracked.universalaccountmanager.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public final class NameChanger {
   private NameChanger() {
   }

   public static String checkAvailability(String name, String mcAccessToken) throws Exception {
      CloseableHttpClient client = HttpClients.createDefault();
      Throwable var3 = null;

      String var8;
      try {
         HttpGet req = new HttpGet("https://api.minecraftservices.com/minecraft/profile/name/" + name + "/available");
         req.setHeader("Authorization", "Bearer " + mcAccessToken);
         String body = EntityUtils.toString(client.execute(req).getEntity(), StandardCharsets.UTF_8);
         JsonElement el = new JsonParser().parse(body);
         if (!el.isJsonObject()) {
            return "UNKNOWN";
         }

         JsonObject json = el.getAsJsonObject();
         if (!json.has("status")) {
            return "UNKNOWN";
         }

         var8 = json.get("status").getAsString();
      } catch (Throwable var19) {
         var3 = var19;
         throw var19;
      } finally {
         if (client != null) {
            if (var3 != null) {
               try {
                  client.close();
               } catch (Throwable var18) {
                  var3.addSuppressed(var18);
               }
            } else {
               client.close();
            }
         }
      }

      return var8;
   }

   public static int changeName(String newName, String mcAccessToken) throws Exception {
      CloseableHttpClient client = HttpClients.createDefault();
      Throwable var3 = null;

      int var5;
      try {
         HttpPut req = new HttpPut("https://api.minecraftservices.com/minecraft/profile/name/" + newName);
         req.setHeader("Authorization", "Bearer " + mcAccessToken);
         var5 = client.execute(req).getStatusLine().getStatusCode();
      } catch (Throwable var14) {
         var3 = var14;
         throw var14;
      } finally {
         if (client != null) {
            if (var3 != null) {
               try {
                  client.close();
               } catch (Throwable var13) {
                  var3.addSuppressed(var13);
               }
            } else {
               client.close();
            }
         }
      }

      return var5;
   }
}
