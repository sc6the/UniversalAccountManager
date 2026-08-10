package me.proxycracked.universalaccountmanager.auth;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import me.proxycracked.universalaccountmanager.UniversalAccountManager;

public final class AccountValidator {
   private static final ExecutorService EXEC = Executors.newFixedThreadPool(3, new ThreadFactory() {
      private final AtomicInteger n = new AtomicInteger(0);

      @Override
      public Thread newThread(Runnable r) {
         Thread t = new Thread(r, "UniversalAccountManager-Validate-" + this.n.incrementAndGet());
         t.setDaemon(true);
         return t;
      }
   });

   private AccountValidator() {
   }

   public static void validateAll() {
      for (Account acc : UniversalAccountManager.accounts) {
         validate(acc);
      }
   }

   public static void validate(Account account) {
      if (account != null && account.getAccessToken() != null && !account.getAccessToken().isEmpty()) {
         EXEC.submit(() -> {
            try {
               boolean ok = TokenAuth.validate(account.getAccessToken());
               if (ok) {
                  account.setAvailable(Boolean.TRUE);
               } else if (!account.isToken() && !account.isCookie()) {
                  account.setAvailable(Boolean.TRUE);
               } else {
                  account.setAvailable(Boolean.FALSE);
               }
            } catch (Exception var2) {
               if (account.getAvailable() == null) {
                  account.setAvailable(Boolean.TRUE);
               }
            }
         });
      } else {
         account.setAvailable(Boolean.FALSE);
      }
   }
}
