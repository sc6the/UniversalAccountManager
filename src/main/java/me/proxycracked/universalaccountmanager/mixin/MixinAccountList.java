package me.proxycracked.universalaccountmanager.mixin;

import me.proxycracked.universalaccountmanager.gui.AccountRowRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands account rows to {@link AccountRowRenderer} so the added account types get their own badge.
 *
 * <p>Purely cosmetic, so it is optional: if the bundled list class ever changes shape the rows
 * simply keep rendering the way the jar draws them.</p>
 */
@Mixin(targets = "me.proxycracked.universalaccountmanager.gui.GuiAccountManager$GuiAccountList", remap = false)
public abstract class MixinAccountList {
    @Inject(method = "drawAccountRow", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void uam$drawRow(int x, int y, int id, CallbackInfo callback) {
        if (AccountRowRenderer.draw(x, y, id)) {
            callback.cancel();
        }
    }
}
