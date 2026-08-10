package me.proxycracked.universalaccountmanager.mixin;

import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.AccountValidator;
import me.proxycracked.universalaccountmanager.auth.ExpiredAccountCleaner;
import me.proxycracked.universalaccountmanager.localts.StartupAccountRefresher;
import me.proxycracked.universalaccountmanager.skin.SkinFavoritesManager;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UniversalAccountManager.class, remap = false)
public abstract class MixinUniversalAccountManager {
    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lme/proxycracked/universalaccountmanager/skin/ForceSkinLoader;init()V"), remap = false)
    private static void disableForceSkin() {
    }

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private static void refreshStoredAccounts(FMLInitializationEvent event, CallbackInfo callback) {
        SkinFavoritesManager.initializeAccountTracking();
        StartupAccountRefresher.start();
        AccountValidator.validateAll();
        ExpiredAccountCleaner.schedule();
    }

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private static void applyDefaultSkinToNewAccounts(CallbackInfo callback) {
        SkinFavoritesManager.onAccountsSaved();
    }
}
