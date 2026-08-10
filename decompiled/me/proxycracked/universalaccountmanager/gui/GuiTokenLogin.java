package me.proxycracked.universalaccountmanager.gui;

import java.util.Iterator;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.auth.TokenAuth;
import me.proxycracked.universalaccountmanager.utils.Notification;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.Session;
import net.minecraft.util.Session.Type;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

public class GuiTokenLogin extends GuiScreen {
   private final GuiScreen previousScreen;
   private GuiTextField tokenField;
   private GuiButton loginButton;
   private GuiButton saveButton;
   private GuiButton cancelButton;
   private String status = "&7Paste a Mojang access token to log in or save it as an account.&r";

   public GuiTokenLogin(GuiScreen previousScreen) {
      this.previousScreen = previousScreen;
   }

   public void func_73866_w_() {
      Keyboard.enableRepeatEvents(true);
      this.field_146292_n.clear();
      int cx = this.field_146294_l / 2;
      int cy = this.field_146295_m / 2;
      this.tokenField = new GuiTextField(0, this.field_146289_q, cx - 150, cy - 12, 300, 20);
      this.tokenField.func_146203_f(32767);
      this.tokenField.func_146195_b(true);
      this.field_146292_n.add(this.loginButton = new GuiButton(0, cx - 150, cy + 16, 95, 20, "Login Now"));
      this.field_146292_n.add(this.saveButton = new GuiButton(1, cx - 50, cy + 16, 100, 20, "Save Account"));
      this.field_146292_n.add(this.cancelButton = new GuiButton(2, cx + 55, cy + 16, 95, 20, "Cancel"));
   }

   public void func_146281_b() {
      Keyboard.enableRepeatEvents(false);
   }

   public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
      this.func_146276_q_();
      this.func_73732_a(this.field_146289_q, "Token Login", this.field_146294_l / 2, this.field_146295_m / 2 - 60, 16777215);
      this.func_73731_b(this.field_146289_q, "Mojang access token:", this.field_146294_l / 2 - 150, this.field_146295_m / 2 - 26, 11184810);
      if (this.status != null) {
         String s = TextFormatting.translate(this.status);
         int w = this.field_146289_q.func_78256_a(s);
         Gui.func_73734_a(
            this.field_146294_l / 2 - w / 2 - 4, this.field_146295_m / 2 + 44, this.field_146294_l / 2 + w / 2 + 4, this.field_146295_m / 2 + 56, 1073741824
         );
         this.func_73732_a(this.field_146289_q, s, this.field_146294_l / 2, this.field_146295_m / 2 + 46, -1);
      }

      this.tokenField.func_146194_f();
      super.func_73863_a(mouseX, mouseY, partialTicks);
   }

   protected void func_73869_a(char typedChar, int keyCode) {
      this.tokenField.func_146201_a(typedChar, keyCode);
      if (keyCode == 1) {
         this.field_146297_k.func_147108_a(this.previousScreen);
      } else if (keyCode == 28) {
         this.func_146284_a(this.loginButton);
      }
   }

   protected void func_73864_a(int mouseX, int mouseY, int mouseButton) {
      try {
         super.func_73864_a(mouseX, mouseY, mouseButton);
      } catch (Exception var5) {
      }

      this.tokenField.func_146192_a(mouseX, mouseY, mouseButton);
   }

   protected void func_146284_a(GuiButton button) {
      if (button != null && button.field_146124_l) {
         switch (button.field_146127_k) {
            case 0:
               this.doLogin(false);
               break;
            case 1:
               this.doLogin(true);
               break;
            case 2:
               this.field_146297_k.func_147108_a(this.previousScreen);
         }
      }
   }

   private void doLogin(boolean alsoSave) {
      String token = this.tokenField.func_146179_b();
      if (StringUtils.isBlank(token)) {
         this.status = "&cToken is empty.&r";
      } else {
         this.status = "&7Validating token...&r";
         new Thread(
               () -> {
                  try {
                     String[] info = TokenAuth.getProfileInfo(token);
                     Session session = new Session(info[0], info[1], token, Type.MOJANG.toString());
                     SessionManager.set(session);
                     if (alsoSave) {
                        boolean replaced = false;
                        Iterator var6 = UniversalAccountManager.accounts.iterator();

                        while (true) {
                           if (var6.hasNext()) {
                              Account a = (Account)var6.next();
                              if (!a.getUsername().equalsIgnoreCase(info[0])) {
                                 continue;
                              }

                              a.setType("token");
                              a.setRefreshToken("");
                              a.setAccessToken(token);
                              a.setUuid(info[1]);
                              replaced = true;
                           }

                           if (!replaced) {
                              UniversalAccountManager.accounts.add(new Account("token", "", token, info[0], info[1], 0L));
                           }

                           UniversalAccountManager.save();
                           break;
                        }
                     }

                     Minecraft.func_71410_x()
                        .func_152344_a(
                           () -> this.field_146297_k
                                 .func_147108_a(
                                    new GuiAccountManager(
                                       this.previousScreen,
                                       new Notification(TextFormatting.translate("&aLogged in as " + info[0] + (alsoSave ? " &7(saved)" : "") + "&r"), 5000L)
                                    )
                                 )
                        );
                  } catch (Exception var8) {
                     this.status = TextFormatting.translate("&cInvalid token: " + var8.getMessage() + "&r");
                  }
               },
               "UniversalAccountManager-Login"
            )
            .start();
      }
   }
}
