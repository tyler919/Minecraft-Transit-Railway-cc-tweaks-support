package org.mtr.mod.peripheral;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.mod.block.BlockTrainScheduleSensor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TrainScheduleSensorPeripheral extends TrainSensorPeripheral {

	private final BlockTrainScheduleSensor.BlockEntity scheduleEntity;

	public TrainScheduleSensorPeripheral(BlockTrainScheduleSensor.BlockEntity blockEntity, World world, BlockPos pos) {
		super(blockEntity, world, pos);
		this.scheduleEntity = blockEntity;
	}

	@Nonnull
	@Override
	public String getType() {
		return "mtr_train_schedule_sensor";
	}

	// === Read Methods ===

	@LuaFunction(mainThread = true)
	public final int getSeconds() {
		return scheduleEntity.getSeconds();
	}

	@LuaFunction(mainThread = true)
	public final boolean getRealtimeOnly() {
		return scheduleEntity.getRealtimeOnly();
	}

	// === Write Methods ===

	@LuaFunction(mainThread = true)
	public final void setScheduleData(int seconds, boolean realtimeOnly) {
		scheduleEntity.setData(
				blockEntity.getRouteIds(),
				blockEntity.getStoppedOnly(),
				blockEntity.getMovingOnly(),
				seconds,
				realtimeOnly
		);
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof TrainScheduleSensorPeripheral && ((TrainScheduleSensorPeripheral) other).pos.equals(this.pos);
	}
}
