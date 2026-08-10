package me.proxycracked.universalaccountmanager.mixin;

import java.util.Iterator;
import java.util.List;
import me.proxycracked.universalaccountmanager.auth.ExpiredAccountCleaner;
import me.proxycracked.universalaccountmanager.gui.GuiAccountManager;
import me.proxycracked.universalaccountmanager.gui.GuiAccountStores;
import me.proxycracked.universalaccountmanager.gui.GuiChanger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiAccountManager.class, remap = false)
public abstract class MixinGuiAccountManager {
    @Inject(method = "func_73866_w_", at = @At("RETURN"), remap = false)
    private void replaceForceSkinWithStore(CallbackInfo callback) {
        GuiScreen screen = (GuiScreen) (Object) this;
        List<GuiButton> buttons = ObfuscationReflectionHelper.getPrivateValue(GuiScreen.class, screen, "buttonList", "field_146292_n");
        GuiButton forceSkin = null;
        GuiButton addAccount = null;
        GuiButton deleteAccount = null;
        GuiButton cancel = null;
        Iterator<GuiButton> iterator = buttons.iterator();
        while (iterator.hasNext()) {
            GuiButton button = iterator.next();
            if (button.id == 10) {
                forceSkin = button;
                iterator.remove();
            } else if (button.id == 6 || button.id == 8 || button.id == 9) {
                iterator.remove();
            } else if (button.id == 5) {
                button.displayString = "Changer";
                button.xPosition = 4;
                button.yPosition = 4;
                button.width = 100;
            } else if (button.id == 1) {
                addAccount = button;
            } else if (button.id == 2) {
                deleteAccount = button;
            } else if (button.id == 3) {
                cancel = button;
            }
        }
        if (addAccount != null && deleteAccount != null && cancel != null) {
            int rowY = addAccount.yPosition;
            deleteAccount.xPosition = screen.width / 2 - 154;
            addAccount.xPosition = screen.width / 2 - 50;
            cancel.xPosition = screen.width / 2 + 54;
            deleteAccount.yPosition = rowY;
            cancel.yPosition = rowY;
            deleteAccount.width = 100;
            addAccount.width = 100;
            cancel.width = 100;
        }
        if (forceSkin != null) {
            buttons.add(new GuiButton(11, screen.width - 104, 4, 100, 20, "Buy Accounts"));
        }
        ExpiredAccountCleaner.schedule();
    }

    @Redirect(
        method = "func_73863_a",
        at = @At(
            value = "INVOKE",
            target = "Lme/proxycracked/universalaccountmanager/gui/GuiAccountManager;func_73731_b(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V",
            ordinal = 0
        ),
        remap = false
    )
    private void moveActiveUsername(GuiAccountManager screen, FontRenderer font, String text, int x, int y, int color) {
        font.drawStringWithShadow(text, 5.0F, 28.0F, color);
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void openAccountStores(GuiButton button, CallbackInfo callback) {
        if (button != null && button.id == 5 && button.enabled) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiChanger((GuiScreen) (Object) this));
            callback.cancel();
        } else if (button != null && button.id == 11 && button.enabled) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiAccountStores((GuiScreen) (Object) this));
            callback.cancel();
        }
    }
}
