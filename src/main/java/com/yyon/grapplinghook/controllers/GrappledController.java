package com.yyon.grapplinghook.controllers;

import com.yyon.grapplinghook.client.ClientProxyInterface;
import com.yyon.grapplinghook.config.GrappleConfig;
import com.yyon.grapplinghook.entities.grapplehook.GrapplehookEntity;
import com.yyon.grapplinghook.entities.grapplehook.SegmentHandler;
import com.yyon.grapplinghook.utils.GrappleCustomization;
import com.yyon.grapplinghook.utils.GrapplemodUtils;
import com.yyon.grapplinghook.utils.Vec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Client-side swing physics for the LOCAL player when another player's hook has grappled them.
 * The shooter's server tick owns the rope (length, reeling, bends, snapping); this controller
 * only makes the grappled player swing smoothly on their own screen, exactly like the physics
 * a player gets when hanging from a wall hook. It is created and released from the hook's
 * synced entity data, so no extra network messages are needed.
 */
public class GrappledController extends GrappleController {

	private final int hookId;

	public GrappledController(int hookId, int playerId, Level world, GrappleCustomization custom) {
		// -1 hook entity id: this controller isn't driving its own thrown hook
		super(-1, playerId, world, new Vec(0, 0, 0), GrapplemodUtils.GRAPPLEID, custom);
		this.hookId = hookId;
	}

	@Override
	public void unattach() {
		// Local release only. The shooter owns the hook entity and the server owns the rope, so
		// never send GrappleEndMessage (that would destroy the shooter's hook). An air controller
		// keeps the momentum smoothing after release, like a normal rope release.
		if (ClientProxyInterface.proxy.unregisterController(this.entityId) != null) {
			this.attached = false;
			if (!isFlying(this.entity)) {
				ClientProxyInterface.proxy.createControl(GrapplemodUtils.AIRID, -1, this.entityId, this.entity.level(), new Vec(0, 0, 0), null, this.custom);
			}
		}
	}

	@Override
	public void updatePlayerPos() {
		if (!this.attached) {
			return;
		}
		Entity entity = this.entity;
		if (entity == null || !entity.isAlive()) {
			this.unattach();
			return;
		}

		Entity hookUncast = this.world.getEntity(this.hookId);
		if (!(hookUncast instanceof GrapplehookEntity) || !hookUncast.isAlive()) {
			this.unattach();
			return;
		}
		GrapplehookEntity hook = (GrapplehookEntity) hookUncast;
		if (hook.getSyncedAttachedEntityId() != entity.getId()) {
			this.unattach();
			return;
		}

		if (entity.getVehicle() != null) {
			this.unattach();
			return;
		}

		// creative flight overrides the rope, same as for the shooter's own grapples
		if (entity instanceof Player && ((Player) entity).getAbilities().flying) {
			this.motion = Vec.motionVec(entity);
			return;
		}

		this.normalGround(false);
		this.normalCollisions(false);
		this.applyAirFriction();
		motion.add_ip(new Vec(0, -0.05, 0));

		// the hook clings to us; we swing around the rope bend nearest to us, and the rope
		// between that bend and the shooter is already spoken for
		double r = hook.getSyncedRopeLength();
		SegmentHandler segments = hook.segmentHandler;
		Vec hookPos = Vec.positionVec(hook);
		Entity shooter = hook.shootingEntity;
		if (shooter != null) {
			segments.updatePos(hookPos, Vec.positionVec(shooter).add(new Vec(0, shooter.getEyeHeight(), 0)), r);
		}
		Vec anchor = segments.getFarthest();
		double usedRope = segments.getDistToFarthest();
		double allowed = Math.max(1.0, r - usedRope);

		Vec playerpos = Vec.positionVec(entity).add(new Vec(0, entity.getBbHeight() * 0.6, 0));
		Vec spherevec = playerpos.sub(anchor);
		double dist = spherevec.length();

		Vec additionalmotion = new Vec(0, 0, 0);
		if (dist > allowed) {
			if (dist - allowed > GrappleConfig.getConf().grapplinghook.other.rope_snap_buffer) {
				// desynced beyond any reasonable rope stretch: stop fighting it locally
				this.unattach();
				this.updateServerPos();
				return;
			}
			if (anchor.sub(playerpos.add(motion)).length() > allowed) {
				motion = motion.removeAlong(spherevec);
			}
			additionalmotion = spherevec.changeLen(allowed).sub(spherevec);
		}

		this.calcTaut(dist, hook);

		// WASD swing control, same feel as swinging on your own rope
		this.applyPlayerMovement();

		Vec newmotion = motion.add(additionalmotion);
		if (Double.isNaN(newmotion.x) || Double.isNaN(newmotion.y) || Double.isNaN(newmotion.z)) {
			newmotion = new Vec(0, 0, 0);
			motion = new Vec(0, 0, 0);
		}
		entity.setDeltaMovement(newmotion.x, newmotion.y, newmotion.z);

		this.updateServerPos();
	}
}
