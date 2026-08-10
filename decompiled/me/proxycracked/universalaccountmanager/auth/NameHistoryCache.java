package me.proxycracked.universalaccountmanager.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import me.proxycracked.universalaccountmanager.utils.HttpUtils;

public final class NameHistoryCache {
   public static final List<NameHistoryCache.Entry> UNAVAILABLE = Collections.emptyList();
   private static final Map<String, List<NameHistoryCache.Entry>> cache = new HashMap<>();
   private static final Set<String> loading = new HashSet<>();
   private static final ExecutorService EXEC = Executors.newFixedThreadPool(2, new ThreadFactory() {
      private final AtomicInteger n = new AtomicInteger(0);

      @Override
      public Thread newThread(Runnable r) {
         Thread t = new Thread(r, "UniversalAccountManager-NameHistory-" + this.n.incrementAndGet());
         t.setDaemon(true);
         return t;
      }
   });

   private NameHistoryCache() {
   }

   public static List<NameHistoryCache.Entry> get(String username, String uuid) {
      String key = key(username, uuid);
      if (key == null) {
         return UNAVAILABLE;
      } else {
         synchronized (cache) {
            List<NameHistoryCache.Entry> v = cache.get(key);
            if (v != null) {
               return v;
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

   private static String key(String username, String uuid) {
      if (uuid != null && !uuid.isEmpty()) {
         return uuid.replace("-", "").toLowerCase();
      } else {
         return username != null && !username.isEmpty() ? "name:" + username.toLowerCase() : null;
      }
   }

   private static void fetch(String key, String username, String uuid) {
      List<NameHistoryCache.Entry> result = null;

      try {
         String lookup = uuid != null && !uuid.isEmpty() ? uuid.replace("-", "") : username;
         result = tryCrafty(lookup);
         if (result == null) {
            result = tryAshcon(lookup);
         }
      } catch (Throwable var8) {
      }

      if (result == null) {
         result = UNAVAILABLE;
      }

      List<NameHistoryCache.Entry> finalResult = result;
      synchronized (cache) {
         cache.put(key, finalResult);
         loading.remove(key);
      }
   }

   private static List<NameHistoryCache.Entry> tryCrafty(String lookup) {
      try {
         String body = HttpUtils.get("https://api.crafty.gg/api/v2/players/" + lookup);
         if (body != null && !body.isEmpty()) {
            JsonElement root = new JsonParser().parse(body);
            if (!root.isJsonObject()) {
               return null;
            } else {
               JsonObject obj = root.getAsJsonObject();
               if (!obj.has("success") || !obj.get("success").getAsBoolean()) {
                  return null;
               } else if (obj.has("data") && obj.get("data").isJsonObject()) {
                  JsonObject data = obj.getAsJsonObject("data");
                  if (data.has("usernames") && data.get("usernames").isJsonArray()) {
                     JsonArray arr = data.getAsJsonArray("usernames");
                     List<NameHistoryCache.Entry> out = new ArrayList<>(arr.size());

                     for (JsonElement el : arr) {
                        if (el.isJsonObject()) {
                           JsonObject o = el.getAsJsonObject();
                           String name = o.has("username") ? o.get("username").getAsString() : null;
                           if (name != null && !name.isEmpty()) {
                              String changedAt = o.has("changed_at") && !o.get("changed_at").isJsonNull() ? o.get("changed_at").getAsString() : null;
                              out.add(new NameHistoryCache.Entry(name, changedAt));
                           }
                        }
                     }

                     if (out.isEmpty()) {
                        return null;
                     } else {
                        Collections.reverse(out);
                        return out;
                     }
                  } else {
                     return null;
                  }
               } else {
                  return null;
               }
            }
         } else {
            return null;
         }
      } catch (Exception var12) {
         return null;
      }
   }

   private static List<NameHistoryCache.Entry> tryAshcon(String lookup) {
      try {
         String body = HttpUtils.get("https://api.ashcon.app/mojang/v2/user/" + lookup);
         if (body != null && !body.isEmpty()) {
            JsonElement root = new JsonParser().parse(body);
            if (!root.isJsonObject()) {
               return null;
            } else {
               JsonObject obj = root.getAsJsonObject();
               if (obj.has("error")) {
                  return null;
               } else if (!obj.has("username_history")) {
                  return null;
               } else {
                  JsonArray arr = obj.getAsJsonArray("username_history");
                  List<NameHistoryCache.Entry> out = new ArrayList<>(arr.size());

                  for (JsonElement el : arr) {
                     if (el.isJsonObject()) {
                        JsonObject o = el.getAsJsonObject();
                        String name = o.has("username") ? o.get("username").getAsString() : null;
                        if (name != null && !name.isEmpty()) {
                           String changedAt = o.has("changed_at") && !o.get("changed_at").isJsonNull() ? o.get("changed_at").getAsString() : null;
                           out.add(new NameHistoryCache.Entry(name, changedAt));
                        }
                     }
                  }

                  return out.isEmpty() ? null : out;
               }
            }
         } else {
            return null;
         }
      } catch (Exception var11) {
         return null;
      }
   }

   public static String formatDate(String iso) {
      if (iso == null) {
         return "";
      } else {
         int t = iso.indexOf(84);
         if (t == 10) {
            return iso.substring(0, 10);
         } else {
            return iso.length() >= 10 ? iso.substring(0, 10) : iso;
         }
      }
   }

   public static final class Entry {
      public final String name;
      public final String changedAt;

      public Entry(String name, String changedAt) {
         this.name = name;
         this.changedAt = changedAt;
      }
   }
}
