package me.proxycracked.universalaccountmanager.gui;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.CookieAuth;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.auth.TokenAuth;
import me.proxycracked.universalaccountmanager.utils.Notification;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class GuiCookieAuth extends GuiScreen {
   private final GuiScreen previousScreen;
   private GuiButton openButton = null;
   private GuiButton cancelButton = null;
   private boolean openButtonEnabled = true;
   private volatile String status = "&fSelect an exported cookies.txt file (Netscape format).&r";
   private volatile String cause = null;
   private ExecutorService executor;
   private CompletableFuture<?> task;
   private volatile boolean success = false;
   private volatile String successName = null;

   public GuiCookieAuth(GuiScreen previousScreen) {
      this.previousScreen = previousScreen;
   }

   public void func_73866_w_() {
      this.field_146292_n.clear();
      int cy = this.field_146295_m / 2 + this.field_146289_q.field_78288_b / 2 + this.field_146289_q.field_78288_b;
      this.field_146292_n.add(this.openButton = new GuiButton(0, this.field_146294_l / 2 - 75 - 2, cy, 75, 20, "Open"));
      this.field_146292_n.add(this.cancelButton = new GuiButton(1, this.field_146294_l / 2 + 2, cy, 75, 20, "Cancel"));
      if (this.executor == null) {
         this.executor = Executors.newSingleThreadExecutor();
      }
   }

   public void func_146281_b() {
      if (this.task != null && !this.task.isDone()) {
         this.task.cancel(true);
      }

      if (this.executor != null) {
         this.executor.shutdownNow();
      }
   }

   public void func_73876_c() {
      if (this.success && this.successName != null) {
         this.field_146297_k
            .func_147108_a(
               new GuiAccountManager(
                  this.previousScreen, new Notification(TextFormatting.translate(String.format("&aSuccessful login! (%s)&r", this.successName)), 5000L)
               )
            );
         this.success = false;
      }
   }

   public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
      if (this.openButton != null) {
         this.openButton.field_146124_l = this.openButtonEnabled;
      }

      this.func_146276_q_();
      super.func_73863_a(mouseX, mouseY, partialTicks);
      this.func_73732_a(
         this.field_146289_q,
         "Cookie Authentication",
         this.field_146294_l / 2,
         this.field_146295_m / 2 - this.field_146289_q.field_78288_b / 2 - this.field_146289_q.field_78288_b * 2,
         11184810
      );
      if (this.status != null) {
         this.func_73732_a(
            this.field_146289_q,
            TextFormatting.translate(this.status),
            this.field_146294_l / 2,
            this.field_146295_m / 2 - this.field_146289_q.field_78288_b / 2,
            -1
         );
      }

      if (this.cause != null) {
         String causeText = TextFormatting.translate(this.cause);
         Gui.func_73734_a(
            0,
            this.field_146295_m - 2 - this.field_146289_q.field_78288_b - 3,
            3 + this.field_146297_k.field_71466_p.func_78256_a(causeText) + 3,
            this.field_146295_m,
            1677721600
         );
         this.func_73731_b(this.field_146289_q, causeText, 3, this.field_146295_m - 2 - this.field_146289_q.field_78288_b, -1);
      }
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
               try {
                  UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
               } catch (Exception var3) {
               }

               SwingUtilities.invokeLater(() -> {
                  FileDialog dialog = new FileDialog((Frame)null, "Select Cookie File", 0);
                  dialog.setDirectory(System.getProperty("user.home") + File.separator + "Downloads");
                  dialog.setFile("*.txt");
                  dialog.setModal(true);
                  this.status = "&aFile picker opened in background.&r";
                  dialog.setVisible(true);
                  String name = dialog.getFile();
                  if (name == null) {
                     this.status = "&eFile selection canceled.&r";
                  } else {
                     File file = new File(dialog.getDirectory(), name);
                     if (!file.exists()) {
                        this.status = "&cSelected file does not exist!&r";
                     } else {
                        this.openButtonEnabled = false;
                        this.status = "&fStarting cookie authentication...&r";
                        this.cause = null;
                        this.task = CookieAuth.loginFromFile(file, s -> this.status = s, this.executor).thenAccept(result -> {
                           String username = result.session.func_111285_a();
                           String uuid = result.session.func_148255_b();
                           Account existing = null;

                           for (Account a : UniversalAccountManager.accounts) {
                              boolean uuidMatch = uuid != null && !uuid.isEmpty() && a.getUuid() != null && uuid.equalsIgnoreCase(a.getUuid());
                              boolean nameMatch = (uuid == null || uuid.isEmpty()) && a.getUsername() != null && username.equalsIgnoreCase(a.getUsername());
                              if (uuidMatch || nameMatch) {
                                 existing = a;
                                 break;
                              }
                           }

                           if (existing == null) {
                              UniversalAccountManager.accounts.add(new Account("cookie", "", result.accessToken, username, uuid, 0L));
                              UniversalAccountManager.save();
                           } else {
                              String oldTok = existing.getAccessToken();
                              boolean expired = oldTok == null || oldTok.isEmpty() || !TokenAuth.validate(oldTok);
                              if (expired) {
                                 existing.setType("cookie");
                                 existing.setAccessToken(result.accessToken);
                                 existing.setUsername(username);
                                 if (uuid != null && !uuid.isEmpty()) {
                                    existing.setUuid(uuid);
                                 }

                                 UniversalAccountManager.save();
                              }
                           }

                           SessionManager.set(result.session);
                           this.successName = username;
                           this.success = true;
                        }).exceptionally(err -> {
                           this.openButtonEnabled = true;
                           this.status = String.format("&c%s&r", err.getMessage());
                           this.cause = err.getCause() != null ? String.format("&c%s&r", err.getCause().getMessage()) : null;
                           return null;
                        });
                     }
                  }
               });
               break;
            case 1:
               this.field_146297_k.func_147108_a(new GuiAccountManager(this.previousScreen));
         }
      }
   }
}
