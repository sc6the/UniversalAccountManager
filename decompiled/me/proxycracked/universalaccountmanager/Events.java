package me.proxycracked.universalaccountmanager;

import java.lang.reflect.Field;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.gui.GuiAccountManager;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent;
import net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent.Post;
import net.minecraftforge.event.world.WorldEvent.Load;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.commons.lang3.StringUtils;

public class Events {
   private static final Minecraft mc = Minecraft.func_71410_x();
   private static final int BTN_ID = 1791;

   @SubscribeEvent
   public void onRenderTick(RenderTickEvent event) {
      if (event.phase == Phase.END && mc.field_71462_r != null) {
         if (mc.field_71462_r instanceof GuiSelectWorld || mc.field_71462_r instanceof GuiMultiplayer) {
            String text = TextFormatting.translate(String.format("&7User: &b%s&r", SessionManager.get().func_111285_a()));
            GlStateManager.func_179140_f();
            mc.field_71462_r.func_73731_b(mc.field_71466_p, text, 3, 3, -1);
            GlStateManager.func_179145_e();
         }
      }
   }

   @SubscribeEvent
   public void initGuiEvent(Post event) {
      if (event.gui instanceof GuiSelectWorld || event.gui instanceof GuiMultiplayer) {
         event.buttonList.add(new GuiButton(1791, event.gui.field_146294_l - 106, 6, 100, 20, "Accounts"));
      }

      if (event.gui instanceof GuiDisconnected) {
         try {
            Field f = ReflectionHelper.findField(GuiDisconnected.class, new String[]{"message", "field_146304_f"});
            IChatComponent message = (IChatComponent)f.get(event.gui);
            String text = message.func_150254_d().split("\n\n")[0];
            if (text.equals("§r§cYou are permanently banned from this server!") || text.equals("§r§cYour account has been blocked.")) {
               UniversalAccountManager.load();

               for (Account account : UniversalAccountManager.accounts) {
                  if (mc.func_110432_I().func_111285_a().equals(account.getUsername())) {
                     account.setUnban(-1L);
                  }
               }

               UniversalAccountManager.save();
               return;
            }

            if (text.matches("§r§cYou are temporarily banned for §r§f.*§r§c from this server!")
               || text.matches("§r§cYour account is temporarily blocked for §r§f.*§r§c from this server!")) {
               String unban = StringUtils.substringBetween(text, "§r§f", "§r§c");
               if (unban != null) {
                  long time = System.currentTimeMillis();

                  for (String duration : unban.split(" ")) {
                     String type = duration.substring(duration.length() - 1);
                     long value = Long.parseLong(duration.substring(0, duration.length() - 1));
                     switch (type) {
                        case "d":
                           time += value * 86400000L;
                           break;
                        case "h":
                           time += value * 3600000L;
                           break;
                        case "m":
                           time += value * 60000L;
                           break;
                        case "s":
                           time += value * 1000L;
                     }
                  }

                  UniversalAccountManager.load();

                  for (Account accountx : UniversalAccountManager.accounts) {
                     if (mc.func_110432_I().func_111285_a().equals(accountx.getUsername())) {
                        accountx.setUnban(time);
                     }
                  }

                  UniversalAccountManager.save();
               }
            }
         } catch (Exception var17) {
         }
      }
   }

   @SubscribeEvent
   public void onClick(ActionPerformedEvent event) {
      if ((event.gui instanceof GuiSelectWorld || event.gui instanceof GuiMultiplayer) && event.button.field_146127_k == 1791) {
         mc.func_147108_a(new GuiAccountManager(event.gui));
      }
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      ServerData sd = mc.func_147104_D();
      if (sd != null) {
         String ip = sd.field_78845_b;
         if (ip.endsWith("hypixel.net") || ip.endsWith("hypixel.io")) {
            UniversalAccountManager.load();

            for (Account account : UniversalAccountManager.accounts) {
               if (mc.func_110432_I().func_111285_a().equals(account.getUsername())) {
                  account.setUnban(0L);
               }
            }

            UniversalAccountManager.save();
         }
      }
   }
}
