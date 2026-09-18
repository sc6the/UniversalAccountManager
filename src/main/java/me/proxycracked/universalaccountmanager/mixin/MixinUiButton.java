package me.proxycracked.universalaccountmanager.mixin;

import me.proxycracked.universalaccountmanager.gui.theme.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiButton.class, remap = false)
public abstract class MixinUiButton {
    @Inject(method = "func_146112_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void uam$draw(Minecraft mc, int x, int y, CallbackInfo ci) {
        // Legacy deliberately falls through to GuiButton's native widgets.png renderer.
        // Minecraft then handles the active resource pack and all three button states.
        if (UiTheme.owns(mc.currentScreen) && UiTheme.get().modern) {
            UiTheme.button((GuiButton) (Object) this, mc, x, y);
            ci.cancel();
        }
    }
}
