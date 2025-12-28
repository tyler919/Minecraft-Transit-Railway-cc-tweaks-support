package org.mtr.mod.peripheral;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.mod.block.BlockTrainAnnouncer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TrainAnnouncerPeripheral extends TrainSensorPeripheral {

	private final BlockTrainAnnouncer.BlockEntity announcerEntity;

	public TrainAnnouncerPeripheral(BlockTrainAnnouncer.BlockEntity blockEntity, World world, BlockPos pos) {
		super(blockEntity, world, pos);
		this.announcerEntity = blockEntity;
	}

	@Nonnull
	@Override
	public String getType() {
		return "mtr_train_announcer";
	}

	// === Read Methods ===

	@LuaFunction(mainThread = true)
	public final String getMessage() {
		return announcerEntity.getMessage();
	}

	@LuaFunction(mainThread = true)
	public final String getSoundId() {
		return announcerEntity.getSoundId();
	}

	@LuaFunction(mainThread = true)
	public final int getDelay() {
		return announcerEntity.getDelay();
	}

	// === Write Methods ===

	@LuaFunction(mainThread = true)
	public final void setAnnouncerData(String message, String soundId, int delay) {
		announcerEntity.setData(
				blockEntity.getRouteIds(),
				blockEntity.getStoppedOnly(),
				blockEntity.getMovingOnly(),
				message != null ? message : "",
				soundId != null ? soundId : "",
				delay
		);
	}

	@LuaFunction(mainThread = true)
	public final void triggerAnnounce() {
		// Note: This only works on client-side for audio playback
		// Server-side it queues the announcement
		announcerEntity.announce();
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof TrainAnnouncerPeripheral && ((TrainAnnouncerPeripheral) other).pos.equals(this.pos);
	}
}
