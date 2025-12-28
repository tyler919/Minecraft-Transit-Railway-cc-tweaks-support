package org.mtr.mod.peripheral;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.mod.block.BlockLiftTrackFloor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class LiftTrackFloorPeripheral implements IPeripheral {

	private final BlockLiftTrackFloor.BlockEntity blockEntity;
	private final World world;
	private final BlockPos pos;

	public LiftTrackFloorPeripheral(BlockLiftTrackFloor.BlockEntity blockEntity, World world, BlockPos pos) {
		this.blockEntity = blockEntity;
		this.world = world;
		this.pos = pos;
	}

	@Nonnull
	@Override
	public String getType() {
		return "mtr_lift_track_floor";
	}

	@Override
	public void attach(@Nonnull IComputerAccess computer) {
	}

	@Override
	public void detach(@Nonnull IComputerAccess computer) {
	}

	// === Read Methods ===

	@LuaFunction(mainThread = true)
	public final String getFloorNumber() {
		return blockEntity.getFloorNumber();
	}

	@LuaFunction(mainThread = true)
	public final String getFloorDescription() {
		return blockEntity.getFloorDescription();
	}

	@LuaFunction(mainThread = true)
	public final boolean getShouldDing() {
		return blockEntity.getShouldDing();
	}

	// === Write Methods ===

	@LuaFunction(mainThread = true)
	public final void setFloorData(String floorNumber, String floorDescription, boolean shouldDing) {
		blockEntity.setData(
				floorNumber != null ? floorNumber : "1",
				floorDescription != null ? floorDescription : "",
				shouldDing
		);
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof LiftTrackFloorPeripheral && ((LiftTrackFloorPeripheral) other).pos.equals(this.pos);
	}
}
