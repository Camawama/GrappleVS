package com.yyon.grapplinghook.utils;

import com.yyon.grapplinghook.GrappleMod;
import com.yyon.grapplinghook.common.CommonSetup;
import com.yyon.grapplinghook.integrations.ValkyrienSkiesIntegration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;

public class GrapplemodUtils {
	private static Boolean vsLoaded = null;

	/** Cached ModList lookup; called on hot per-tick paths (raytraces, entity ticks). */
	public static boolean vsLoaded() {
		if (vsLoaded == null) {
			vsLoaded = ModList.get().isLoaded("valkyrienskies");
		}
		return vsLoaded;
	}

	public static void sendToCorrectClient(Object message, int playerid, Level w) {
		Entity entity = w.getEntity(playerid);
		if (entity instanceof ServerPlayer) {
			CommonSetup.network.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) entity), message);
		} else {
			GrappleMod.LOGGER.warn("Couldn't find player to send message to");
		}
	}

	public static BlockHitResult rayTraceBlocks(Level world, Vec from, Vec to) {
		HitResult result;
		if (vsLoaded()) {
			ClipContext context = new ClipContext(from.toVec3d(), to.toVec3d(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
			result = ValkyrienSkiesIntegration.rayTraceBlocks(world, context);
		} else {
			result = world.clip(new ClipContext(from.toVec3d(), to.toVec3d(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
		}

		if (result != null && result instanceof BlockHitResult) {
			BlockHitResult blockhit = (BlockHitResult) result;
			if (blockhit.getType() != HitResult.Type.BLOCK) {
				return null;
			}
			return blockhit;
		}
		return null;
	}

	public static long getTime(Level w) {
		return w.getGameTime();
	}

	private static int controllerid = 0;
	public static int GRAPPLEID = controllerid++;
	public static int REPELID = controllerid++;
	public static int AIRID = controllerid++;

}