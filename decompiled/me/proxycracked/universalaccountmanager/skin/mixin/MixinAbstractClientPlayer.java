package me.proxycracked.universalaccountmanager.skin.mixin;

import me.proxycracked.universalaccountmanager.skin.ForceSkinLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractClientPlayer.class})
public class MixinAbstractClientPlayer {
   @Inject(
      method = {"getLocationSkin"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void uam$forceSkin$getLocationSkin(CallbackInfoReturnable<ResourceLocation> cir) {
      if (ForceSkinLoader.hasSkin()) {
         if (this == Minecraft.func_71410_x().field_71439_g) {
            ResourceLocation loc = ForceSkinLoader.getSkinLocation();
            if (loc != null) {
               cir.setReturnValue(loc);
            }
         }
      }
   }

   @Inject(
      method = {"getSkinType"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void uam$forceSkin$getSkinType(CallbackInfoReturnable<String> cir) {
      if (ForceSkinLoader.hasSkin()) {
         if (this == Minecraft.func_71410_x().field_71439_g) {
            cir.setReturnValue(ForceSkinLoader.isSlim() ? "slim" : "default");
         }
      }
   }
}
