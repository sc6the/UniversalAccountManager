package me.proxycracked.universalaccountmanager.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import me.proxycracked.universalaccountmanager.utils.HttpUtils;

public final class NameAvailabilityCache {
   private static final Map<String, NameAvailabilityCache.State> cache = new HashMap<>();
   private static final Set<String> loading = new HashSet<>();
   private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(new ThreadFactory() {
      private final AtomicInteger n = new AtomicInteger(0);

      @Override
      public Thread newThread(Runnable r) {
         Thread t = new Thread(r, "UniversalAccountManager-NameAvail-" + this.n.incrementAndGet());
         t.setDaemon(true);
         return t;
      }
   });

   private NameAvailabilityCache() {
   }

   public static NameAvailabilityCache.State get(String name) {
      if (name == null) {
         return NameAvailabilityCache.State.INVALID;
      } else {
         String key = name.toLowerCase();
         if (key.isEmpty() || key.length() > 16) {
            return NameAvailabilityCache.State.INVALID;
         } else if (!key.matches("[a-z0-9_]+")) {
            return NameAvailabilityCache.State.INVALID;
         } else {
            synchronized (cache) {
               NameAvailabilityCache.State s = cache.get(key);
               if (s != null) {
                  return s;
               }

               if (loading.contains(key)) {
                  return NameAvailabilityCache.State.LOADING;
               }

               loading.add(key);
            }

            EXEC.submit(() -> fetch(key));
            return NameAvailabilityCache.State.LOADING;
         }
      }
   }

   private static void fetch(String name) {
      NameAvailabilityCache.State result = NameAvailabilityCache.State.ERROR;

      try {
         String body = HttpUtils.get("https://api.mojang.com/users/profiles/minecraft/" + name);
         if (body != null && !body.isEmpty()) {
            JsonElement el = new JsonParser().parse(body);
            if (el.isJsonObject()) {
               JsonObject obj = el.getAsJsonObject();
               if (obj.has("error")) {
                  String err = obj.has("errorMessage") ? obj.get("errorMessage").getAsString() : "";
                  if (!err.toLowerCase().contains("invalid") && !err.toLowerCase().contains("path")) {
                     result = NameAvailabilityCache.State.AVAILABLE;
                  } else {
                     result = NameAvailabilityCache.State.INVALID;
                  }
               } else if (obj.has("id")) {
                  result = NameAvailabilityCache.State.TAKEN;
               } else {
                  result = NameAvailabilityCache.State.AVAILABLE;
               }
            }
         } else {
            result = NameAvailabilityCache.State.AVAILABLE;
         }
      } catch (Throwable var8) {
         result = NameAvailabilityCache.State.ERROR;
      }

      synchronized (cache) {
         cache.put(name, result);
         loading.remove(name);
      }
   }

   public static enum State {
      LOADING,
      AVAILABLE,
      TAKEN,
      INVALID,
      ERROR;
   }
}
