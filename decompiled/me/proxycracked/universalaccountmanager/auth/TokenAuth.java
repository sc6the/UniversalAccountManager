package me.proxycracked.universalaccountmanager.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import net.minecraft.util.Session;
import net.minecraft.util.Session.Type;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public final class TokenAuth {
   private TokenAuth() {
   }

   public static String[] getProfileInfo(String token) throws Exception {
      CloseableHttpClient client = HttpClients.createDefault();
      Throwable var2 = null;

      String[] var6;
      try {
         HttpGet req = new HttpGet("https://api.minecraftservices.com/minecraft/profile");
         req.setHeader("Authorization", "Bearer " + token);
         String body = EntityUtils.toString(client.execute(req).getEntity(), StandardCharsets.UTF_8);
         JsonObject json = new JsonParser().parse(body).getAsJsonObject();
         if (!json.has("name") || !json.has("id")) {
            throw new Exception(
               json.has("error")
                  ? json.get("error").getAsString() + ": " + (json.has("errorMessage") ? json.get("errorMessage").getAsString() : "")
                  : "Token rejected"
            );
         }

         var6 = new String[]{json.get("name").getAsString(), json.get("id").getAsString()};
      } catch (Throwable var15) {
         var2 = var15;
         throw var15;
      } finally {
         if (client != null) {
            if (var2 != null) {
               try {
                  client.close();
               } catch (Throwable var14) {
                  var2.addSuppressed(var14);
               }
            } else {
               client.close();
            }
         }
      }

      return var6;
   }

   public static boolean validate(String token) {
      try {
         getProfileInfo(token);
         return true;
      } catch (Exception var2) {
         return false;
      }
   }

   public static CompletableFuture<Session> login(String token, Executor executor) {
      return CompletableFuture.supplyAsync(() -> {
         try {
            String[] info = getProfileInfo(token);
            return new Session(info[0], info[1], token, Type.MOJANG.toString());
         } catch (InterruptedException var2) {
            throw new CancellationException("Token login cancelled!");
         } catch (Exception var3) {
            throw new CompletionException("Token login failed!", var3);
         }
      }, executor);
   }
}
