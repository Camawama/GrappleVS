package com.yyon.grapplinghook.entities.grapplehook;

import com.yyon.grapplinghook.GrappleMod;
import com.yyon.grapplinghook.common.CommonSetup;
import com.yyon.grapplinghook.integrations.ValkyrienSkiesIntegration;
import com.yyon.grapplinghook.network.SegmentMessage;
import com.yyon.grapplinghook.utils.GrapplemodUtils;
import com.yyon.grapplinghook.utils.Vec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class SegmentHandler {

	public static final long NO_SHIP = -1;

	public LinkedList<Vec> segments;
	public LinkedList<Direction> segmentBottomSides;
	public LinkedList<Direction> segmentTopSides;
	// Valkyrien Skies: per-bend ship id (NO_SHIP for world bends) and ship-local (shipyard) position.
	// Ship bends are re-projected to world space every tick so the rope follows the moving ship.
	public LinkedList<Long> segmentShipIds;
	public LinkedList<Vec> segmentShipLocals;
	public Level world;
	public GrapplehookEntity hookEntity;

	Vec prevHookPos = null;
	Vec prevPlayerPos = null;

	final double bendOffset = 0.05;
	final double intoBlock = 0.05;

	public SegmentHandler(Level w, GrapplehookEntity hookEntity, Vec hookpos, Vec playerpos) {
		segments = new LinkedList<Vec>();
		segments.add(hookpos);
		segments.add(playerpos);
		segmentBottomSides = new LinkedList<Direction>();
		segmentBottomSides.add(null);
		segmentBottomSides.add(null);
		segmentTopSides = new LinkedList<Direction>();
		segmentTopSides.add(null);
		segmentTopSides.add(null);
		segmentShipIds = new LinkedList<Long>();
		segmentShipIds.add(NO_SHIP);
		segmentShipIds.add(NO_SHIP);
		segmentShipLocals = new LinkedList<Vec>();
		segmentShipLocals.add(null);
		segmentShipLocals.add(null);
		this.world = w;
		this.hookEntity = hookEntity;
		this.prevHookPos = new Vec(hookpos);
		this.prevPlayerPos = new Vec(playerpos);
	}

	public void forceSetPos(Vec hookpos, Vec playerpos) {
		this.prevHookPos = new Vec(hookpos);
		this.prevPlayerPos = new Vec(playerpos);
		this.segments.set(0, new Vec(hookpos));
		this.segments.set(this.segments.size() - 1, new Vec(playerpos));
	}

	double ropeLen;

	/**
	 * Re-projects every ship-attached bend to its current world position. Bends whose ship can no
	 * longer be resolved (unloaded/deleted) are removed so the rope doesn't stay pinned in mid-air.
	 */
	public void refreshShipBends() {
		if (!GrapplemodUtils.vsLoaded()) {
			return;
		}
		for (int i = 1; i < segments.size() - 1; ) {
			long shipId = segmentShipIds.get(i);
			if (shipId == NO_SHIP) {
				i++;
				continue;
			}
			Vec worldPos = ValkyrienSkiesIntegration.shipToWorld(this.world, shipId, segmentShipLocals.get(i));
			if (worldPos == null) {
				this.removeSegment(i);
			} else {
				segments.set(i, worldPos);
				i++;
			}
		}
	}

	public void updatePos(Vec hookpos, Vec playerpos, double ropelen) {
		this.refreshShipBends();
		segments.set(0, hookpos);
		segments.set(segments.size() - 1, playerpos);
		this.ropeLen = ropelen;
	}

	public void update(Vec hookpos, Vec playerpos, double ropelen, boolean movinghook) {
		if (prevHookPos == null) {
			prevHookPos = hookpos;
			prevPlayerPos = playerpos;
		}

		this.refreshShipBends();

		segments.set(0, hookpos);
		segments.set(segments.size() - 1, playerpos);
		this.ropeLen = ropelen;


		Vec closest = segments.get(segments.size()-2);

		while (true) {
			if (segments.size() == 2) {
				break;
			}

			int index = segments.size()-2;
			closest = segments.get(index);
			Direction bottomside = segmentBottomSides.get(index);
			Direction topside = segmentTopSides.get(index);
			Vec ropevec = playerpos.sub(closest);

			Vec beforepoint = segments.get(index-1);

			long bendShipId = segmentShipIds.get(index);
			Vec edgevec = getWorldNormal(bottomside, bendShipId).cross(getWorldNormal(topside, bendShipId));
			Vec planenormal = beforepoint.sub(closest).cross(edgevec);

			if (ropevec.dot(planenormal) > 0) {
				this.removeSegment(index);
			} else {
				break;
			}
		}

		Vec farthest = segments.get(1);

		if (movinghook) {
			while (true) {
				if (segments.size() == 2) {
					break;
				}

				int index = 1;
				farthest = segments.get(index);
				Direction bottomside = segmentBottomSides.get(index);
				Direction topside = segmentTopSides.get(index);
				Vec ropevec = farthest.sub(hookpos);

				Vec beforepoint = segments.get(index+1);

				long bendShipId = segmentShipIds.get(index);
				Vec edgevec = getWorldNormal(bottomside, bendShipId).cross(getWorldNormal(topside, bendShipId));
				Vec planenormal = beforepoint.sub(farthest).cross(edgevec);

				if (ropevec.dot(planenormal) > 0 || ropevec.length() < 0.1) {
					this.removeSegment(index);
				} else {
					break;
				}
			}

			while (true) {
				if (this.getDistToFarthest() > ropelen) {
					this.removeSegment(1);
				} else {
					break;
				}
			}
		}

		if (movinghook) {
			farthest = segments.get(1);
			Vec prevfarthest = farthest;
			if (segments.size() == 2) {
				prevfarthest = prevPlayerPos;
			}
			updateSegment(hookpos, prevHookPos, farthest, prevfarthest, 1, 0);
		}

		Vec prevclosest = closest;
		if (segments.size() == 2) {
			prevclosest = prevHookPos;
		}
		updateSegment(closest, prevclosest, playerpos, prevPlayerPos, segments.size() - 1, 0);


		prevHookPos = hookpos;
		prevPlayerPos = playerpos;
	}

	public void removeSegment(int index) {
		segments.remove(index);
		segmentBottomSides.remove(index);
		segmentTopSides.remove(index);
		segmentShipIds.remove(index);
		segmentShipLocals.remove(index);

		if (!this.world.isClientSide && this.hookEntity.shootingEntity != null) {
			SegmentMessage addmessage = new SegmentMessage(this.hookEntity.getId(), false, index, new Vec(0, 0, 0), Direction.DOWN, Direction.DOWN, NO_SHIP, new Vec(0, 0, 0));
			// TRACKING_ENTITY excludes the owning player, who simulates its own segments client-side
			CommonSetup.network.send(PacketDistributor.TRACKING_ENTITY.with(() -> this.hookEntity.shootingEntity), addmessage);
		}
	}

	public void updateSegment(Vec top, Vec prevtop, Vec bottom, Vec prevbottom, int index, int numberrecursions) {
		BlockHitResult bottomraytraceresult = GrapplemodUtils.rayTraceBlocks(this.world, bottom, top);

		// if rope hit block
		if (bottomraytraceresult != null) {
			// If the rope hit a Valkyrien Skies ship, measure last tick's rope in the ship's
			// reference frame (glue the prev endpoints to the ship). Otherwise a ship moving or
			// rotating into a stationary rope also "hits" with the prev endpoints and the crossing
			// is never detected.
			long bottomShipId = NO_SHIP;
			if (GrapplemodUtils.vsLoaded()) {
				bottomShipId = ValkyrienSkiesIntegration.getShipIdManaging(this.world, bottomraytraceresult.getBlockPos());
				if (bottomShipId != NO_SHIP) {
					Vec prevTopAdj = ValkyrienSkiesIntegration.prevTickWorldPosToCurrent(this.world, bottomShipId, prevtop);
					Vec prevBottomAdj = ValkyrienSkiesIntegration.prevTickWorldPosToCurrent(this.world, bottomShipId, prevbottom);
					if (prevTopAdj != null && prevBottomAdj != null) {
						prevtop = prevTopAdj;
						prevbottom = prevBottomAdj;
					}
				}
			}

			if (GrapplemodUtils.rayTraceBlocks(this.world, prevbottom, prevtop) != null) {
				return;
			}

			Vec bottomhitvec = new Vec(bottomraytraceresult.getLocation());

			Direction bottomside = bottomraytraceresult.getDirection();
			Vec bottomnormal = this.getWorldNormal(bottomside, bottomShipId);

			// calculate where bottomhitvec was along the rope in the previous tick
			double prevropelen = prevtop.sub(prevbottom).length();

			Vec cornerbound1 = bottomhitvec.add(bottomnormal.changeLen(-intoBlock));

			Vec bound_option1 = linePlaneIntersection(prevtop, prevbottom, cornerbound1, bottomnormal);
			Vec bound_option2 = linePlaneIntersection(top, prevtop, cornerbound1, bottomnormal);
			Vec bound_option3 = linePlaneIntersection(prevbottom, bottom, cornerbound1, bottomnormal);

			for (Vec cornerbound2 : new Vec[] {bound_option1, bound_option2, bound_option3}) {
				if (cornerbound2 == null) {
					continue;
				}

				// the corner must be in the line (cornerbound2, cornerbound1)
				BlockHitResult cornerraytraceresult = GrapplemodUtils.rayTraceBlocks(this.world, cornerbound2, cornerbound1);
				if (cornerraytraceresult != null) {
					Vec cornerhitpos = new Vec(cornerraytraceresult.getLocation());
					Direction cornerside = cornerraytraceresult.getDirection();

					long cornerShipId = NO_SHIP;
					if (GrapplemodUtils.vsLoaded()) {
						cornerShipId = ValkyrienSkiesIntegration.getShipIdManaging(this.world, cornerraytraceresult.getBlockPos());
					}
					Vec cornernormal = this.getWorldNormal(cornerside, cornerShipId);

					// world-space parallelism check: on rotated ships the corner face can be
					// (anti)parallel to the bottom face without the Direction enums matching
					if (Math.abs(cornernormal.dot(bottomnormal)) > 0.99) {
						// this should not happen
						continue;
					} else {
						// add a bend around the corner
						Vec actualcorner = cornerhitpos.add(bottomnormal.changeLen(intoBlock));
						Vec bend = actualcorner.add(bottomnormal.changeLen(bendOffset)).add(cornernormal.changeLen(bendOffset));
						Vec topropevec = bend.sub(top);
						Vec bottomropevec = bend.sub(bottom);

						// ignore bends that are too close to another bend
						if (topropevec.length() < 0.05) {
							if (this.segmentBottomSides.get(index - 1) == bottomside && this.segmentTopSides.get(index - 1) == cornerside) {
								continue;
							}
						}
						if (bottomropevec.length() < 0.05) {
							if (this.segmentBottomSides.get(index) == bottomside && this.segmentTopSides.get(index) == cornerside) {
								continue;
							}
						}

						// if the corner block belongs to a Valkyrien Skies ship, remember the ship
						// and the bend's ship-local position so it can follow the ship
						long shipId = cornerShipId;
						Vec shipLocal = null;
						if (shipId != NO_SHIP) {
							shipLocal = ValkyrienSkiesIntegration.worldToShip(this.world, shipId, bend);
							if (shipLocal == null) {
								shipId = NO_SHIP;
							}
						}

						this.actuallyAddSegment(index, bend, bottomside, cornerside, shipId, shipLocal);

						// if not enough rope length left, undo
						if(this.getDistToAnchor() + .2 > this.ropeLen) {
							this.removeSegment(index);
							continue;
						}

						// now to recurse on top section of rope
						double newropelen = topropevec.length() + bottomropevec.length();

						double prevtoptobend = topropevec.length() * prevropelen / newropelen;
						Vec prevbend = prevtop.add(prevbottom.sub(prevtop).changeLen(prevtoptobend));

						if (numberrecursions < 10) {
							updateSegment(top, prevtop, bend, prevbend, index, numberrecursions+1);
						} else {
							GrappleMod.LOGGER.warn("Rope segment recursion limit exceeded");
						}
						break;
					}
				}
			}
		}
	}

	public Vec linePlaneIntersection(Vec linepoint1, Vec linepoint2, Vec planepoint, Vec planenormal) {
		// calculate the intersection of a line and a plane
		// formula: https://en.wikipedia.org/wiki/Line%E2%80%93plane_intersection#Algebraic_form

		Vec linevec = linepoint2.sub(linepoint1);

		if (linevec.dot(planenormal) == 0) {
			return null;
		}

		double d = planepoint.sub(linepoint1).dot(planenormal) / linevec.dot(planenormal);
		return linepoint1.add(linevec.mult(d));
	}

	public Vec getNormal(Direction facing) {
		Vec3i facingvec = facing.getNormal();
		return new Vec(facingvec.getX(), facingvec.getY(), facingvec.getZ());
	}

	/**
	 * World-space normal of a hit side. Sides reported by ship raytraces are shipyard-space
	 * Directions, so for ship bends the normal must be rotated by the ship's current transform;
	 * world bends pass NO_SHIP and get the plain axis-aligned normal.
	 */
	public Vec getWorldNormal(Direction facing, long shipId) {
		Vec normal = getNormal(facing);
		if (shipId != NO_SHIP && GrapplemodUtils.vsLoaded()) {
			Vec world = ValkyrienSkiesIntegration.shipDirToWorld(this.world, shipId, normal);
			if (world != null) {
				return world;
			}
		}
		return normal;
	}

	public boolean hookPastBend(double ropelen) {
		return (this.getDistToFarthest() > ropelen);
	}

	public BlockPos getBendBlock(int index) {
		Vec inwardOffset = this.getNormal(this.segmentBottomSides.get(index)).changeLen(-this.intoBlock * 2)
				.add(this.getNormal(this.segmentTopSides.get(index)).changeLen(-this.intoBlock * 2));

		// ship bends: compute the block in shipyard space, where the bend's block actually lives
		long shipId = this.segmentShipIds.get(index);
		if (shipId != NO_SHIP) {
			Vec local = new Vec(this.segmentShipLocals.get(index));
			local.add_ip(inwardOffset);
			return BlockPos.containing(local.x, local.y, local.z);
		}

		Vec bendpos = new Vec(this.segments.get(index));
		bendpos.add_ip(inwardOffset);
		return BlockPos.containing(bendpos.x, bendpos.y, bendpos.z);
	}

	public void actuallyAddSegment(int index, Vec bendpoint, Direction bottomside, Direction topside, long shipId, Vec shipLocal) {
		segments.add(index, bendpoint);
		segmentBottomSides.add(index, bottomside);
		segmentTopSides.add(index, topside);
		segmentShipIds.add(index, shipId);
		segmentShipLocals.add(index, shipLocal);

		if (!this.world.isClientSide && this.hookEntity.shootingEntity != null) {
			SegmentMessage addmessage = new SegmentMessage(this.hookEntity.getId(), true, index, bendpoint, topside, bottomside, shipId, shipLocal != null ? shipLocal : new Vec(0, 0, 0));
			// TRACKING_ENTITY excludes the owning player, who simulates its own segments client-side
			CommonSetup.network.send(PacketDistributor.TRACKING_ENTITY.with(() -> this.hookEntity.shootingEntity), addmessage);
		}
	}

	public void print() {
		for (int i = 1; i < segments.size() - 1; i++) {
			GrappleMod.LOGGER.debug("segment {} {} {} {}", i, segmentTopSides.get(i), segmentBottomSides.get(i), segments.get(i));
		}
	}

	public Vec getClosest(Vec hookpos) {
		segments.set(0, hookpos);

		return segments.get(segments.size() - 2);
	}

	public double getDistToAnchor() {
		double dist = 0;
		for (int i = 0; i < segments.size() - 2; i++) {
			dist += segments.get(i).sub(segments.get(i+1)).length();
		}

		return dist;
	}

	public Vec getFarthest() {
		return segments.get(1);
	}

	public double getDistToFarthest() {
		double dist = 0;
		for (int i = 1; i < segments.size() - 1; i++) {
			dist += segments.get(i).sub(segments.get(i+1)).length();
		}

		return dist;
	}

	public double getDist(Vec hookpos, Vec playerpos) {
		segments.set(0, hookpos);
		segments.set(segments.size() - 1, playerpos);
		double dist = 0;
		for (int i = 0; i < segments.size() - 1; i++) {
			dist += segments.get(i).sub(segments.get(i+1)).length();
		}

		return dist;
	}

	/**
	 * Read-only: called from the render thread (culling), so it must not mutate the segment lists
	 * the tick threads are working with.
	 */
	public AABB getBoundingBox(Vec hookpos, Vec playerpos) {
		Vec minvec = new Vec(hookpos);
		Vec maxvec = new Vec(hookpos);
		List<Vec> points = new ArrayList<>(segments);
		points.add(playerpos);
		for (Vec segpos : points) {
			if (segpos == null) {
				continue;
			}
			if (segpos.x < minvec.x) {
				minvec.x = segpos.x;
			} else if (segpos.x > maxvec.x) {
				maxvec.x = segpos.x;
			}
			if (segpos.y < minvec.y) {
				minvec.y = segpos.y;
			} else if (segpos.y > maxvec.y) {
				maxvec.y = segpos.y;
			}
			if (segpos.z < minvec.z) {
				minvec.z = segpos.z;
			} else if (segpos.z > maxvec.z) {
				maxvec.z = segpos.z;
			}
		}
		return new AABB(minvec.x, minvec.y, minvec.z, maxvec.x, maxvec.y, maxvec.z);
	}
}
