package me.proxycracked.universalaccountmanager.mixin;

import me.proxycracked.universalaccountmanager.gui.theme.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Scope color mapping to UAM screens without changing the shared font or its palette. */
@Mixin(value = FontRenderer.class, remap = false)
public abstract class MixinUiFont {
    @Shadow private int[] field_78285_g;

    @ModifyVariable(method = "func_175065_a", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int uam$textColor(int color) {
        return UiTheme.owns(Minecraft.getMinecraft().currentScreen) ? UiTheme.get().textColor(color) : color;
    }

    @Redirect(method = "func_78255_a", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/gui/FontRenderer;field_78285_g:[I"), remap = false)
    private int[] uam$formattingColors(FontRenderer renderer) {
        return UiTheme.owns(Minecraft.getMinecraft().currentScreen) ? UiTheme.get().formattingColors() : field_78285_g;
    }
}
