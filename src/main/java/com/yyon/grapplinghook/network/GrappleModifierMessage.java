package com.yyon.grapplinghook.network;

import com.yyon.grapplinghook.blocks.modifierblock.TileEntityGrappleModifier;
import com.yyon.grapplinghook.utils.GrappleCustomization;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

/*
 * This file is part of GrappleMod.

    GrappleMod is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    GrappleMod is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with GrappleMod.  If not, see <http://www.gnu.org/licenses/>.
 */

public class GrappleModifierMessage extends BaseMessageServer {
   
	public BlockPos pos;
	public GrappleCustomization custom;

    public GrappleModifierMessage(BlockPos pos, GrappleCustomization custom) {
    	this.pos = pos;
    	this.custom = custom;
    }

	public GrappleModifierMessage(FriendlyByteBuf buf) {
		super(buf);
	}

    public void decode(FriendlyByteBuf buf) {
    	this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
    	this.custom = new GrappleCustomization();
    	this.custom.readFromBuf(buf);
    }

    public void encode(FriendlyByteBuf buf) {
    	buf.writeInt(this.pos.getX());
    	buf.writeInt(this.pos.getY());
    	buf.writeInt(this.pos.getZ());
    	this.custom.writeToBuf(buf);
    }

    public void processMessage(NetworkEvent.Context ctx) {
		ServerPlayer player = ctx.getSender();
		if (player == null) {
			return;
		}
		Level w = player.level();

		// the player must actually be near the block they claim to be editing
		if (player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > 64) {
			return;
		}

		BlockEntity ent = w.getBlockEntity(this.pos);

		if (ent != null && ent instanceof TileEntityGrappleModifier) {
			TileEntityGrappleModifier tile = (TileEntityGrappleModifier) ent;
			tile.setCustomizationServer(validate(this.custom, tile, player));
		}
    }

	/**
	 * The GUI enforces unlocked categories and slider limits client-side only; re-apply the same
	 * rules here so a modified client can't set locked or out-of-range options.
	 */
	private static GrappleCustomization validate(GrappleCustomization requested, TileEntityGrappleModifier tile, ServerPlayer player) {
		boolean creative = player.isCreative();
		int limits = (creative || tile.isUnlocked(GrappleCustomization.upgradeCategories.LIMITS)) ? 1 : 0;

		GrappleCustomization result = new GrappleCustomization();
		for (String option : GrappleCustomization.booleanoptions) {
			boolean val = requested.getBoolean(option);
			if (val != result.getBoolean(option) && isOptionAllowed(option, tile, creative, limits)) {
				result.setBoolean(option, val);
			}
		}
		for (String option : GrappleCustomization.doubleoptions) {
			double val = requested.getDouble(option);
			if (val != result.getDouble(option) && isOptionAllowed(option, tile, creative, limits)) {
				double min = result.getMin(option, limits);
				double max = result.getMax(option, limits);
				result.setDouble(option, Math.max(min, Math.min(max, val)));
			}
		}
		return result;
	}

	private static boolean isOptionAllowed(String option, TileEntityGrappleModifier tile, boolean creative, int limits) {
		GrappleCustomization.upgradeCategories category = GrappleCustomization.getCategory(option);
		if (category != null && !creative && !tile.isUnlocked(category)) {
			return false;
		}
		// enabled: 0 = always, 1 = requires limits upgrade, 2 = disabled by config
		return GrappleCustomization.DEFAULT.optionEnabled(option) <= limits;
	}
}
