package com.yyon.grapplinghook.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yyon.grapplinghook.config.GrappleConfig;
import com.yyon.grapplinghook.entities.grapplehook.SegmentHandler;
import com.yyon.grapplinghook.utils.Vec;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bedrock-style lead visuals: replaces the vanilla straight leash line with the grapple mod's
 * sagging rope, including purely-visual wrapping around terrain corners (a client-side
 * SegmentHandler per leashed mob; gameplay attach/break rules stay vanilla).
 */
@OnlyIn(Dist.CLIENT)
public class LeadOverhaul {

    // slightly beyond the 10-block vanilla break distance so the visual rope never prunes early
    private static final double LEAD_ROPE_LENGTH = 12.0;
    // vanilla leads go taut at ~6 blocks; below that the rope hangs
    private static final double LEAD_SLACK_LENGTH = 6.0;

    private static class LeashRope {
        SegmentHandler segments;
        int lastTick = Integer.MIN_VALUE;
    }

    private static final Map<Mob, LeashRope> leashRopes = new WeakHashMap<>();

    /** Attachment point on the mob, world space. */
    private static Vec mobPoint(Mob mob, float partialTicks) {
        double x = Mth.lerp(partialTicks, mob.xo, mob.getX());
        double y = Mth.lerp(partialTicks, mob.yo, mob.getY());
        double z = Mth.lerp(partialTicks, mob.zo, mob.getZ());
        Vec3 offset = mob.getLeashOffset(partialTicks);
        return new Vec(x + offset.x, y + offset.y, z + offset.z);
    }

    /**
     * Renders the overhauled lead. Returns false when the overhaul is disabled so the caller
     * falls through to the vanilla leash rendering.
     */
    public static boolean renderLeash(Mob mob, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, Entity holder) {
        if (!GrappleConfig.getConf().leads.overhaul_enabled) {
            return false;
        }

        Vec mobPos = mobPoint(mob, partialTicks);
        Vec holderPos = new Vec(holder.getRopeHoldPosition(partialTicks));

        // visual-only rope wrapping, advanced once per game tick
        LeashRope rope = leashRopes.computeIfAbsent(mob, m -> new LeashRope());
        if (rope.segments == null) {
            rope.segments = new SegmentHandler(mob.level(), null, mobPoint(mob, 1.0F), new Vec(holder.getRopeHoldPosition(1.0F)));
        }
        if (rope.lastTick != mob.tickCount) {
            rope.lastTick = mob.tickCount;
            try {
                rope.segments.update(mobPoint(mob, 1.0F), new Vec(holder.getRopeHoldPosition(1.0F)), LEAD_ROPE_LENGTH, true);
            } catch (Exception e) {
                // never let a visual helper crash rendering; fall back to a straight rope
                rope.segments = new SegmentHandler(mob.level(), null, mobPoint(mob, 1.0F), new Vec(holder.getRopeHoldPosition(1.0F)));
            }
        }
        SegmentHandler segments = rope.segments;

        // sag: taut when the full rope path approaches vanilla lead length
        double ropeDist = segments.getDist(mobPos, holderPos);
        double taut = 1 - ((LEAD_SLACK_LENGTH - ropeDist) / 5);
        taut = Math.max(0, Math.min(1, taut));

        int light = LevelRenderer.getLightColor(mob.level(), BlockPos.containing(mob.getEyePosition()));

        poseStack.pushPose();
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix4f = pose.pose();
        Matrix3f matrix3f = pose.normal();
        VertexConsumer buffer = buffers.getBuffer(RopeRenderer.ROPE_RENDER);

        // pose origin is the mob's lerped render position; everything is drawn relative to it
        Vec origin = new Vec(
                Mth.lerp(partialTicks, mob.xo, mob.getX()),
                Mth.lerp(partialTicks, mob.yo, mob.getY()),
                Mth.lerp(partialTicks, mob.zo, mob.getZ()));

        int count = segments.segments.size();
        for (int i = 0; i < count - 1; i++) {
            Vec from = (i == 0) ? mobPos : segments.segments.get(i);
            Vec to = (i + 2 == count) ? holderPos : segments.segments.get(i + 1);
            double segTaut = (i + 2 == count) ? taut : 1.0;
            RopeRenderer.drawSegment(from.sub(origin), to.sub(origin), segTaut, buffer, matrix4f, matrix3f, light);
        }

        poseStack.popPose();
        return true;
    }
}
