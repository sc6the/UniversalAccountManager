package me.proxycracked.universalaccountmanager.mixin;

import me.proxycracked.universalaccountmanager.gui.theme.UiTheme;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiScreen.class, remap = false)
public abstract class MixinUiScreen {
    @Inject(method = "func_146276_q_", at = @At("HEAD"), cancellable = true, remap = false)
    private void uam$background(CallbackInfo ci) {
        GuiScreen screen = (GuiScreen) (Object) this;
        if (UiTheme.owns(screen) && UiTheme.get().modern) {
            UiTheme.background(screen.width, screen.height);
            ci.cancel();
        }
    }
}
