package me.proxycracked.universalaccountmanager.auth;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import net.minecraft.util.Session;
import net.minecraft.util.Session.Type;

public final class CookieAuth {
   private static final Gson gson = new Gson();
   private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36";

   private CookieAuth() {
   }

   public static CompletableFuture<CookieAuth.CookieResult> loginFromFile(File cookieFile, Consumer<String> status, Executor executor) {
      return CompletableFuture.supplyAsync(() -> {
         try {
            status.accept("&fReading cookie file...&r");
            Map<String, String> cookies = parseCookieFile(cookieFile);
            if (cookies.isEmpty()) {
               throw new Exception("No login.live.com cookies found in file");
            } else {
               String cookieString = buildCookieString(cookies);
               status.accept("&fStarting Microsoft authentication (1/3)...&r");
               String location3 = followRedirectChain(cookieString, status);
               status.accept("&fExtracting access token...&r");
               int idx = location3.indexOf("accessToken=");
               if (idx < 0) {
                  throw new Exception("No accessToken in redirect URL");
               } else {
                  String accessTokenB64 = location3.substring(idx + "accessToken=".length());
                  String decoded = new String(Base64.getDecoder().decode(accessTokenB64), StandardCharsets.UTF_8);
                  String[] parts = decoded.split("\"rp://api.minecraftservices.com/\",");
                  if (parts.length < 2) {
                     throw new Exception("Failed to decode access token");
                  } else {
                     String rest = parts[1];
                     String token = rest.split("\"Token\":\"")[1].split("\"")[0];
                     String uhs = rest.split(Pattern.quote("{\"DisplayClaims\":{\"xui\":[{\"uhs\":\""))[1].split("\"")[0];
                     String xblToken = "XBL3.0 x=" + uhs + ";" + token;
                     status.accept("&fLogging into Minecraft services...&r");
                     CookieAuth.McResponse mc = postMinecraftLogin(xblToken);
                     if (mc != null && mc.access_token != null) {
                        status.accept("&fFetching Minecraft profile...&r");
                        CookieAuth.ProfileResponse profile = getMinecraftProfile(mc.access_token);
                        if (profile != null && profile.name != null) {
                           Session session = new Session(profile.name, profile.id, mc.access_token, Type.MOJANG.toString());
                           return new CookieAuth.CookieResult(session, mc.access_token);
                        } else {
                           throw new Exception("Could not fetch Minecraft profile");
                        }
                     } else {
                        throw new Exception("Minecraft service rejected the token");
                     }
                  }
               }
            }
         } catch (InterruptedException var16) {
            throw new CancellationException("Cookie login cancelled!");
         } catch (Exception var17) {
            throw new CompletionException("Cookie login failed!", var17);
         }
      }, executor);
   }

   public static Map<String, String> parseCookieFile(File file) throws IOException {
      Map<String, String> cookies = new HashMap<>();

      String line;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
         while ((line = reader.readLine()) != null) {
            if (!line.startsWith("#") && !line.trim().isEmpty()) {
               String[] parts = line.split("\t", -1);
               if (parts.length > 6 && parts[0].endsWith("login.live.com")) {
                  String name = parts[5].trim();
                  if (!cookies.containsKey(name)) {
                     cookies.put(name, parts[6].trim());
                  }
               }
            }
         }
      }

      return cookies;
   }

   public static String buildCookieString(Map<String, String> cookies) {
      StringBuilder sb = new StringBuilder();

      for (Entry<String, String> e : cookies.entrySet()) {
         if (sb.length() > 0) {
            sb.append("; ");
         }

         sb.append(e.getKey()).append("=").append(e.getValue());
      }

      return sb.toString();
   }

   private static String followRedirectChain(String cookieString, Consumer<String> status) throws Exception {
      String url1 = "https://sisu.xboxlive.com/connect/XboxLive/?state=login&cobrandId=8058f65d-ce06-4c30-9559-473c9275a65d&tid=896928775&ru=https%3A%2F%2Fwww.minecraft.net%2Fen-us%2Flogin&aid=1142970254";
      String location1 = redirectGet(url1, null);
      if (location1 == null) {
         throw new Exception("Redirect failed at step 1 (sisu.xboxlive.com)");
      } else {
         location1 = location1.replace(" ", "%20");
         status.accept("&fProcessing Microsoft redirect (2/3)...&r");
         String location2 = redirectGet(location1, cookieString);
         if (location2 == null) {
            throw new Exception("Redirect failed at step 2 (login.live.com)");
         } else {
            status.accept("&fFinalizing Microsoft redirect (3/3)...&r");
            String location3 = redirectGet(location2, cookieString);
            if (location3 == null) {
               throw new Exception("Redirect failed at step 3 (minecraft.net)");
            } else {
               return location3;
            }
         }
      }
   }

   private static String redirectGet(String url, String cookieString) throws Exception {
      HttpsURLConnection conn = (HttpsURLConnection)new URL(url).openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty(
         "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
      );
      conn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
      conn.setRequestProperty("Accept-Language", "en-US;q=0.8");
      conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");
      if (cookieString != null) {
         conn.setRequestProperty("Cookie", cookieString);
      }

      conn.setInstanceFollowRedirects(false);
      conn.connect();
      String location = conn.getHeaderField("Location");
      conn.disconnect();
      return location;
   }

   private static CookieAuth.McResponse postMinecraftLogin(String xblToken) throws Exception {
      String url = "https://api.minecraftservices.com/authentication/login_with_xbox";
      String payload = "{\"identityToken\":\"" + xblToken + "\",\"ensureLegacyEnabled\":true}";
      HttpsURLConnection conn = (HttpsURLConnection)new URL(url).openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setRequestProperty("Accept", "application/json");
      conn.setDoOutput(true);

      try (OutputStream os = conn.getOutputStream()) {
         os.write(payload.getBytes(StandardCharsets.UTF_8));
      }

      StringBuilder body = new StringBuilder();

      try {
         String line;
         try (
            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
         ) {
            while ((line = reader.readLine()) != null) {
               body.append(line);
            }
         }
      } finally {
         conn.disconnect();
      }

      return (CookieAuth.McResponse)gson.fromJson(body.toString(), CookieAuth.McResponse.class);
   }

   private static CookieAuth.ProfileResponse getMinecraftProfile(String accessToken) throws Exception {
      String url = "https://api.minecraftservices.com/minecraft/profile";
      HttpsURLConnection conn = (HttpsURLConnection)new URL(url).openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Authorization", "Bearer " + accessToken);
      conn.setRequestProperty("Accept", "application/json");
      StringBuilder body = new StringBuilder();

      try {
         String line;
         try (
            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
         ) {
            while ((line = reader.readLine()) != null) {
               body.append(line);
            }
         }
      } finally {
         conn.disconnect();
      }

      return (CookieAuth.ProfileResponse)gson.fromJson(body.toString(), CookieAuth.ProfileResponse.class);
   }

   public static class CookieResult {
      public final Session session;
      public final String accessToken;

      public CookieResult(Session session, String accessToken) {
         this.session = session;
         this.accessToken = accessToken;
      }
   }

   private static class McResponse {
      String access_token;
   }

   private static class ProfileResponse {
      String name;
      String id;
   }
}
