package org.mtr.mod.peripheral;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.mod.block.BlockArrivalProjectorBase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ArrivalProjectorPeripheral extends PIDSPeripheral {

	public ArrivalProjectorPeripheral(BlockArrivalProjectorBase.BlockEntityArrivalProjectorBase blockEntity, World world, BlockPos pos) {
		super(blockEntity, world, pos);
	}

	@Nonnull
	@Override
	public String getType() {
		return "mtr_arrival_projector";
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof ArrivalProjectorPeripheral && ((ArrivalProjectorPeripheral) other).pos.equals(this.pos);
	}
}
