package me.proxycracked.universalaccountmanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.TokenAuth;
import me.proxycracked.universalaccountmanager.skin.ForceSkinLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(
   modid = "universalaccountmanager",
   version = "1.7",
   clientSideOnly = true,
   acceptedMinecraftVersions = "1.8.9"
)
public class UniversalAccountManager {
   private static final Minecraft mc = Minecraft.func_71410_x();
   private static final File file = new File(mc.field_71412_D, "universalaccountmanager_accounts.json");
   private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
   public static final ArrayList<Account> accounts = new ArrayList<>();

   @EventHandler
   public static void init(FMLInitializationEvent event) {
      MinecraftForge.EVENT_BUS.register(new Events());
      File legacy = new File(mc.field_71412_D, "tokenmanager_accounts.json");
      if (!file.exists() && legacy.exists()) {
         if (legacy.renameTo(file)) {
            System.out.println("[UniversalAccountManager] Migrated tokenmanager_accounts.json -> universalaccountmanager_accounts.json");
         } else {
            System.err.println("[UniversalAccountManager] Failed to migrate legacy accounts file");
         }
      }

      if (!file.exists()) {
         try {
            if ((file.getParentFile().exists() || file.getParentFile().mkdirs()) && file.createNewFile()) {
               System.out.println("[UniversalAccountManager] Created universalaccountmanager_accounts.json");
            }
         } catch (IOException var3) {
            System.err.println("[UniversalAccountManager] Couldn't create universalaccountmanager_accounts.json");
         }
      }

      load();
      autoImportLauncherAccount();
      resort();
      ForceSkinLoader.init();
   }

   private static void autoImportLauncherAccount() {
      try {
         Session s = mc.func_110432_I();
         if (s == null) {
            return;
         }

         String username = s.func_111285_a();
         String tmpUuid = s.func_148255_b();
         String token = s.func_148254_d();
         if (username == null || username.isEmpty()) {
            return;
         }

         if (token == null || token.isEmpty()) {
            return;
         }

         if (tmpUuid == null) {
            tmpUuid = "";
         }

         String uuid = tmpUuid;
         if ("Player".equals(username) && uuid.isEmpty()) {
            return;
         }

         Account match = findMatch(uuid, username);
         if (match == null) {
            accounts.add(new Account("launcher", "", token, username, uuid, 0L));
            save();
            System.out.println("[UniversalAccountManager] Auto-imported launcher account: " + username);
            return;
         }

         Thread t = new Thread(() -> {
            try {
               String existing = match.getAccessToken();
               boolean expired = existing == null || existing.isEmpty() || !TokenAuth.validate(existing);
               if (!expired) {
                  return;
               }

               match.setType("launcher");
               match.setAccessToken(token);
               match.setUsername(username);
               if (!uuid.isEmpty()) {
                  match.setUuid(uuid);
               }

               save();
               System.out.println("[UniversalAccountManager] Refreshed expired token for: " + username);
            } catch (Exception var6x) {
            }
         }, "UniversalAccountManager-LauncherImport");
         t.setDaemon(true);
         t.start();
      } catch (Exception var7) {
         System.err.println("[UniversalAccountManager] Failed to auto-import launcher account: " + var7.getMessage());
      }
   }

   private static Account findMatch(String uuid, String username) {
      for (Account a : accounts) {
         boolean uuidMatch = uuid != null && !uuid.isEmpty() && a.getUuid() != null && uuid.equalsIgnoreCase(a.getUuid());
         boolean nameMatch = (uuid == null || uuid.isEmpty()) && a.getUsername() != null && username.equalsIgnoreCase(a.getUsername());
         if (uuidMatch || nameMatch) {
            return a;
         }
      }

      return null;
   }

   public static void load() {
      accounts.clear();
      if (file.exists() && file.length() != 0L) {
         try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            JsonElement json = new JsonParser().parse(r);
            if (json instanceof JsonArray) {
               for (JsonElement el : json.getAsJsonArray()) {
                  JsonObject o = el.getAsJsonObject();
                  Account acc = new Account(
                     Optional.ofNullable(o.get("type")).<String>map(JsonElement::getAsString).orElse("ms"),
                     Optional.ofNullable(o.get("refreshToken")).<String>map(JsonElement::getAsString).orElse(""),
                     Optional.ofNullable(o.get("accessToken")).<String>map(JsonElement::getAsString).orElse(""),
                     Optional.ofNullable(o.get("username")).<String>map(JsonElement::getAsString).orElse(""),
                     Optional.ofNullable(o.get("uuid")).<String>map(JsonElement::getAsString).orElse(""),
                     Optional.ofNullable(o.get("unban")).<Long>map(JsonElement::getAsLong).orElse(0L)
                  );
                  acc.setPinned(Optional.ofNullable(o.get("pinned")).<Boolean>map(JsonElement::getAsBoolean).orElse(false));
                  accounts.add(acc);
               }
            }
         } catch (Exception var17) {
            System.err.println("[UniversalAccountManager] Failed to load accounts: " + var17.getMessage());
         }
      }
   }

   public static void resort() {
      accounts.sort(Comparator.<Account, Integer>comparing(a -> a.isPinned() ? 0 : 1).thenComparing(a -> a.isPinned() ? safeName(a) : ""));
   }

   public static int firstUnpinnedIndex() {
      int i = 0;

      while (i < accounts.size() && accounts.get(i).isPinned()) {
         i++;
      }

      return i;
   }

   private static String safeName(Account a) {
      String n = a.getUsername();
      return n == null ? "" : n.toLowerCase();
   }

   public static void save() {
      try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
         JsonArray arr = new JsonArray();

         for (Account a : accounts) {
            JsonObject o = new JsonObject();
            o.addProperty("type", a.getType());
            o.addProperty("refreshToken", a.getRefreshToken());
            o.addProperty("accessToken", a.getAccessToken());
            o.addProperty("username", a.getUsername());
            o.addProperty("uuid", a.getUuid());
            o.addProperty("unban", a.getUnban());
            o.addProperty("pinned", a.isPinned());
            arr.add(o);
         }

         w.println(gson.toJson(arr));
      } catch (IOException var16) {
         System.err.println("[UniversalAccountManager] Failed to save accounts: " + var16.getMessage());
      }
   }
}
