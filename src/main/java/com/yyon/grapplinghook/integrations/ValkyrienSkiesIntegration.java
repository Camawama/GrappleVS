package com.yyon.grapplinghook.integrations;

import com.yyon.grapplinghook.entities.grapplehook.GrapplehookEntity;
import com.yyon.grapplinghook.utils.Vec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.events.PhysTickEvent;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /**
     * Rotates a shipyard-space direction (e.g. a block hit side) into world space, without scaling.
     * Ship raytraces report hit Directions in shipyard space; any math done in world space (rope
     * bend planes, corner offsets) must use the rotated normal or it silently assumes the ship is
     * axis-aligned. Returns null if the ship isn't loaded.
     */
    public static Vec shipDirToWorld(Level level, long shipId, Vec dir) {
        Ship ship = getShipById(level, shipId);
        if (ship == null) {
            return null;
        }
        Vector3d v = ship.getTransform().transformDirectionNoScalingFromShipToWorld(
                new Vector3d(dir.x, dir.y, dir.z), new Vector3d());
        return new Vec(v.x, v.y, v.z);
    }

    /**
     * Where a world-space point from the previous tick sits now, if it were glued to the ship.
     * Mapping last tick's rope through this puts the rope and the ship in a shared reference
     * frame, so a ship moving or rotating into a stationary rope still shows up as the rope
     * sweeping across the hull. Returns null if the ship isn't loaded.
     */
    public static Vec prevTickWorldPosToCurrent(Level level, long shipId, Vec prevWorldPos) {
        Ship ship = getShipById(level, shipId);
        if (ship == null) {
            return null;
        }
        Vector3d local = ship.getPrevTickTransform().getWorldToShip()
                .transformPosition(new Vector3d(prevWorldPos.x, prevWorldPos.y, prevWorldPos.z));
        Vector3d cur = ship.getShipToWorld().transformPosition(local);
        return new Vec(cur.x, cur.y, cur.z);
    }

    // ---- hook impact impulses -------------------------------------------------------------
    // Impulses are queued on the game thread and applied on the physics thread via the global
    // phys-tick event (the only public force API in VS 2.4.x core).

    private record QueuedImpulse(String dimensionId, long shipId, Vector3d impulseNs, Vector3d shipyardPos, long createdMs) {}

    private static final ConcurrentLinkedQueue<QueuedImpulse> pendingImpulses = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean physListenerRegistered = new AtomicBoolean(false);
    private static final long IMPULSE_EXPIRY_MS = 5000;

    /** Queues a world-space impulse (N&middot;s) at a shipyard-space position on the given ship. */
    public static void queueShipImpulse(Level level, long shipId, Vec worldImpulseNs, Vec shipyardPos) {
        if (physListenerRegistered.compareAndSet(false, true)) {
            ValkyrienSkiesMod.getVsCore().getPhysTickEvent().on(ValkyrienSkiesIntegration::onPhysTick);
        }
        pendingImpulses.add(new QueuedImpulse(
                VSGameUtilsKt.getDimensionId(level), shipId,
                new Vector3d(worldImpulseNs.x, worldImpulseNs.y, worldImpulseNs.z),
                new Vector3d(shipyardPos.x, shipyardPos.y, shipyardPos.z),
                System.currentTimeMillis()));
    }

    private static void onPhysTick(PhysTickEvent event) {
        if (pendingImpulses.isEmpty()) {
            return;
        }
        Iterator<QueuedImpulse> it = pendingImpulses.iterator();
        while (it.hasNext()) {
            QueuedImpulse qi = it.next();
            if (!qi.dimensionId().equals(event.getWorld().getDimension())) {
                // a queued impulse whose dimension never phys-ticks again would leak otherwise
                if (System.currentTimeMillis() - qi.createdMs() > IMPULSE_EXPIRY_MS) {
                    it.remove();
                }
                continue;
            }
            it.remove();
            PhysShip physShip = event.getWorld().getShipById(qi.shipId());
            if (physShip == null) {
                continue;
            }
            double delta = event.getDelta();
            if (delta <= 0) {
                delta = 1.0 / 60.0;
            }
            // impulse (N*s) -> force applied over exactly one physics step
            Vector3d force = new Vector3d(qi.impulseNs()).mul(1.0 / delta);
            physShip.applyWorldForceToModelPos(force, qi.shipyardPos());
        }
    }

    public static BlockHitResult rayTraceBlocks(Level level, ClipContext context) {
        // shouldTransformHitPos = true: ship hit locations come back in world space
        // (the BlockPos of the hit stays in shipyard space, which is what block lookups need)
        return RaycastUtilsKt.clipIncludeShips(level, context, true);
    }
}
