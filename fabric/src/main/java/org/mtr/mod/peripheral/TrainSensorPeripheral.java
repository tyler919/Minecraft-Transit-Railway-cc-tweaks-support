package org.mtr.mod.peripheral;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.mod.block.BlockTrainSensorBase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class TrainSensorPeripheral implements IPeripheral {

	protected final BlockTrainSensorBase.BlockEntityBase blockEntity;
	protected final World world;
	protected final BlockPos pos;

	public TrainSensorPeripheral(BlockTrainSensorBase.BlockEntityBase blockEntity, World world, BlockPos pos) {
		this.blockEntity = blockEntity;
		this.world = world;
		this.pos = pos;
	}

	@Nonnull
	@Override
	public String getType() {
		return "mtr_train_sensor";
	}

	@Override
	public void attach(@Nonnull IComputerAccess computer) {
	}

	@Override
	public void detach(@Nonnull IComputerAccess computer) {
	}

	// === Read Methods ===

	@LuaFunction(mainThread = true)
	public final List<Long> getFilterRouteIds() {
		return new ArrayList<>(blockEntity.getRouteIds());
	}

	@LuaFunction(mainThread = true)
	public final boolean getStoppedOnly() {
		return blockEntity.getStoppedOnly();
	}

	@LuaFunction(mainThread = true)
	public final boolean getMovingOnly() {
		return blockEntity.getMovingOnly();
	}

	// === Write Methods ===

	@LuaFunction(mainThread = true)
	public final void setFilterRouteIds(List<Long> routeIds) {
		LongAVLTreeSet newRouteIds = new LongAVLTreeSet();
		if (routeIds != null) {
			routeIds.forEach(newRouteIds::add);
		}
		blockEntity.setData(newRouteIds, blockEntity.getStoppedOnly(), blockEntity.getMovingOnly());
	}

	@LuaFunction(mainThread = true)
	public final void setStoppedOnly(boolean stoppedOnly) {
		blockEntity.setData(blockEntity.getRouteIds(), stoppedOnly, blockEntity.getMovingOnly());
	}

	@LuaFunction(mainThread = true)
	public final void setMovingOnly(boolean movingOnly) {
		blockEntity.setData(blockEntity.getRouteIds(), blockEntity.getStoppedOnly(), movingOnly);
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof TrainSensorPeripheral && ((TrainSensorPeripheral) other).pos.equals(this.pos);
	}
}
