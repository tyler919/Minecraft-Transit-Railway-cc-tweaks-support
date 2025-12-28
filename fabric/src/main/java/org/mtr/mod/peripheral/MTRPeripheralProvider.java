package org.mtr.mod.peripheral;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralLookup;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.mod.block.*;

import javax.annotation.Nullable;

public final class MTRPeripheralProvider {

	private MTRPeripheralProvider() {
	}

	public static void register() {
		PeripheralLookup.get().registerFallback((world, pos, state, blockEntity, direction) -> {
			return getPeripheral(blockEntity, world, pos);
		});
	}

	@Nullable
	private static IPeripheral getPeripheral(BlockEntity blockEntity, World world, BlockPos pos) {
		if (blockEntity == null) {
			return null;
		}

		// PIDS blocks
		if (blockEntity instanceof BlockPIDSBase.BlockEntityBase pidsEntity) {
			return new PIDSPeripheral(pidsEntity, world, pos);
		}

		// Train Sensor blocks (check specific subclasses first)
		if (blockEntity instanceof BlockTrainAnnouncer.BlockEntity announcerEntity) {
			return new TrainAnnouncerPeripheral(announcerEntity, world, pos);
		}

		if (blockEntity instanceof BlockTrainScheduleSensor.BlockEntity scheduleEntity) {
			return new TrainScheduleSensorPeripheral(scheduleEntity, world, pos);
		}

		if (blockEntity instanceof BlockTrainSensorBase.BlockEntityBase sensorEntity) {
			return new TrainSensorPeripheral(sensorEntity, world, pos);
		}

		// Signal blocks
		if (blockEntity instanceof BlockSignalBase.BlockEntityBase signalEntity) {
			return new SignalPeripheral(signalEntity, world, pos);
		}

		// Lift buttons
		if (blockEntity instanceof BlockLiftButtons.BlockEntity liftEntity) {
			return new LiftButtonsPeripheral(liftEntity, world, pos);
		}

		// Arrival Projectors
		if (blockEntity instanceof BlockArrivalProjectorBase.BlockEntityArrivalProjectorBase projectorEntity) {
			return new ArrivalProjectorPeripheral(projectorEntity, world, pos);
		}

		return null;
	}
}
