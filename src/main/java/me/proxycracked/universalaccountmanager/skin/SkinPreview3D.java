package me.proxycracked.universalaccountmanager.skin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;

public final class SkinPreview3D {
    private static final ModelPlayer MODEL_CLASSIC = new ModelPlayer(0.0F, false);
    private static final ModelPlayer MODEL_SLIM = new ModelPlayer(0.0F, true);
    private static final float UNIT = 0.0625F;

    private SkinPreview3D() {
    }

    public static void draw(int posX, int posY, int scale, float bodyYaw, float headPitch,
                            ResourceLocation skin, boolean slim) {
        if (skin == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        ModelPlayer model = slim ? MODEL_SLIM : MODEL_CLASSIC;
        resetPose(model, headPitch);

        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translate(posX, posY, 50.0F);
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.rotate(135.0F, 0.0F, 1.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(bodyYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(0.0F, -1.5F, 0.0F);
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        minecraft.getTextureManager().bindTexture(skin);

        drawBaseLayer(model);
        drawSecondLayer(model);

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static void drawBaseLayer(ModelPlayer model) {
        model.bipedHead.render(UNIT);
        model.bipedBody.render(UNIT);
        model.bipedRightArm.render(UNIT);
        model.bipedLeftArm.render(UNIT);
        model.bipedRightLeg.render(UNIT);
        model.bipedLeftLeg.render(UNIT);
    }

    private static void drawSecondLayer(ModelPlayer model) {
        model.bipedHeadwear.render(UNIT);
        model.bipedBodyWear.render(UNIT);
        model.bipedRightArmwear.render(UNIT);
        model.bipedLeftArmwear.render(UNIT);
        model.bipedRightLegwear.render(UNIT);
        model.bipedLeftLegwear.render(UNIT);
    }

    private static void resetPose(ModelPlayer model, float headPitch) {
        reset(model.bipedHead);
        reset(model.bipedBody);
        reset(model.bipedRightArm);
        reset(model.bipedLeftArm);
        reset(model.bipedRightLeg);
        reset(model.bipedLeftLeg);
        model.bipedHead.rotateAngleX = (float) Math.toRadians(headPitch);

        sync(model.bipedHead, model.bipedHeadwear);
        sync(model.bipedBody, model.bipedBodyWear);
        sync(model.bipedRightArm, model.bipedRightArmwear);
        sync(model.bipedLeftArm, model.bipedLeftArmwear);
        sync(model.bipedRightLeg, model.bipedRightLegwear);
        sync(model.bipedLeftLeg, model.bipedLeftLegwear);
        model.isSneak = false;
        model.isRiding = false;
        model.aimedBow = false;
    }

    private static void sync(ModelRenderer base, ModelRenderer overlay) {
        ModelBase.copyModelAngles(base, overlay);
        base.showModel = true;
        base.isHidden = false;
        overlay.showModel = true;
        overlay.isHidden = false;
    }

    private static void reset(ModelRenderer part) {
        part.rotateAngleX = 0.0F;
        part.rotateAngleY = 0.0F;
        part.rotateAngleZ = 0.0F;
        part.showModel = true;
        part.isHidden = false;
    }
}
