package me.proxycracked.universalaccountmanager.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Uses a free corner slot rather than displacing another mod's menu buttons. */
public final class AccountMenuEntry {
    private static final int ID = 0x55414D;

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.gui instanceof net.minecraft.client.gui.GuiMultiplayer) {
            // The bundled original Events listener adds this at normal priority.
            event.buttonList.removeIf(button -> button.id == 1791);
            return;
        }
        if (!(event.gui instanceof GuiMainMenu)) return;
        for (GuiButton b : event.buttonList) if (b.id == ID) return;
        for (int y = 6; y + 20 < event.gui.height; y += 24) {
            int x = event.gui.width - 108;
            boolean free = true;
            for (GuiButton b : event.buttonList) {
                if (b.visible && x < b.xPosition + b.width && x + 102 > b.xPosition
                    && y < b.yPosition + b.height && y + 20 > b.yPosition) free = false;
            }
            if (free) { event.buttonList.add(new GuiButton(ID, x, y, 102, 20, "Accounts")); return; }
        }
    }

    @SubscribeEvent
    public void click(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.gui instanceof GuiMainMenu && event.button.id == ID)
            Minecraft.getMinecraft().displayGuiScreen(new GuiAccountManager(event.gui));
    }
}
