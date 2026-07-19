package com.yyon.grapplinghook.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yyon.grapplinghook.client.LeadOverhaul;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobRenderer.class)
public class MobRendererMixin {

    @Inject(method = "renderLeash", at = @At("HEAD"), cancellable = true)
    private void grapplemod$overhaulLeash(Mob mob, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, Entity leashHolder, CallbackInfo ci) {
        if (LeadOverhaul.renderLeash(mob, partialTicks, poseStack, buffers, leashHolder)) {
            ci.cancel();
        }
    }
}
