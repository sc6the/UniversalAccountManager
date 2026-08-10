package me.proxycracked.universalaccountmanager.utils;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;

public final class SystemUtils {
   private SystemUtils() {
   }

   public static void openWebLink(URI url) {
      if (url != null) {
         try {
            Class<?> desktop = Class.forName("java.awt.Desktop");
            Object o = desktop.getMethod("getDesktop").invoke(null);
            desktop.getMethod("browse", URI.class).invoke(o, url);
         } catch (Exception var3) {
         }
      }
   }

   public static void setClipboard(String text) {
      try {
         Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
      } catch (Exception var2) {
      }
   }
}
