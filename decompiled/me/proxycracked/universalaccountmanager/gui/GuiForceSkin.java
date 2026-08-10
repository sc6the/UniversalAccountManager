package me.proxycracked.universalaccountmanager.gui;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import me.proxycracked.universalaccountmanager.skin.ForceSkinManager;
import me.proxycracked.universalaccountmanager.skin.SkinChanger;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

public class GuiForceSkin extends GuiScreen {
   private final GuiScreen previousScreen;
   private GuiTextField inputField;
   private GuiButton variantButton;
   private GuiButton applyButton;
   private GuiButton uploadButton;
   private GuiButton toggleButton;
   private GuiButton backButton;
   private String variant = "classic";
   private String status = "&7Enter a username, paste a skin URL, or upload a file.&r";
   private byte[] uploadedBytes = null;
   private String uploadedName = null;

   public GuiForceSkin(GuiScreen previousScreen) {
      this.previousScreen = previousScreen;
   }

   public void func_73866_w_() {
      Keyboard.enableRepeatEvents(true);
      this.field_146292_n.clear();
      int cx = this.field_146294_l / 2;
      int cy = this.field_146295_m / 2;
      this.inputField = new GuiTextField(0, this.field_146289_q, cx - 150, cy - 20, 300, 20);
      this.inputField.func_146203_f(32767);
      this.inputField.func_146195_b(true);
      this.field_146292_n.add(this.variantButton = new GuiButton(0, cx - 150, cy + 10, 145, 20, this.variantLabel()));
      this.field_146292_n.add(this.uploadButton = new GuiButton(4, cx + 5, cy + 10, 145, 20, this.uploadLabel()));
      this.field_146292_n.add(this.applyButton = new GuiButton(1, cx - 150, cy + 34, 145, 20, "Apply Force Skin"));
      this.field_146292_n.add(this.toggleButton = new GuiButton(3, cx + 5, cy + 34, 145, 20, this.toggleLabel()));
      this.field_146292_n.add(this.backButton = new GuiButton(2, cx - 75, cy + 60, 150, 20, "Back"));
      this.refreshToggleButton();
   }

   private String variantLabel() {
      return "Model: " + ("slim".equals(this.variant) ? "Slim (3px)" : "Classic (4px)");
   }

   private String uploadLabel() {
      return this.uploadedBytes == null
         ? "Upload File..."
         : "File: " + (this.uploadedName != null && this.uploadedName.length() <= 12 ? this.uploadedName : "(loaded)");
   }

   private String toggleLabel() {
      if (ForceSkinManager.exists()) {
         return "Disable Forced Skin";
      } else {
         return ForceSkinManager.isDisabled() ? "Enable Forced Skin" : "No Forced Skin Set";
      }
   }

   private void refreshToggleButton() {
      if (this.toggleButton != null) {
         this.toggleButton.field_146126_j = this.toggleLabel();
         this.toggleButton.field_146124_l = ForceSkinManager.exists() || ForceSkinManager.isDisabled();
      }
   }

   public void func_146281_b() {
      Keyboard.enableRepeatEvents(false);
   }

   public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
      this.func_146276_q_();
      this.func_73732_a(this.field_146289_q, "Force Skin", this.field_146294_l / 2, this.field_146295_m / 2 - 80, 16777215);
      this.func_73732_a(
         this.field_146289_q,
         TextFormatting.translate("&7Overrides your in-game skin locally — visible only to you.&r"),
         this.field_146294_l / 2,
         this.field_146295_m / 2 - 68,
         -1
      );
      this.func_73732_a(
         this.field_146289_q,
         TextFormatting.translate("&8Disable hides the file without deleting it — toggle to bring it back.&r"),
         this.field_146294_l / 2,
         this.field_146295_m / 2 - 56,
         -1
      );
      this.func_73731_b(this.field_146289_q, "Username or Skin URL:", this.field_146294_l / 2 - 150, this.field_146295_m / 2 - 34, 11184810);
      if (this.status != null) {
         this.func_73732_a(this.field_146289_q, TextFormatting.translate(this.status), this.field_146294_l / 2, this.field_146295_m / 2 + 88, -1);
      }

      this.inputField.func_146194_f();
      super.func_73863_a(mouseX, mouseY, partialTicks);
   }

   protected void func_73869_a(char typedChar, int keyCode) {
      this.inputField.func_146201_a(typedChar, keyCode);
      if (keyCode == 1) {
         this.field_146297_k.func_147108_a(this.previousScreen);
      }
   }

   protected void func_73864_a(int mouseX, int mouseY, int mouseButton) {
      try {
         super.func_73864_a(mouseX, mouseY, mouseButton);
      } catch (Exception var5) {
      }

      this.inputField.func_146192_a(mouseX, mouseY, mouseButton);
   }

   protected void func_146284_a(GuiButton button) {
      if (button != null && button.field_146124_l) {
         switch (button.field_146127_k) {
            case 0:
               this.variant = "slim".equals(this.variant) ? "classic" : "slim";
               this.variantButton.field_146126_j = this.variantLabel();
               break;
            case 1:
               this.apply();
               break;
            case 2:
               this.field_146297_k.func_147108_a(this.previousScreen);
               break;
            case 3:
               if (ForceSkinManager.exists()) {
                  this.status = ForceSkinManager.disable() ? "&aForce Skin disabled.&r" : "&cCouldn't disable Force Skin.&r";
               } else if (ForceSkinManager.isDisabled()) {
                  this.status = ForceSkinManager.enable() ? "&aForce Skin re-enabled.&r" : "&cCouldn't re-enable Force Skin.&r";
               }

               this.refreshToggleButton();
               break;
            case 4:
               this.pickFile();
         }
      }
   }

   private void pickFile() {
      this.status = "&7Opening file picker...&r";
      new Thread(() -> {
         try {
            FileDialog fd = new FileDialog((Frame)null, "Select a skin PNG", 0);
            fd.setFile("*.png");
            fd.setVisible(true);
            String dir = fd.getDirectory();
            String name = fd.getFile();
            if (dir == null || name == null) {
               this.status = "&7Upload cancelled.&r";
               return;
            }

            byte[] data;
            try (InputStream in = new FileInputStream(new File(dir, name))) {
               ByteArrayOutputStream buf = new ByteArrayOutputStream();
               byte[] chunk = new byte[8192];

               int n;
               while ((n = in.read(chunk)) > 0) {
                  buf.write(chunk, 0, n);
               }

               data = buf.toByteArray();
            }

            this.uploadedBytes = data;
            this.uploadedName = name;
            this.uploadButton.field_146126_j = this.uploadLabel();
            this.status = "&aLoaded " + name + " (" + data.length + " bytes)&r";
         } catch (Exception var20) {
            this.status = "&cUpload failed: " + var20.getMessage() + "&r";
         }
      }, "UniversalAccountManager-Picker").start();
   }

   private void apply() {
      if (this.uploadedBytes != null) {
         byte[] bytes = this.uploadedBytes;
         boolean slim = "slim".equals(this.variant);
         this.status = "&7Writing Force Skin from file...&r";
         new Thread(() -> {
            try {
               ForceSkinManager.applyFromBytes(bytes, slim);
               this.uploadedBytes = null;
               this.uploadedName = null;
               this.uploadButton.field_146126_j = this.uploadLabel();
               this.refreshToggleButton();
               this.status = "&aForce Skin saved from upload.&r";
            } catch (Exception var4x) {
               this.status = "&cForce Skin write failed: " + var4x.getMessage() + "&r";
            }
         }, "UniversalAccountManager-ForceSkin").start();
      } else {
         String input = this.inputField.func_146179_b().trim();
         if (StringUtils.isBlank(input)) {
            this.status = "&cEnter a username, URL, or upload a file.&r";
         } else {
            boolean isUrl = input.regionMatches(true, 0, "http://", 0, 7) || input.regionMatches(true, 0, "https://", 0, 8);
            if (isUrl) {
               boolean slim = "slim".equals(this.variant);
               this.status = "&7Downloading skin...&r";
               new Thread(() -> {
                  try {
                     ForceSkinManager.applyFromUrl(input, slim);
                     this.refreshToggleButton();
                     this.status = "&aForce Skin saved.&r";
                  } catch (Exception var4x) {
                     this.status = "&cForce Skin write failed: " + var4x.getMessage() + "&r";
                  }
               }, "UniversalAccountManager-ForceSkin").start();
            } else {
               this.status = "&7Resolving " + input + "'s skin...&r";
               new Thread(() -> {
                  SkinChanger.SkinInfo info = SkinChanger.resolveSkin(input);
                  if (info == null) {
                     this.status = "&cCouldn't find a skin for " + input + "&r";
                  } else {
                     this.variant = info.variant;
                     this.variantButton.field_146126_j = this.variantLabel();
                     this.status = "&7Downloading skin...&r";

                     try {
                        ForceSkinManager.applyFromUrl(info.url, "slim".equals(info.variant));
                        this.refreshToggleButton();
                        this.status = "&aForce Skin saved from " + input + ".&r";
                     } catch (Exception var4x) {
                        this.status = "&cForce Skin write failed: " + var4x.getMessage() + "&r";
                     }
                  }
               }, "UniversalAccountManager-ForceSkin").start();
            }
         }
      }
   }
}
