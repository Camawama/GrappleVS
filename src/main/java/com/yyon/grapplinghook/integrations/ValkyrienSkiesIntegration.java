package com.yyon.grapplinghook.integrations;

import com.yyon.grapplinghook.entities.grapplehook.GrapplehookEntity;
import com.yyon.grapplinghook.utils.Vec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

/**
 * All direct references to Valkyrien Skies classes live in this class. Callers must guard every
 * call with {@code GrapplemodUtils.vsLoaded()} so this class is never loaded when VS is absent.
 */
public class ValkyrienSkiesIntegration {

    public static final long NO_SHIP = -1;

    /**
     * Attaches the hook to the ship managing blockPos, if any. The side-hit nudge is applied in
     * shipyard space (hit Directions on ships are shipyard-space), so it stays correct for rotated
     * ships. Returns true if a ship attachment was made; the caller applies the nudge in world
     * space itself when this returns false.
     */
    public static boolean attachToShip(GrapplehookEntity entity, Level level, Vec3 pos, BlockPos blockPos, Vec3 nudge) {
        if (blockPos == null || !VSGameUtilsKt.isBlockInShipyard(level, blockPos)) {
            return false;
        }
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, blockPos);
        if (ship == null) {
            return false;
        }

        Vector3d local = ship.getWorldToShip().transformPosition(new Vector3d(pos.x, pos.y, pos.z));
        local.add(nudge.x, nudge.y, nudge.z);
        Vector3d world = ship.getShipToWorld().transformPosition(new Vector3d(local));

        entity.setPos(world.x, world.y, world.z);
        entity.getPersistentData().putLong("vs_ship_id", ship.getId());
        entity.getPersistentData().putDouble("vs_local_x", local.x);
        entity.getPersistentData().putDouble("vs_local_y", local.y);
        entity.getPersistentData().putDouble("vs_local_z", local.z);
        return true;
    }

    /**
     * Re-positions an attached hook onto its (possibly moving) ship. Returns false when the hook
     * is ship-attached but the ship can no longer be resolved (unloaded/deleted), so the caller
     * can decide to detach.
     */
    public static boolean updateShipAttachment(GrapplehookEntity entity, Level level) {
        if (!entity.getPersistentData().contains("vs_ship_id")) {
            return true;
        }
        long shipId = entity.getPersistentData().getLong("vs_ship_id");
        Ship ship = getShipById(level, shipId);
        if (ship == null) {
            return false;
        }

        double x = entity.getPersistentData().getDouble("vs_local_x");
        double y = entity.getPersistentData().getDouble("vs_local_y");
        double z = entity.getPersistentData().getDouble("vs_local_z");

        Vector3d worldPos = ship.getShipToWorld().transformPosition(new Vector3d(x, y, z));

        entity.setPos(worldPos.x, worldPos.y, worldPos.z);

        if (entity.thisPos != null) {
            entity.thisPos.x = worldPos.x;
            entity.thisPos.y = worldPos.y;
            entity.thisPos.z = worldPos.z;
        }
        return true;
    }

    public static Ship getShipById(Level level, long shipId) {
        return VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(shipId);
    }

    /** Ship id managing a (shipyard-space) block position, or NO_SHIP. Works on both sides. */
    public static long getShipIdManaging(Level level, BlockPos pos) {
        if (pos == null || !VSGameUtilsKt.isBlockInShipyard(level, pos)) {
            return NO_SHIP;
        }
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        return ship == null ? NO_SHIP : ship.getId();
    }

    /** World-space position -> ship-local (shipyard) position, or null if the ship isn't loaded. */
    public static Vec worldToShip(Level level, long shipId, Vec worldPos) {
        Ship ship = getShipById(level, shipId);
        if (ship == null) {
            return null;
        }
        Vector3d v = ship.getWorldToShip().transformPosition(new Vector3d(worldPos.x, worldPos.y, worldPos.z));
        return new Vec(v.x, v.y, v.z);
    }

    /** Ship-local (shipyard) position -> world-space position, or null if the ship isn't loaded. */
    public static Vec shipToWorld(Level level, long shipId, Vec localPos) {
        Ship ship = getShipById(level, shipId);
        if (ship == null) {
            return null;
        }
        Vector3d v = ship.getShipToWorld().transformPosition(new Vector3d(localPos.x, localPos.y, localPos.z));
        return new Vec(v.x, v.y, v.z);
    }

    public static boolean isBlockInShipyard(Level level, BlockPos pos) {
        return VSGameUtilsKt.isBlockInShipyard(level, pos);
    }

    public static BlockHitResult rayTraceBlocks(Level level, ClipContext context) {
        // shouldTransformHitPos = true: ship hit locations come back in world space
        // (the BlockPos of the hit stays in shipyard space, which is what block lookups need)
        return RaycastUtilsKt.clipIncludeShips(level, context, true);
    }
}
