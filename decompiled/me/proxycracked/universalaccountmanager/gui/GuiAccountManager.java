package me.proxycracked.universalaccountmanager.gui;

import java.io.IOException;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.AccountValidator;
import me.proxycracked.universalaccountmanager.auth.MicrosoftAuth;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.auth.TokenAuth;
import me.proxycracked.universalaccountmanager.hypixel.HypixelBanCheck;
import me.proxycracked.universalaccountmanager.skin.SkinHeadCache;
import me.proxycracked.universalaccountmanager.utils.Notification;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class GuiAccountManager extends GuiScreen {
   private final GuiScreen previousScreen;
   private GuiButton addAccountButton;
   private GuiButton clearExpiredButton;
   private GuiButton changeSkinButton;
   private GuiButton changeUsernameButton;
   private GuiButton saveLauncherButton;
   private GuiButton forceSkinButton;
   private GuiButton deleteButton;
   private GuiButton cancelButton;
   private GuiAccountManager.GuiAccountList accountList;
   private Notification notification;
   private int selectedAccount = -1;
   private ExecutorService executor;
   private CompletableFuture<Void> task;
   private int dragSource = -1;
   private int dragTarget = -1;
   private int dragMouseY = 0;
   private boolean dragMoved = false;
   private int pendingDeleteIndex = -1;
   private static final int CONFIRM_DELETE_ID = 100;
   private boolean ctxOpen = false;
   private int ctxX;
   private int ctxY;
   private int ctxTarget = -1;
   private static final int CTX_W = 78;
   private static final int CTX_ROW_H = 14;

   public GuiAccountManager(GuiScreen previousScreen) {
      this.previousScreen = previousScreen;
   }

   public GuiAccountManager(GuiScreen previousScreen, Notification notification) {
      this.previousScreen = previousScreen;
      this.notification = notification;
   }

   public void func_73866_w_() {
      UniversalAccountManager.load();
      Keyboard.enableRepeatEvents(true);
      this.field_146292_n.clear();
      int btnW = 100;
      int gap = 4;
      int rowY1 = this.field_146295_m - 76;
      int rowY2 = this.field_146295_m - 52;
      int rowY3 = this.field_146295_m - 28;
      int rowStart = this.field_146294_l / 2 - (btnW * 3 + gap * 2) / 2;
      this.field_146292_n.add(this.changeUsernameButton = new GuiButton(6, this.field_146294_l / 2 - btnW / 2, rowY1, btnW, 20, "Change Username"));
      this.field_146292_n.add(this.saveLauncherButton = new GuiButton(9, this.field_146294_l / 2 - btnW / 2, rowY1, btnW, 20, "Save as Token"));
      this.field_146292_n.add(this.changeSkinButton = new GuiButton(5, rowStart, rowY2, btnW, 20, "Skinchanger"));
      this.field_146292_n.add(this.addAccountButton = new GuiButton(1, rowStart + btnW + gap, rowY2, btnW, 20, "Add Account"));
      this.field_146292_n.add(this.forceSkinButton = new GuiButton(10, rowStart + (btnW + gap) * 2, rowY2, btnW, 20, "Force Skin"));
      this.field_146292_n.add(this.clearExpiredButton = new GuiButton(8, rowStart, rowY3, btnW, 20, "Clear Expired"));
      this.field_146292_n.add(this.deleteButton = new GuiButton(2, rowStart + btnW + gap, rowY3, btnW, 20, "Delete Account"));
      this.field_146292_n.add(this.cancelButton = new GuiButton(3, rowStart + (btnW + gap) * 2, rowY3, btnW, 20, "Cancel"));
      this.accountList = new GuiAccountManager.GuiAccountList(this.field_146297_k);
      this.accountList.func_148134_d(11, 12);
      AccountValidator.validateAll();
      this.func_73876_c();
   }

   private Account activeAccount() {
      String token = SessionManager.get().func_148254_d();
      if (token == null) {
         return null;
      } else {
         for (Account acc : UniversalAccountManager.accounts) {
            if (token.equals(acc.getAccessToken())) {
               return acc;
            }
         }

         return null;
      }
   }

   public void func_146281_b() {
      Keyboard.enableRepeatEvents(false);
      if (this.task != null && !this.task.isDone()) {
         this.task.cancel(true);
         this.executor.shutdownNow();
      }
   }

   public void func_73876_c() {
      boolean has = this.selectedAccount >= 0 && this.selectedAccount < UniversalAccountManager.accounts.size();
      if (this.deleteButton != null) {
         this.deleteButton.field_146124_l = has;
      }

      Account active = this.activeAccount();
      if (this.changeUsernameButton != null) {
         this.changeUsernameButton.field_146125_m = active != null && !active.isLauncher();
      }

      if (this.saveLauncherButton != null) {
         this.saveLauncherButton.field_146125_m = active != null && active.isLauncher();
      }
   }

   public void func_73863_a(int mouseX, int mouseY, float renderPartialTicks) {
      if (this.accountList != null) {
         this.accountList.func_148128_a(mouseX, mouseY, renderPartialTicks);
      }

      super.func_73863_a(mouseX, mouseY, renderPartialTicks);
      this.func_73732_a(
         this.field_146289_q,
         TextFormatting.translate(String.format("&fUniversal Account Manager &8(&7%s&8)&r", UniversalAccountManager.accounts.size())),
         this.field_146294_l / 2,
         16,
         -1
      );
      String activeText = TextFormatting.translate(String.format("&7Active: &b%s&r", SessionManager.get().func_111285_a()));
      this.func_73731_b(this.field_146289_q, activeText, 5, 5, -1);
      if (this.notification != null && !this.notification.isExpired()) {
         String t = this.notification.getMessage();
         int w = this.field_146289_q.func_78256_a(t);
         Gui.func_73734_a(this.field_146294_l / 2 - w / 2 - 4, 28, this.field_146294_l / 2 + w / 2 + 4, 28 + this.field_146289_q.field_78288_b + 6, -2147483648);
         this.func_73732_a(this.field_146289_q, t, this.field_146294_l / 2, 31, -1);
      }

      if (this.dragSource >= 0 && this.dragMoved && this.accountList != null && this.dragSource < UniversalAccountManager.accounts.size()) {
         int top = this.accountList.getTopY();
         int bot = this.accountList.getBottomY() - 28;
         int rowY = Math.max(top, Math.min(bot, mouseY - 14));
         int rowX = this.field_146294_l / 2 - this.accountList.func_148139_c() / 2 + 4;
         Gui.func_73734_a(rowX - 2, rowY - 1, rowX + this.accountList.func_148139_c() - 4, rowY + 27, -1071634384);
         Gui.func_73734_a(rowX - 2, rowY - 1, rowX + this.accountList.func_148139_c() - 4, rowY, -10496);
         Gui.func_73734_a(rowX - 2, rowY + 26, rowX + this.accountList.func_148139_c() - 4, rowY + 27, -10496);
         this.accountList.drawAccountRow(rowX, rowY, this.dragSource);
      }

      if (this.ctxOpen) {
         this.drawContextMenu(mouseX, mouseY);
      }
   }

   private void drawContextMenu(int mouseX, int mouseY) {
      String[] items = this.contextItems();
      int h = items.length * 14 + 2;
      Gui.func_73734_a(this.ctxX - 1, this.ctxY - 1, this.ctxX + 78 + 1, this.ctxY + h + 1, -16777216);
      Gui.func_73734_a(this.ctxX, this.ctxY, this.ctxX + 78, this.ctxY + h, -14669776);

      for (int i = 0; i < items.length; i++) {
         int rowY = this.ctxY + 1 + i * 14;
         boolean hover = mouseX >= this.ctxX && mouseX <= this.ctxX + 78 && mouseY >= rowY && mouseY < rowY + 14;
         if (hover) {
            Gui.func_73734_a(this.ctxX, rowY, this.ctxX + 78, rowY + 14, -12957608);
         }

         this.func_73731_b(this.field_146289_q, TextFormatting.translate(items[i]), this.ctxX + 6, rowY + 3, hover ? -1 : -3355444);
      }
   }

   private String[] contextItems() {
      if (this.ctxTarget >= 0 && this.ctxTarget < UniversalAccountManager.accounts.size()) {
         Account a = UniversalAccountManager.accounts.get(this.ctxTarget);
         return new String[]{a.isPinned() ? "&eUnpin&r" : "&ePin&r", "&aLogin&r"};
      } else {
         return new String[0];
      }
   }

   private int contextItemAt(int mouseX, int mouseY) {
      String[] items = this.contextItems();
      if (mouseX >= this.ctxX && mouseX <= this.ctxX + 78) {
         for (int i = 0; i < items.length; i++) {
            int rowY = this.ctxY + 1 + i * 14;
            if (mouseY >= rowY && mouseY < rowY + 14) {
               return i;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private boolean contextHit(int mouseX, int mouseY) {
      if (!this.ctxOpen) {
         return false;
      } else {
         int h = this.contextItems().length * 14 + 2;
         return mouseX >= this.ctxX && mouseX <= this.ctxX + 78 && mouseY >= this.ctxY && mouseY <= this.ctxY + h;
      }
   }

   private void openContext(int mouseX, int mouseY, int slot) {
      this.ctxOpen = true;
      this.ctxTarget = slot;
      int h = this.contextItems().length * 14 + 2;
      this.ctxX = Math.min(mouseX, this.field_146294_l - 78 - 2);
      this.ctxY = Math.min(mouseY, this.field_146295_m - h - 2);
   }

   private void closeContext() {
      this.ctxOpen = false;
      this.ctxTarget = -1;
   }

   public void func_146274_d() throws IOException {
      if (this.ctxOpen) {
         int mx = Mouse.getEventX() * this.field_146294_l / this.field_146297_k.field_71443_c;
         int my = this.field_146295_m - Mouse.getEventY() * this.field_146295_m / this.field_146297_k.field_71440_d - 1;
         if (!this.contextHit(mx, my) && this.accountList != null) {
            this.accountList.func_178039_p();
         }
      } else if (this.accountList != null) {
         this.accountList.func_178039_p();
      }

      super.func_146274_d();
   }

   protected void func_73864_a(int mouseX, int mouseY, int mouseButton) throws IOException {
      if (this.ctxOpen) {
         if (mouseButton == 0) {
            int item = this.contextItemAt(mouseX, mouseY);
            int target = this.ctxTarget;
            this.closeContext();
            if (item == 0) {
               this.togglePin(target);
            } else if (item == 1) {
               this.selectedAccount = target;
               this.func_73876_c();
               this.doLogin();
            }
         } else {
            this.closeContext();
         }
      } else {
         super.func_73864_a(mouseX, mouseY, mouseButton);
         if (mouseButton == 1 && this.accountList != null && mouseY >= this.accountList.getTopY() && mouseY <= this.accountList.getBottomY()) {
            int idx = this.accountList.slotForDrag(mouseY);
            if (idx >= 0 && idx < UniversalAccountManager.accounts.size()) {
               this.selectedAccount = idx;
               this.openContext(mouseX, mouseY, idx);
               this.func_73876_c();
            }
         }
      }
   }

   protected void func_73869_a(char typedChar, int keyCode) {
      switch (keyCode) {
         case 1:
            this.func_146284_a(this.cancelButton);
            break;
         case 28:
            this.doLogin();
            break;
         case 200:
            if (this.selectedAccount > 0) {
               if (func_146271_m()) {
                  if (this.canMoveUp(this.selectedAccount)) {
                     Collections.swap(UniversalAccountManager.accounts, this.selectedAccount, this.selectedAccount - 1);
                     this.selectedAccount--;
                     UniversalAccountManager.save();
                  }
               } else {
                  this.selectedAccount--;
               }
            }
            break;
         case 208:
            if (this.selectedAccount < UniversalAccountManager.accounts.size() - 1) {
               if (func_146271_m()) {
                  if (this.canMoveDown(this.selectedAccount)) {
                     Collections.swap(UniversalAccountManager.accounts, this.selectedAccount, this.selectedAccount + 1);
                     this.selectedAccount++;
                     UniversalAccountManager.save();
                  }
               } else {
                  this.selectedAccount++;
               }
            }
            break;
         case 211:
            this.func_146284_a(this.deleteButton);
      }

      if (func_175280_f(keyCode) && this.selectedAccount >= 0 && this.selectedAccount < UniversalAccountManager.accounts.size()) {
         func_146275_d(UniversalAccountManager.accounts.get(this.selectedAccount).getUsername());
      }
   }

   private boolean canMoveUp(int idx) {
      if (idx <= 0) {
         return false;
      } else {
         Account a = UniversalAccountManager.accounts.get(idx);
         int floor = UniversalAccountManager.firstUnpinnedIndex();
         return a.isPinned() ? idx > 0 : idx > floor;
      }
   }

   private boolean canMoveDown(int idx) {
      if (idx >= UniversalAccountManager.accounts.size() - 1) {
         return false;
      } else {
         Account a = UniversalAccountManager.accounts.get(idx);
         int floor = UniversalAccountManager.firstUnpinnedIndex();
         return a.isPinned() ? idx < floor - 1 : true;
      }
   }

   protected void func_146273_a(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      super.func_146273_a(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
      if (clickedMouseButton == 0 && this.dragSource >= 0) {
         if (Math.abs(mouseY - this.dragMouseY) > 3) {
            this.dragMoved = true;
         }

         int target = this.accountList.slotForDrag(mouseY);
         if (target >= 0) {
            Account src = UniversalAccountManager.accounts.get(this.dragSource);
            int floor = UniversalAccountManager.firstUnpinnedIndex();
            if (src.isPinned()) {
               if (target >= floor) {
                  target = Math.max(0, floor - 1);
               }
            } else if (target < floor) {
               target = floor;
            }

            this.dragTarget = target;
         }
      }
   }

   protected void func_146286_b(int mouseX, int mouseY, int state) {
      super.func_146286_b(mouseX, mouseY, state);
      if (state == 0 && this.dragSource >= 0 && this.dragMoved && this.dragTarget >= 0 && this.dragSource != this.dragTarget) {
         Account moving = UniversalAccountManager.accounts.remove(this.dragSource);
         int insert = this.dragTarget;
         if (insert > UniversalAccountManager.accounts.size()) {
            insert = UniversalAccountManager.accounts.size();
         }

         UniversalAccountManager.accounts.add(insert, moving);
         this.selectedAccount = UniversalAccountManager.accounts.indexOf(moving);
         UniversalAccountManager.save();
         this.func_73876_c();
      }

      this.dragSource = -1;
      this.dragTarget = -1;
      this.dragMoved = false;
   }

   protected void func_146284_a(GuiButton button) {
      if (button != null && button.field_146124_l) {
         switch (button.field_146127_k) {
            case 0:
               this.doLogin();
               break;
            case 1:
               this.field_146297_k.func_147108_a(new GuiAddAccount(this.previousScreen));
               break;
            case 2:
               this.doDelete();
               break;
            case 3:
               this.field_146297_k.func_147108_a(this.previousScreen);
               break;
            case 4:
            case 7:
            default:
               if (this.accountList != null) {
                  this.accountList.func_148147_a(button);
               }
               break;
            case 5:
               this.field_146297_k.func_147108_a(new GuiChangeSkin(this));
               break;
            case 6:
               this.field_146297_k.func_147108_a(new GuiChangeUsername(this));
               break;
            case 8:
               this.doClearExpired();
               break;
            case 9:
               this.doSaveLauncher();
               break;
            case 10:
               this.field_146297_k.func_147108_a(new GuiForceSkin(this));
         }
      }
   }

   private void togglePin(int idx) {
      if (idx >= 0 && idx < UniversalAccountManager.accounts.size()) {
         Account acc = UniversalAccountManager.accounts.get(idx);
         acc.setPinned(!acc.isPinned());
         UniversalAccountManager.resort();
         this.selectedAccount = UniversalAccountManager.accounts.indexOf(acc);
         UniversalAccountManager.save();
         this.func_73876_c();
      }
   }

   private void doClearExpired() {
      int before = UniversalAccountManager.accounts.size();
      UniversalAccountManager.accounts.removeIf(a -> Boolean.FALSE.equals(a.getAvailable()));
      int removed = before - UniversalAccountManager.accounts.size();
      if (removed > 0) {
         UniversalAccountManager.save();
         this.selectedAccount = -1;
         this.notification = new Notification(
            TextFormatting.translate(String.format("&aRemoved %d expired account%s.&r", removed, removed == 1 ? "" : "s")), 4000L
         );
      } else {
         this.notification = new Notification(TextFormatting.translate("&7No expired accounts to remove.&r"), 3000L);
      }

      this.func_73876_c();
   }

   private void doSaveLauncher() {
      Account active = this.activeAccount();
      if (active != null && active.isLauncher()) {
         active.setType("token");
         UniversalAccountManager.save();
         this.notification = new Notification(TextFormatting.translate(String.format("&aSaved &b%s&a as a stored account.&r", active.getUsername())), 4000L);
         this.func_73876_c();
      }
   }

   private void doDelete() {
      if (this.selectedAccount >= 0 && this.selectedAccount < UniversalAccountManager.accounts.size()) {
         Account acc = UniversalAccountManager.accounts.get(this.selectedAccount);
         String username = StringUtils.isBlank(acc.getUsername()) ? "this account" : acc.getUsername();
         this.pendingDeleteIndex = this.selectedAccount;
         this.field_146297_k.func_147108_a(new GuiYesNo(this, "Delete \"" + username + "\"?", "This cannot be undone.", "Delete", "Cancel", 100));
      }
   }

   public void func_73878_a(boolean result, int id) {
      if (id == 100) {
         int idx = this.pendingDeleteIndex;
         this.pendingDeleteIndex = -1;
         if (result && idx >= 0 && idx < UniversalAccountManager.accounts.size()) {
            UniversalAccountManager.accounts.remove(idx);
            UniversalAccountManager.save();
            this.selectedAccount = -1;
         }

         this.field_146297_k.func_147108_a(this);
         this.func_73876_c();
      } else {
         super.func_73878_a(result, id);
      }
   }

   private void doLogin() {
      if (this.task == null || this.task.isDone()) {
         if (this.selectedAccount >= 0 && this.selectedAccount < UniversalAccountManager.accounts.size()) {
            if (this.executor == null) {
               this.executor = Executors.newSingleThreadExecutor();
            }

            Account account = UniversalAccountManager.accounts.get(this.selectedAccount);
            String username = StringUtils.isBlank(account.getUsername()) ? "???" : account.getUsername();
            if (!account.isToken() && !account.isCookie()) {
               AtomicReference<String> refreshToken = new AtomicReference<>("");
               AtomicReference<String> accessToken = new AtomicReference<>("");
               this.notification = new Notification(TextFormatting.translate(String.format("&7Fetching profile... (%s)&r", username)), -1L);
               this.task = MicrosoftAuth.login(account.getAccessToken(), this.executor).handle((session, error) -> {
                  if (session != null) {
                     account.setUsername(session.func_111285_a());
                     account.setUuid(session.func_148255_b());
                     UniversalAccountManager.save();
                     SessionManager.set(session);
                     this.notification = new Notification(TextFormatting.translate(String.format("&aLogged in! (%s)&r", account.getUsername())), 5000L);
                     return true;
                  } else {
                     return false;
                  }
               }).thenComposeAsync(completed -> {
                  if (completed) {
                     throw new NoSuchElementException();
                  } else {
                     this.notification = new Notification(TextFormatting.translate(String.format("&7Refreshing tokens... (%s)&r", username)), -1L);
                     return MicrosoftAuth.refreshMSAccessTokens(account.getRefreshToken(), this.executor);
                  }
               }).thenComposeAsync(t -> {
                  this.notification = new Notification(TextFormatting.translate(String.format("&7Acquiring Xbox token... (%s)&r", username)), -1L);
                  refreshToken.set(t.get("refresh_token"));
                  return MicrosoftAuth.acquireXboxAccessToken(t.get("access_token"), this.executor);
               }).thenComposeAsync(x -> {
                  this.notification = new Notification(TextFormatting.translate(String.format("&7Acquiring XSTS token... (%s)&r", username)), -1L);
                  return MicrosoftAuth.acquireXboxXstsToken(x, this.executor);
               }).thenComposeAsync(x -> {
                  this.notification = new Notification(TextFormatting.translate(String.format("&7Acquiring MC token... (%s)&r", username)), -1L);
                  return MicrosoftAuth.acquireMCAccessToken(x.get("Token"), x.get("uhs"), this.executor);
               }).thenComposeAsync(t -> {
                  this.notification = new Notification(TextFormatting.translate(String.format("&7Fetching profile... (%s)&r", username)), -1L);
                  accessToken.set(t);
                  return MicrosoftAuth.login(t, this.executor);
               }).thenAccept(session -> {
                  account.setRefreshToken(refreshToken.get());
                  account.setAccessToken(accessToken.get());
                  account.setUsername(session.func_111285_a());
                  account.setUuid(session.func_148255_b());
                  UniversalAccountManager.save();
                  SessionManager.set(session);
                  this.notification = new Notification(TextFormatting.translate(String.format("&aLogged in! (%s)&r", account.getUsername())), 5000L);
               }).exceptionally(err -> {
                  if (!(err.getCause() instanceof NoSuchElementException)) {
                     this.notification = new Notification(TextFormatting.translate(String.format("&c%s (%s)&r", err.getMessage(), username)), 5000L);
                  }

                  return null;
               });
            } else {
               this.notification = new Notification(TextFormatting.translate(String.format("&7Validating token... (%s)&r", username)), -1L);
               this.task = TokenAuth.login(account.getAccessToken(), this.executor)
                  .thenAccept(session -> {
                     account.setUsername(session.func_111285_a());
                     account.setUuid(session.func_148255_b());
                     UniversalAccountManager.save();
                     SessionManager.set(session);
                     this.notification = new Notification(TextFormatting.translate(String.format("&aLogged in! (%s)&r", account.getUsername())), 5000L);
                  })
                  .exceptionally(
                     err -> {
                        this.notification = new Notification(
                           TextFormatting.translate(String.format("&cToken rejected (%s) — re-add this account.&r", username)), 5000L
                        );
                        return null;
                     }
                  );
            }
         }
      }
   }

   class GuiAccountList extends GuiSlot {
      public GuiAccountList(Minecraft mc) {
         super(mc, GuiAccountManager.this.field_146294_l, GuiAccountManager.this.field_146295_m, 46, GuiAccountManager.this.field_146295_m - 88, 28);
      }

      protected int func_148127_b() {
         return UniversalAccountManager.accounts.size();
      }

      protected boolean func_148131_a(int slot) {
         return slot == GuiAccountManager.this.selectedAccount;
      }

      protected int func_148137_d() {
         return (this.field_148155_a + this.func_148139_c()) / 2 + 2;
      }

      public int func_148139_c() {
         return 360;
      }

      protected int func_148138_e() {
         return UniversalAccountManager.accounts.size() * 28;
      }

      protected void func_148123_a() {
         GuiAccountManager.this.func_146276_q_();
      }

      int getTopY() {
         return this.field_148153_b;
      }

      int getBottomY() {
         return this.field_148154_c;
      }

      int slotForDrag(int mouseY) {
         int idx = this.func_148124_c(GuiAccountManager.this.field_146294_l / 2, mouseY);
         if (idx >= 0) {
            return idx;
         } else {
            int size = UniversalAccountManager.accounts.size();
            if (size == 0) {
               return -1;
            } else {
               return mouseY < this.field_148153_b ? 0 : size - 1;
            }
         }
      }

      protected void func_148144_a(int slot, boolean dbl, int mx, int my) {
         GuiAccountManager.this.selectedAccount = slot;
         if (slot >= 0 && slot < UniversalAccountManager.accounts.size()) {
            GuiAccountManager.this.dragSource = slot;
            GuiAccountManager.this.dragTarget = slot;
            GuiAccountManager.this.dragMouseY = my;
            GuiAccountManager.this.dragMoved = false;
         }

         GuiAccountManager.this.func_73876_c();
         if (dbl) {
            GuiAccountManager.this.doLogin();
         }
      }

      protected void func_180791_a(int id, int x, int y, int k, int mx, int my) {
         if (GuiAccountManager.this.dragSource != id || !GuiAccountManager.this.dragMoved) {
            this.drawAccountRow(x, y, id);
         }
      }

      void drawAccountRow(int x, int y, int id) {
         FontRenderer fr = GuiAccountManager.this.field_146289_q;
         Account acc = UniversalAccountManager.accounts.get(id);
         Session active = SessionManager.get();
         boolean unavailable = Boolean.FALSE.equals(acc.getAvailable());
         int headSize = 24;
         int headX = x + 2;
         int headY = y + 1;
         ResourceLocation head = SkinHeadCache.get(acc.getUsername(), acc.getUuid());
         if (head != null) {
            if (unavailable) {
               GlStateManager.func_179131_c(0.45F, 0.45F, 0.45F, 1.0F);
            } else {
               GlStateManager.func_179131_c(1.0F, 1.0F, 1.0F, 1.0F);
            }

            this.field_148161_k.func_110434_K().func_110577_a(head);
            Gui.func_152125_a(headX, headY, 0.0F, 0.0F, 64, 64, headSize, headSize, 64.0F, 64.0F);
            GlStateManager.func_179131_c(1.0F, 1.0F, 1.0F, 1.0F);
         } else {
            Gui.func_73734_a(headX, headY, headX + headSize, headY + headSize, -14540254);
            Gui.func_73734_a(headX + 1, headY + 1, headX + headSize - 1, headY + headSize - 1, -12961222);
            GuiAccountManager.this.func_73732_a(fr, "?", headX + headSize / 2, headY + headSize / 2 - 4, -7829368);
         }

         String username = acc.getUsername();
         if (StringUtils.isBlank(username)) {
            username = "&7&l?";
         } else if (unavailable) {
            username = "&8&m" + username + "&r";
         } else if (acc.getAccessToken().equals(active.func_148254_d())) {
            username = "&a&l" + username;
         } else if (username.equals(active.func_111285_a())) {
            username = "&a" + username;
         }

         String pinPrefix = acc.isPinned() ? "&e★&r " : "";
         String usernameRendered = TextFormatting.translate(pinPrefix + "&r" + username + "&r");
         GuiAccountManager.this.func_73731_b(fr, usernameRendered, headX + headSize + 6, y + 4, -1);
         String typeBadge;
         if (acc.isLauncher()) {
            typeBadge = "&8[&fLAUNCHER&8]&r";
         } else if (acc.isToken()) {
            typeBadge = "&8[&dTOKEN&8]&r";
         } else if (acc.isCookie()) {
            typeBadge = "&8[&eCOOKIE&8]&r";
         } else {
            typeBadge = "&8[&bMS&8]&r";
         }

         GuiAccountManager.this.func_73731_b(fr, TextFormatting.translate(typeBadge), headX + headSize + 6 + fr.func_78256_a(usernameRendered) + 6, y + 4, -1);
         String banRendered = TextFormatting.translate(HypixelBanCheck.renderStatus(acc));
         int banWidth = fr.func_78256_a(banRendered);
         GuiAccountManager.this.func_73731_b(fr, banRendered, x + this.func_148139_c() - 14 - banWidth, y + 4, -1);
         String uuid = acc.getUuid();
         if (uuid != null && uuid.length() >= 8) {
            String uuidShort = "&8uuid: " + uuid.substring(0, 8) + "&r";
            GuiAccountManager.this.func_73731_b(fr, TextFormatting.translate(uuidShort), headX + headSize + 6, y + 16, -1);
         }
      }
   }
}
