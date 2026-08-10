package me.proxycracked.universalaccountmanager.gui;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.skin.SessionSkinCache;
import me.proxycracked.universalaccountmanager.skin.SkinChanger;
import me.proxycracked.universalaccountmanager.skin.SkinHeadCache;
import me.proxycracked.universalaccountmanager.skin.SkinPreview3D;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

public class GuiChangeSkin extends GuiScreen {
   private final GuiScreen previousScreen;
   private GuiTextField inputField;
   private GuiButton variantButton;
   private GuiButton applyButton;
   private GuiButton uploadButton;
   private GuiButton cancelButton;
   private String variant = "classic";
   private String status = "&7Enter a username, paste a skin URL, or upload a file.&r";
   private byte[] uploadedBytes = null;
   private String uploadedName = null;
   private String lookupKey = "";
   private long debounceUntil = 0L;

   public GuiChangeSkin(GuiScreen previousScreen) {
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
      this.field_146292_n.add(this.applyButton = new GuiButton(1, cx - 75, cy + 34, 150, 20, "Apply Skin"));
      this.field_146292_n.add(this.cancelButton = new GuiButton(2, cx - 75, cy + 58, 150, 20, "Back"));
   }

   private String variantLabel() {
      return "Model: " + ("slim".equals(this.variant) ? "Slim (3px)" : "Classic (4px)");
   }

   private String uploadLabel() {
      return this.uploadedBytes == null
         ? "Upload File..."
         : "File: " + (this.uploadedName != null && this.uploadedName.length() <= 12 ? this.uploadedName : "(loaded)");
   }

   public void func_146281_b() {
      Keyboard.enableRepeatEvents(false);
   }

   public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
      this.func_146276_q_();
      this.func_73732_a(
         this.field_146289_q,
         "Skinchanger (active session: " + SessionManager.get().func_111285_a() + ")",
         this.field_146294_l / 2,
         this.field_146295_m / 2 - 60,
         16777215
      );
      this.func_73731_b(this.field_146289_q, "Username or Skin URL:", this.field_146294_l / 2 - 150, this.field_146295_m / 2 - 34, 11184810);
      if (this.status != null) {
         this.func_73732_a(this.field_146289_q, TextFormatting.translate(this.status), this.field_146294_l / 2, this.field_146295_m / 2 + 88, -1);
      }

      this.inputField.func_146194_f();
      this.drawLivePreview();
      super.func_73863_a(mouseX, mouseY, partialTicks);
   }

   private void drawLivePreview() {
      int previewY = this.field_146295_m / 2 - 30;
      int leftX = this.field_146294_l / 2 - 240;
      int rightX = this.field_146294_l / 2 + 240;
      SessionSkinCache.CachedSkin own = SessionSkinCache.get(SessionManager.get().func_111285_a(), SessionManager.get().func_148255_b());
      this.drawPreviewBox(leftX, previewY, "Current", own);
      String text = this.inputField.func_146179_b().trim();
      boolean isUrl = text.regionMatches(true, 0, "http://", 0, 7) || text.regionMatches(true, 0, "https://", 0, 8);
      if (!isUrl && !text.isEmpty()) {
         this.maybeKickLookup(text);
         SessionSkinCache.CachedSkin typed = SessionSkinCache.get(text, "");
         this.drawPreviewBox(rightX, previewY, text, typed);
      } else if (isUrl) {
         this.drawPreviewBox(rightX, previewY, "(URL)", null);
      }
   }

   private void maybeKickLookup(String text) {
      long now = System.currentTimeMillis();
      if (!text.equalsIgnoreCase(this.lookupKey)) {
         this.lookupKey = text;
         this.debounceUntil = now + 250L;
      } else {
         if (now >= this.debounceUntil) {
            SessionSkinCache.get(text, "");
         }
      }
   }

   private void drawPreviewBox(int cx, int cy, String label, SessionSkinCache.CachedSkin skin) {
      int boxW = 60;
      int boxH = 96;
      int boxX = cx - boxW / 2;
      int boxY = cy - boxH / 2;
      func_73734_a(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, -2147483648);
      this.func_73732_a(this.field_146289_q, TextFormatting.translate("&8" + label), cx, boxY - 10, -1);
      if (skin == null) {
         this.func_73732_a(this.field_146289_q, TextFormatting.translate("&7loading..."), cx, cy - 4, -1);
      } else if (skin.isUnavailable()) {
         this.func_73732_a(this.field_146289_q, TextFormatting.translate("&8not found"), cx, cy - 4, -1);
      } else {
         SkinPreview3D.draw(cx, cy + boxH / 2 - 8, 38, 0.0F, 0.0F, skin.rl, "slim".equals(skin.type));
      }
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
            case 3:
            default:
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
      String token = Minecraft.func_71410_x().func_110432_I().func_148254_d();
      if (this.uploadedBytes != null) {
         byte[] bytes = this.uploadedBytes;
         String variantUsed = this.variant;
         this.status = "&7Uploading skin (" + variantUsed + ")...&r";
         new Thread(() -> {
            try {
               int code = SkinChanger.applySkinFile(bytes, variantUsed, token);
               if (code == 200) {
                  this.uploadedBytes = null;
                  this.uploadedName = null;
                  this.uploadButton.field_146126_j = this.uploadLabel();
               }

               this.status = this.formatResult(code);
            } catch (Exception var5x) {
               this.status = "&cFailed: " + var5x.getMessage() + "&r";
            }
         }, "UniversalAccountManager-Skin").start();
      } else {
         String input = this.inputField.func_146179_b().trim();
         if (StringUtils.isBlank(input)) {
            this.status = "&cEnter a username, URL, or upload a file.&r";
         } else {
            boolean isUrl = input.regionMatches(true, 0, "http://", 0, 7) || input.regionMatches(true, 0, "https://", 0, 8);
            if (isUrl) {
               String variantUsed = this.variant;
               this.status = "&7Applying skin (" + variantUsed + ")...&r";
               new Thread(() -> {
                  try {
                     int code = SkinChanger.applySkinUrl(input, variantUsed, token);
                     if (code == 200) {
                        this.refreshActiveHead(input);
                     }

                     this.status = this.formatResult(code);
                  } catch (Exception var5x) {
                     this.status = "&cFailed: " + var5x.getMessage() + "&r";
                  }
               }, "UniversalAccountManager-Skin").start();
            } else {
               this.status = "&7Resolving " + input + "'s skin...&r";
               new Thread(() -> {
                  try {
                     SkinChanger.SkinInfo info = SkinChanger.resolveSkin(input);
                     if (info == null) {
                        this.status = "&cCouldn't find a skin for " + input + "&r";
                        return;
                     }

                     this.variant = info.variant;
                     this.variantButton.field_146126_j = this.variantLabel();
                     this.status = "&7Applying skin (" + this.variant + ")...&r";
                     int code = SkinChanger.applySkinUrl(info.url, this.variant, token);
                     if (code == 200) {
                        this.refreshActiveHead(info.url);
                     }

                     this.status = this.formatResult(code);
                  } catch (Exception var5x) {
                     this.status = "&cFailed: " + var5x.getMessage() + "&r";
                  }
               }, "UniversalAccountManager-Skin").start();
            }
         }
      }
   }

   private void refreshActiveHead(String appliedSkinUrl) {
      String uuid = SessionManager.get().func_148255_b();
      String name = SessionManager.get().func_111285_a();
      String key = uuid != null && !uuid.isEmpty() ? uuid : name;
      if (key != null && !key.isEmpty()) {
         SkinHeadCache.applyFromSkinUrl(key, appliedSkinUrl);
         SessionSkinCache.putFromUrl(name, uuid, appliedSkinUrl, this.variant);
      }
   }

   private String formatResult(int code) {
      switch (code) {
         case 200:
            return "&aSkin applied!&r";
         case 401:
            return "&cInvalid or expired token. Re-login first.&r";
         case 429:
            return "&cToo many requests, try again later.&r";
         default:
            return "&cMojang returned status " + code + "&r";
      }
   }
}
