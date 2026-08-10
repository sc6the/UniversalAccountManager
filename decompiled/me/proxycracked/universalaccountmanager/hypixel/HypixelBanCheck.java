package me.proxycracked.universalaccountmanager.hypixel;

import me.proxycracked.universalaccountmanager.auth.Account;

public final class HypixelBanCheck {
   private HypixelBanCheck() {
   }

   public static HypixelBanCheck.Result evaluate(Account account) {
      long unban = account.getUnban();
      long now = System.currentTimeMillis();
      if (unban < 0L) {
         return new HypixelBanCheck.Result(HypixelBanCheck.Status.PERMANENT, 0L);
      } else if (unban == 0L) {
         return new HypixelBanCheck.Result(HypixelBanCheck.Status.CLEAN, 0L);
      } else {
         return unban <= now
            ? new HypixelBanCheck.Result(HypixelBanCheck.Status.CLEAN, 0L)
            : new HypixelBanCheck.Result(HypixelBanCheck.Status.TEMP, unban - now);
      }
   }

   public static String formatRemaining(long diff) {
      long s = diff / 1000L % 60L;
      long m = diff / 60000L % 60L;
      long h = diff / 3600000L % 24L;
      long d = diff / 86400000L;
      StringBuilder sb = new StringBuilder();
      if (d > 0L) {
         sb.append(d).append('d').append(' ');
      }

      if (h > 0L) {
         sb.append(h).append('h').append(' ');
      }

      if (m > 0L) {
         sb.append(m).append('m').append(' ');
      }

      if (s > 0L || sb.length() == 0) {
         sb.append(s).append('s');
      }

      return sb.toString().trim();
   }

   public static String renderStatus(Account account) {
      if (Boolean.FALSE.equals(account.getAvailable())) {
         return "&7&lBAN STATUS NOT AVAILABLE";
      } else {
         HypixelBanCheck.Result r = evaluate(account);
         switch (r.status) {
            case PERMANENT:
               return "&4&lPERMA BANNED";
            case TEMP:
               return "&c&lBANNED &7(" + formatRemaining(r.remainingMs) + ")";
            case CLEAN:
               return "&a&lNO BAN";
            default:
               return "&7&lUNKNOWN";
         }
      }
   }

   public static class Result {
      public final HypixelBanCheck.Status status;
      public final long remainingMs;

      public Result(HypixelBanCheck.Status s, long r) {
         this.status = s;
         this.remainingMs = r;
      }
   }

   public static enum Status {
      UNKNOWN,
      CLEAN,
      TEMP,
      PERMANENT;
   }
}
