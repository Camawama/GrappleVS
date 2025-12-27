package com.yyon.grapplinghook.integrations;

import com.yyon.grapplinghook.entities.grapplehook.GrapplehookEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

public class ValkyrienSkiesIntegration {

    public static void attachToShip(GrapplehookEntity entity, Level level, Vec3 pos, BlockPos blockPos) {
        if (level instanceof ServerLevel serverLevel) {
            Vector3d jomlPos = new Vector3d(pos.x, pos.y, pos.z);
            Ship ship = null;

            // 1. Try finding ship by BlockPos (if provided and in shipyard)
            if (blockPos != null && VSGameUtilsKt.isBlockInShipyard(serverLevel, blockPos)) {
                ship = VSGameUtilsKt.getShipManagingPos(serverLevel, blockPos);
            }
            
            // 2. Fallback: Try finding ship by World Position
            if (ship == null) {
                ship = VSGameUtilsKt.getShipObjectManagingPos(serverLevel, jomlPos);
            }

            if (ship != null) {
                // Transform World Position to Ship Local Position
                Vector3d localPos = ship.getWorldToShip().transformPosition(new Vector3d(jomlPos));
                
                entity.getPersistentData().putLong("vs_ship_id", ship.getId());
                entity.getPersistentData().putDouble("vs_local_x", localPos.x);
                entity.getPersistentData().putDouble("vs_local_y", localPos.y);
                entity.getPersistentData().putDouble("vs_local_z", localPos.z);
            }
        }
    }

    public static void updateShipAttachment(GrapplehookEntity entity, Level level) {
        if (entity.getPersistentData().contains("vs_ship_id")) {
            long shipId = entity.getPersistentData().getLong("vs_ship_id");
            
            // Updated for VS 2.4.0: Use VSGameUtilsKt to get the ship world
            Ship ship = VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(shipId);

            if (ship != null) {
                double x = entity.getPersistentData().getDouble("vs_local_x");
                double y = entity.getPersistentData().getDouble("vs_local_y");
                double z = entity.getPersistentData().getDouble("vs_local_z");
                
                Vector3d localPos = new Vector3d(x, y, z);
                Vector3d worldPos = ship.getShipToWorld().transformPosition(new Vector3d(localPos));
                
                entity.setPos(worldPos.x, worldPos.y, worldPos.z);

                if (entity.thisPos != null) {
                    entity.thisPos.x = worldPos.x;
                    entity.thisPos.y = worldPos.y;
                    entity.thisPos.z = worldPos.z;
                }
            }
        }
    }
    
    public static boolean isBlockInShipyard(Level level, BlockPos pos) {
        return VSGameUtilsKt.isBlockInShipyard(level, pos);
    }

    public static BlockHitResult rayTraceBlocks(Level level, ClipContext context) {
        return RaycastUtilsKt.clipIncludeShips(level, context, false);
    }
}
