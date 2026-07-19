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
        int holderId = -1;
    }

    private static final Map<Mob, LeashRope> leashRopes = new WeakHashMap<>();

    /**
     * Attachment point on the mob, world space: the vanilla leash point (neck), which requires
     * rotating the leash offset by the mob's body yaw — the raw offset is in model space, and
     * skipping the rotation leaves the rope floating beside/above wide mobs like horses.
     */
    private static Vec mobPoint(Mob mob, float partialTicks) {
        double x = Mth.lerp(partialTicks, mob.xo, mob.getX());
        double y = Mth.lerp(partialTicks, mob.yo, mob.getY());
        double z = Mth.lerp(partialTicks, mob.zo, mob.getZ());
        double bodyYaw = Mth.lerp(partialTicks, mob.yBodyRotO, mob.yBodyRot) * (Math.PI / 180.0) + (Math.PI / 2.0);
        Vec3 offset = mob.getLeashOffset(partialTicks);
        double ox = Math.cos(bodyYaw) * offset.z + Math.sin(bodyYaw) * offset.x;
        double oz = Math.sin(bodyYaw) * offset.z - Math.cos(bodyYaw) * offset.x;
        return new Vec(x + ox, y + offset.y, z + oz);
    }

    /**
     * Holder endpoint used for collision/wrapping. A fence knot's rope-hold position sits inside
     * the fence block, which would make the raytrace collide right at the endpoint and pile up
     * junk bends there — so the collision endpoint is nudged towards the mob, out of the post.
     * Rendering still draws to the real holder position.
     */
    private static Vec segmentHolderPoint(Mob mob, Entity holder) {
        Vec holderPos = new Vec(holder.getRopeHoldPosition(1.0F));
        Vec toMob = mobPoint(mob, 1.0F).sub(holderPos);
        double dist = toMob.length();
        if (dist < 0.01) {
            return holderPos;
        }
        return holderPos.add(toMob.changeLen(Math.min(0.45, dist * 0.5)));
    }

    /**
     * Leads unwrap eagerly: any bend whose neighbours can see each other again is dropped at
     * once. The grapple rope's release-plane test is the physical behaviour, but on a lead it
     * reads as the rope being glued to the corner until pulled far past it.
     */
    private static void pruneVisibleBends(SegmentHandler segments, Mob mob) {
        for (int i = 1; i < segments.segments.size() - 1; ) {
            Vec before = segments.segments.get(i - 1);
            Vec after = segments.segments.get(i + 1);
            if (com.yyon.grapplinghook.utils.GrapplemodUtils.rayTraceBlocks(mob.level(), before, after) == null) {
                segments.removeSegment(i);
            } else {
                i++;
            }
        }
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
        // reset on a new/changed leash (unleash + re-leash must not resurrect old bends) and
        // when the rope wasn't rendered recently (stale state from an earlier leash)
        boolean stale = rope.lastTick != Integer.MIN_VALUE && mob.tickCount - rope.lastTick > 5;
        if (rope.segments == null || rope.holderId != holder.getId() || stale) {
            rope.segments = null;
            rope.holderId = holder.getId();
        }
        if (rope.segments == null) {
            rope.segments = new SegmentHandler(mob.level(), null, mobPoint(mob, 1.0F), segmentHolderPoint(mob, holder));
        }
        if (rope.lastTick != mob.tickCount) {
            rope.lastTick = mob.tickCount;
            try {
                rope.segments.update(mobPoint(mob, 1.0F), segmentHolderPoint(mob, holder), LEAD_ROPE_LENGTH, true);
                pruneVisibleBends(rope.segments, mob);
            } catch (Exception e) {
                // never let a visual helper crash rendering; fall back to a straight rope
                rope.segments = new SegmentHandler(mob.level(), null, mobPoint(mob, 1.0F), segmentHolderPoint(mob, holder));
            }
        }
        SegmentHandler segments = rope.segments;

        // sag: the unused slack (blocks below lead length) droops out of the rope, bedrock-style
        double ropeDist = segments.getDist(mobPos, holderPos);
        double slack = Math.max(0, LEAD_SLACK_LENGTH - ropeDist);
        double sagDepth = Math.min(slack * 0.3, 1.4);

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
            // distribute the droop across spans proportionally to their share of the rope
            double spanFrac = ropeDist > 0.01 ? to.sub(from).length() / ropeDist : 1.0;
            RopeRenderer.drawSegmentSag(from.sub(origin), to.sub(origin), sagDepth * spanFrac, buffer, matrix4f, matrix3f, light);
        }

        poseStack.popPose();
        return true;
    }
}
