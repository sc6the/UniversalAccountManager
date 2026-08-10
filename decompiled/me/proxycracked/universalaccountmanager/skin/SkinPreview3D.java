package me.proxycracked.universalaccountmanager.skin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
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

   public static void draw(int posX, int posY, int scale, float bodyYaw, float headPitch, ResourceLocation skin, boolean slim) {
      if (skin != null) {
         Minecraft mc = Minecraft.func_71410_x();
         ModelPlayer model = slim ? MODEL_SLIM : MODEL_CLASSIC;
         resetPose(model);
         model.field_78116_c.field_78795_f = (float)Math.toRadians((double)headPitch);
         model.field_178720_f.field_78795_f = model.field_78116_c.field_78795_f;
         GlStateManager.func_179142_g();
         GlStateManager.func_179094_E();
         GlStateManager.func_179109_b((float)posX, (float)posY, 50.0F);
         GlStateManager.func_179152_a((float)scale, (float)scale, (float)scale);
         GlStateManager.func_179114_b(135.0F, 0.0F, 1.0F, 0.0F);
         RenderHelper.func_74519_b();
         GlStateManager.func_179114_b(-135.0F, 0.0F, 1.0F, 0.0F);
         GlStateManager.func_179114_b(bodyYaw, 0.0F, 1.0F, 0.0F);
         GlStateManager.func_179109_b(0.0F, -1.5F, 0.0F);
         GlStateManager.func_179126_j();
         GlStateManager.func_179147_l();
         GlStateManager.func_179120_a(770, 771, 1, 0);
         GlStateManager.func_179131_c(1.0F, 1.0F, 1.0F, 1.0F);
         mc.func_110434_K().func_110577_a(skin);
         model.field_78116_c.func_78785_a(0.0625F);
         model.field_78115_e.func_78785_a(0.0625F);
         model.field_178723_h.func_78785_a(0.0625F);
         model.field_178724_i.func_78785_a(0.0625F);
         model.field_178721_j.func_78785_a(0.0625F);
         model.field_178722_k.func_78785_a(0.0625F);
         model.field_178720_f.func_78785_a(0.0625F);
         model.field_178730_v.func_78785_a(0.0625F);
         model.field_178732_b.func_78785_a(0.0625F);
         model.field_178734_a.func_78785_a(0.0625F);
         model.field_178731_d.func_78785_a(0.0625F);
         model.field_178733_c.func_78785_a(0.0625F);
         GlStateManager.func_179084_k();
         GlStateManager.func_179121_F();
         RenderHelper.func_74518_a();
         GlStateManager.func_179101_C();
         GlStateManager.func_179138_g(OpenGlHelper.field_77476_b);
         GlStateManager.func_179090_x();
         GlStateManager.func_179138_g(OpenGlHelper.field_77478_a);
      }
   }

   private static void resetPose(ModelPlayer m) {
      m.field_78116_c.field_78795_f = m.field_78116_c.field_78796_g = m.field_78116_c.field_78808_h = 0.0F;
      m.field_178720_f.field_78795_f = m.field_178720_f.field_78796_g = m.field_178720_f.field_78808_h = 0.0F;
      m.field_78115_e.field_78795_f = m.field_78115_e.field_78796_g = m.field_78115_e.field_78808_h = 0.0F;
      m.field_178723_h.field_78795_f = m.field_178723_h.field_78796_g = m.field_178723_h.field_78808_h = 0.0F;
      m.field_178724_i.field_78795_f = m.field_178724_i.field_78796_g = m.field_178724_i.field_78808_h = 0.0F;
      m.field_178721_j.field_78795_f = m.field_178721_j.field_78796_g = m.field_178721_j.field_78808_h = 0.0F;
      m.field_178722_k.field_78795_f = m.field_178722_k.field_78796_g = m.field_178722_k.field_78808_h = 0.0F;
      m.field_178730_v.field_78795_f = m.field_178730_v.field_78796_g = m.field_178730_v.field_78808_h = 0.0F;
      m.field_178732_b.field_78795_f = m.field_178732_b.field_78796_g = m.field_178732_b.field_78808_h = 0.0F;
      m.field_178734_a.field_78795_f = m.field_178734_a.field_78796_g = m.field_178734_a.field_78808_h = 0.0F;
      m.field_178731_d.field_78795_f = m.field_178731_d.field_78796_g = m.field_178731_d.field_78808_h = 0.0F;
      m.field_178733_c.field_78795_f = m.field_178733_c.field_78796_g = m.field_178733_c.field_78808_h = 0.0F;
      m.field_78117_n = false;
      m.field_78091_s = false;
      m.field_78093_q = false;
   }
}
