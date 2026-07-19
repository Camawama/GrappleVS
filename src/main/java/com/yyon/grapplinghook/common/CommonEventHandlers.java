package com.yyon.grapplinghook.common;

import com.yyon.grapplinghook.GrappleMod;
import com.yyon.grapplinghook.config.GrappleConfig;
import com.yyon.grapplinghook.entities.grapplehook.GrapplehookEntity;
import com.yyon.grapplinghook.items.GrapplehookItem;
import com.yyon.grapplinghook.items.LongFallBoots;
import com.yyon.grapplinghook.network.GrappleDetachMessage;
import com.yyon.grapplinghook.network.LoggedInMessage;
import com.yyon.grapplinghook.server.ServerControllerManager;
import com.yyon.grapplinghook.utils.GrapplemodUtils;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.yyon.grapplinghook.utils.Vec;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;

public class CommonEventHandlers {
	public CommonEventHandlers() {
	    MinecraftForge.EVENT_BUS.register(this);

		AutoConfig.register(GrappleConfig.class, Toml4jConfigSerializer<GrappleConfig>::new);
	}

	/**
	 * Lead overhaul: leashed mobs swing on the lead instead of vanilla's jerky elastic bounce.
	 * When the lead is stretched past its natural length, the outward velocity component is
	 * dropped (so the mob pendulums around the holder) and a bedrock-style spring eases it back.
	 * Vanilla attach rules and the 10-block break distance are untouched.
	 */
	@SubscribeEvent
	public void onLivingTick(LivingEvent.LivingTickEvent event) {
		if (event.getEntity().level().isClientSide) {
			return;
		}
		if (!(event.getEntity() instanceof Mob)) {
			return;
		}
		if (!GrappleConfig.getConf().leads.overhaul_enabled) {
			return;
		}
		Mob mob = (Mob) event.getEntity();
		Entity holder = mob.getLeashHolder();
		if (holder == null || holder.level() != mob.level()) {
			return;
		}

		final double leadLength = 6.0;
		Vec holderPos = new Vec(holder.getRopeHoldPosition(1.0F));
		Vec mobPos = Vec.positionVec(mob).add(new Vec(0, mob.getBbHeight() * 0.7, 0));
		Vec spherevec = mobPos.sub(holderPos);
		double dist = spherevec.length();
		if (dist <= leadLength) {
			return;
		}

		Vec motion = Vec.motionVec(mob);
		// swinging: keep tangential velocity, drop the outward component
		if (motion.dot(spherevec) > 0) {
			motion = motion.removeAlong(spherevec);
		}
		// spring back toward lead length, softened so the drag looks smooth
		Vec spherechange = spherevec.changeLen(leadLength).sub(spherevec);
		motion = motion.add(spherechange.mult(0.2));
		mob.setDeltaMovement(motion.toVec3d());
		mob.hurtMarked = true;
	}

	@SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
    	Player player = event.getPlayer();
    	if (player != null) {
	    	ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
	    	if (stack != null) {
	    		Item item = stack.getItem();
	    		if (item instanceof GrapplehookItem) {
	    			event.setCanceled(true);
	    			return;
	    		}
	    	}
    	}
    }
    
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
    	if (!event.getEntity().level().isClientSide) {
    		Entity entity = event.getEntity();
    		int id = entity.getId();
    		boolean isconnected = ServerControllerManager.allGrapplehookEntities.containsKey(id);
    		if (isconnected) {
    			HashSet<GrapplehookEntity> grapplehookEntities = ServerControllerManager.allGrapplehookEntities.get(id);
    			for (GrapplehookEntity hookEntity: grapplehookEntities) {
    				hookEntity.removeServer();
    			}
    			grapplehookEntities.clear();

    			ServerControllerManager.attached.remove(id);
    			
    			if (GrapplehookItem.grapplehookEntitiesLeft.containsKey(entity)) {
    				GrapplehookItem.grapplehookEntitiesLeft.remove(entity);
    			}
    			if (GrapplehookItem.grapplehookEntitiesRight.containsKey(entity)) {
    				GrapplehookItem.grapplehookEntitiesRight.remove(entity);
    			}
    			
    			GrapplemodUtils.sendToCorrectClient(new GrappleDetachMessage(id), id, entity.level());
    		}
    	}
	}
	
	@SubscribeEvent
	public void onLivingHurtEvent(LivingHurtEvent event) {
		if (event.getEntity() != null && event.getEntity() instanceof Player) {
			Player player = (Player)event.getEntity();
			
			for (ItemStack armor : player.getArmorSlots()) {
			    if (armor != null && armor.getItem() instanceof LongFallBoots)
			    {
			    	if (event.getSource() == event.getEntity().level().damageSources().flyIntoWall()) {
						// this cancels the fall event so you take no damage
						event.setCanceled(true);
			    	}
			    }
			}
		}
	}
	
	@SubscribeEvent
	public void onLivingFallEvent(LivingFallEvent event) {
		if (event.getEntity() != null && event.getEntity() instanceof Player) {
			Player player = (Player)event.getEntity();
			
			for (ItemStack armor : player.getArmorSlots()) {
			    if (armor != null && armor.getItem() instanceof LongFallBoots)
			    {
					// this cancels the fall event so you take no damage
					event.setCanceled(true);
			    }
			}
		}
	}

	@SubscribeEvent
	public void onServerStart(ServerStartedEvent event) {
		if (GrappleConfig.getConf().other.override_allowflight) {
			event.getServer().setFlightAllowed(true);
		}
	}
	
	@SubscribeEvent
	public void onPlayerLoggedInEvent(PlayerLoggedInEvent e) {
		if (e.getEntity() instanceof ServerPlayer) {
			CommonSetup.network.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) e.getEntity()), new LoggedInMessage(GrappleConfig.getConf()));
		} else {
			GrappleMod.LOGGER.warn("Logged-in entity is not a ServerPlayer");
		}
	}

	// the static hook/controller maps are keyed by entity/entity-id; clean them up on logout
	// so they don't leak player and hook references across sessions
	@SubscribeEvent
	public void onPlayerLoggedOutEvent(PlayerLoggedOutEvent e) {
		if (!(e.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		int id = player.getId();
		ServerControllerManager.attached.remove(id);
		HashSet<GrapplehookEntity> hooks = ServerControllerManager.allGrapplehookEntities.remove(id);
		if (hooks != null) {
			for (GrapplehookEntity hookEntity : hooks) {
				if (hookEntity != null && hookEntity.isAlive()) {
					hookEntity.removeServer();
				}
			}
		}
		GrapplehookItem.grapplehookEntitiesLeft.remove(player);
		GrapplehookItem.grapplehookEntitiesRight.remove(player);
	}
}
