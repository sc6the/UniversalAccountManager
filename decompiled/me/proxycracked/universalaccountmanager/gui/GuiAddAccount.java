package me.proxycracked.universalaccountmanager.gui;

import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class GuiAddAccount extends GuiScreen {
   private final GuiScreen previousScreen;
   private GuiButton cancelButton;

   public GuiAddAccount(GuiScreen previousScreen) {
      this.previousScreen = previousScreen;
   }

   public void func_73866_w_() {
      this.field_146292_n.clear();
      int btnW = 140;
      int gap = 6;
      int cx = this.field_146294_l / 2;
      int cy = this.field_146295_m / 2;
      this.field_146292_n.add(new GuiButton(0, cx - btnW / 2, cy - 30, btnW, 20, "Microsoft"));
      this.field_146292_n.add(new GuiButton(1, cx - btnW / 2, cy - 30 + 24, btnW, 20, "Cookie"));
      this.field_146292_n.add(new GuiButton(2, cx - btnW / 2, cy - 30 + 48, btnW, 20, "Token"));
      this.field_146292_n.add(this.cancelButton = new GuiButton(3, cx - btnW / 2, cy - 30 + 48 + gap + 20, btnW, 20, "Cancel"));
   }

   public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
      this.func_146276_q_();
      this.func_73732_a(
         this.field_146289_q,
         TextFormatting.translate("&fAdd Account&r"),
         this.field_146294_l / 2,
         this.field_146295_m / 2 - 30 - this.field_146289_q.field_78288_b - 6,
         -1
      );
      super.func_73863_a(mouseX, mouseY, partialTicks);
   }

   protected void func_73869_a(char typedChar, int keyCode) {
      if (keyCode == 1) {
         this.func_146284_a(this.cancelButton);
      }
   }

   protected void func_146284_a(GuiButton button) {
      if (button != null && button.field_146124_l) {
         switch (button.field_146127_k) {
            case 0:
               this.field_146297_k.func_147108_a(new GuiMicrosoftAuth(this.previousScreen));
               break;
            case 1:
               this.field_146297_k.func_147108_a(new GuiCookieAuth(this.previousScreen));
               break;
            case 2:
               this.field_146297_k.func_147108_a(new GuiTokenLogin(this.previousScreen));
               break;
            case 3:
               this.field_146297_k.func_147108_a(new GuiAccountManager(this.previousScreen));
         }
      }
   }
}
