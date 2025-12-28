package org.mtr.mod.peripheral;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.mod.block.BlockLiftButtons;
import org.mtr.mod.block.BlockLiftTrackFloor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LiftButtonsPeripheral implements IPeripheral {

	private final BlockLiftButtons.BlockEntity blockEntity;
	private final World world;
	private final BlockPos pos;

	public LiftButtonsPeripheral(BlockLiftButtons.BlockEntity blockEntity, World world, BlockPos pos) {
		this.blockEntity = blockEntity;
		this.world = world;
		this.pos = pos;
	}

	@Nonnull
	@Override
	public String getType() {
		return "mtr_lift_buttons";
	}

	@Override
	public void attach(@Nonnull IComputerAccess computer) {
	}

	@Override
	public void detach(@Nonnull IComputerAccess computer) {
	}

	// === Read Methods ===

	@LuaFunction(mainThread = true)
	public final List<Map<String, Integer>> getTrackPositions() {
		List<Map<String, Integer>> positions = new ArrayList<>();
		blockEntity.forEachTrackPosition(trackPos -> {
			Map<String, Integer> posData = new HashMap<>();
			posData.put("x", trackPos.getX());
			posData.put("y", trackPos.getY());
			posData.put("z", trackPos.getZ());
			positions.add(posData);
		});
		return positions;
	}

	@LuaFunction(mainThread = true)
	public final List<Map<String, Object>> getFloorDetails() {
		List<Map<String, Object>> floors = new ArrayList<>();
		blockEntity.forEachTrackPosition(trackPos -> {
			Map<String, Object> floorData = new HashMap<>();
			floorData.put("x", trackPos.getX());
			floorData.put("y", trackPos.getY());
			floorData.put("z", trackPos.getZ());

			// Try to get floor info from the BlockLiftTrackFloor at this position
			BlockEntity floorEntity = world.getBlockEntity(new BlockPos(trackPos.getX(), trackPos.getY(), trackPos.getZ()));
			if (floorEntity instanceof BlockLiftTrackFloor.BlockEntity liftFloor) {
				floorData.put("floorNumber", liftFloor.getFloorNumber());
				floorData.put("floorDescription", liftFloor.getFloorDescription());
				floorData.put("shouldDing", liftFloor.getShouldDing());
			} else {
				floorData.put("floorNumber", "");
				floorData.put("floorDescription", "");
				floorData.put("shouldDing", false);
			}

			floors.add(floorData);
		});
		return floors;
	}

	// === Write Methods ===

	@LuaFunction(mainThread = true)
	public final void registerFloor(int x, int y, int z) {
		blockEntity.registerFloor(new org.mtr.mapping.holder.BlockPos(x, y, z), true);
	}

	@LuaFunction(mainThread = true)
	public final void unregisterFloor(int x, int y, int z) {
		blockEntity.registerFloor(new org.mtr.mapping.holder.BlockPos(x, y, z), false);
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof LiftButtonsPeripheral && ((LiftButtonsPeripheral) other).pos.equals(this.pos);
	}
}
