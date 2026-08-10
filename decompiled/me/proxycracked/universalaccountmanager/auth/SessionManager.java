package me.proxycracked.universalaccountmanager.auth;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class SessionManager {
   private static final Minecraft mc = Minecraft.func_71410_x();
   private static Field sessionField = null;

   private static Field getField() {
      if (sessionField == null) {
         try {
            sessionField = ReflectionHelper.findField(Minecraft.class, new String[]{"session", "field_71449_j"});
            sessionField.setAccessible(true);
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(sessionField, sessionField.getModifiers() & -17);
         } catch (Exception var6) {
            try {
               for (Field f : Minecraft.class.getDeclaredFields()) {
                  if (f.getType().isAssignableFrom(Session.class)) {
                     sessionField = f;
                     sessionField.setAccessible(true);
                     break;
                  }
               }
            } catch (Exception var5) {
               sessionField = null;
            }
         }
      }

      return sessionField;
   }

   public static Session get() {
      return mc.func_110432_I();
   }

   public static void set(Session session) {
      try {
         Field f = getField();
         if (f != null) {
            f.set(mc, session);
         }
      } catch (Exception var2) {
      }
   }
}
