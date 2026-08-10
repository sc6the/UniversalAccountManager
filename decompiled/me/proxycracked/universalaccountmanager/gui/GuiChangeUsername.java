package me.proxycracked.universalaccountmanager.gui;

import java.util.List;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.NameAvailabilityCache;
import me.proxycracked.universalaccountmanager.auth.NameChanger;
import me.proxycracked.universalaccountmanager.auth.NameHistoryCache;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.skin.SessionSkinCache;
import me.proxycracked.universalaccountmanager.skin.SkinHeadCache;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.Session;
import net.minecraft.util.Session.Type;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

public class GuiChangeUsername extends GuiScreen {
   private final GuiScreen previousScreen;
   private GuiTextField nameField;
   private GuiButton applyButton;
   private GuiButton cancelButton;
   private String status = "&7Type to check availability live.&r";
   private String lookupKey = "";
   private long debounceUntil = 0L;

   public GuiChangeUsername(GuiScreen previousScreen) {
      this.previousScreen = previousScreen;
   }

   public void func_73866_w_() {
      Keyboard.enableRepeatEvents(true);
      this.field_146292_n.clear();
      int cx = this.field_146294_l / 2;
      int cy = this.field_146295_m / 2;
      this.nameField = new GuiTextField(0, this.field_146289_q, cx - 150, cy - 20, 300, 20);
      this.nameField.func_146203_f(16);
      this.nameField.func_146195_b(true);
      this.field_146292_n.add(this.applyButton = new GuiButton(1, cx - 150, cy + 10, 300, 20, "Change Name"));
      this.field_146292_n.add(this.cancelButton = new GuiButton(2, cx - 75, cy + 34, 150, 20, "Back"));
   }

   public void func_146281_b() {
      Keyboard.enableRepeatEvents(false);
   }

   public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
      this.func_146276_q_();
      this.func_73732_a(
         this.field_146289_q,
         "Change Username (active session: " + SessionManager.get().func_111285_a() + ")",
         this.field_146294_l / 2,
         this.field_146295_m / 2 - 60,
         16777215
      );
      this.func_73731_b(this.field_146289_q, "New Username:", this.field_146294_l / 2 - 150, this.field_146295_m / 2 - 34, 11184810);
      this.nameField.func_146194_f();
      String typed = this.nameField.func_146179_b().trim();
      this.drawAvailability(typed);
      if (this.status != null) {
         this.func_73732_a(this.field_146289_q, TextFormatting.translate(this.status), this.field_146294_l / 2, this.field_146295_m / 2 + 64, -1);
      }

      this.drawNameHistory();
      super.func_73863_a(mouseX, mouseY, partialTicks);
   }

   private void drawAvailability(String typed) {
      if (!typed.isEmpty()) {
         this.maybeKickAvailability(typed);
         NameAvailabilityCache.State state = NameAvailabilityCache.get(typed);
         String label;
         switch (state) {
            case LOADING:
               label = "&7checking...";
               break;
            case AVAILABLE:
               label = "&a✔ available";
               break;
            case TAKEN:
               label = "&c✘ taken";
               break;
            case INVALID:
               label = "&8invalid";
               break;
            default:
               label = "&8couldn't check";
         }

         this.func_73731_b(this.field_146289_q, TextFormatting.translate(label), this.field_146294_l / 2 - 150, this.field_146295_m / 2 + 2, -1);
      }
   }

   private void maybeKickAvailability(String typed) {
      long now = System.currentTimeMillis();
      if (!typed.equalsIgnoreCase(this.lookupKey)) {
         this.lookupKey = typed;
         this.debounceUntil = now + 250L;
      } else {
         if (now >= this.debounceUntil) {
            NameAvailabilityCache.get(typed);
         }
      }
   }

   private void drawNameHistory() {
      int cx = this.field_146294_l / 2;
      int hy = this.field_146295_m / 2 + 84;
      String ownName = SessionManager.get().func_111285_a();
      String ownUuid = SessionManager.get().func_148255_b();
      this.func_73732_a(this.field_146289_q, TextFormatting.translate("&7&lNAME HISTORY"), cx, hy, -1);
      hy += 11;
      List<NameHistoryCache.Entry> hist = NameHistoryCache.get(ownName, ownUuid);
      if (hist == null) {
         this.func_73732_a(this.field_146289_q, TextFormatting.translate("&8loading..."), cx, hy, -1);
      } else if (hist.isEmpty()) {
         this.func_73732_a(this.field_146289_q, TextFormatting.translate("&8unavailable"), cx, hy, -1);
      } else {
         int maxRows = 6;
         int shown = Math.min(maxRows, hist.size());

         for (int i = hist.size() - 1; i >= hist.size() - shown; i--) {
            NameHistoryCache.Entry e = hist.get(i);
            String date = e.changedAt == null ? "first" : NameHistoryCache.formatDate(e.changedAt);
            this.func_73732_a(this.field_146289_q, TextFormatting.translate("&8" + date + " &f" + e.name), cx, hy, -1);
            hy += 10;
         }

         if (shown < hist.size()) {
            this.func_73732_a(this.field_146289_q, TextFormatting.translate("&8+" + (hist.size() - shown) + " older"), cx, hy, -1);
            hy += 10;
         }
      }

      this.func_73732_a(this.field_146289_q, TextFormatting.translate("&8&ohistory may be incomplete"), cx, hy + 4, -1);
   }

   protected void func_73869_a(char typedChar, int keyCode) {
      this.nameField.func_146201_a(typedChar, keyCode);
      if (keyCode == 1) {
         this.field_146297_k.func_147108_a(this.previousScreen);
      }
   }

   protected void func_73864_a(int mouseX, int mouseY, int mouseButton) {
      try {
         super.func_73864_a(mouseX, mouseY, mouseButton);
      } catch (Exception var5) {
      }

      this.nameField.func_146192_a(mouseX, mouseY, mouseButton);
   }

   protected void func_146284_a(GuiButton button) {
      if (button != null && button.field_146124_l) {
         switch (button.field_146127_k) {
            case 1:
               this.doChange();
               break;
            case 2:
               this.field_146297_k.func_147108_a(this.previousScreen);
         }
      }
   }

   private void doChange() {
      String name = this.nameField.func_146179_b().trim();
      if (StringUtils.isBlank(name)) {
         this.status = "&cEnter a username.&r";
      } else {
         String token = Minecraft.func_71410_x().func_110432_I().func_148254_d();
         this.status = "&7Changing username to " + name + "...&r";
         new Thread(() -> {
            try {
               int code = NameChanger.changeName(name, token);
               switch (code) {
                  case 200:
                     this.applyToSession(name, token);
                     this.status = "&aUsername changed to " + name + "!&r";
                     break;
                  case 400:
                     this.status = "&cInvalid username.&r";
                     break;
                  case 401:
                     this.status = "&cInvalid or expired token. Re-login first.&r";
                     break;
                  case 403:
                     this.status = "&cName is unavailable or on cooldown.&r";
                     break;
                  case 429:
                     this.status = "&cToo many requests, try again later.&r";
                     break;
                  default:
                     this.status = "&cMojang returned status " + code + "&r";
               }
            } catch (Exception var4) {
               this.status = "&cFailed: " + var4.getMessage() + "&r";
            }
         }, "UniversalAccountManager-Name").start();
      }
   }

   private void applyToSession(String newName, String token) {
      Session current = SessionManager.get();
      Session updated = new Session(newName, current.func_148255_b(), current.func_148254_d(), Type.MOJANG.toString());
      SessionManager.set(updated);

      for (Account acc : UniversalAccountManager.accounts) {
         if (token.equals(acc.getAccessToken())) {
            acc.setUsername(newName);
            String key = acc.getUuid() != null && !acc.getUuid().isEmpty() ? acc.getUuid() : acc.getUsername();
            SkinHeadCache.invalidate(key);
            SessionSkinCache.invalidate(newName, acc.getUuid());
         }
      }

      UniversalAccountManager.save();
   }
}
