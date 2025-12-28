package org.mtr.mod.peripheral;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.block.BlockPIDSBase;
import org.mtr.mod.data.ArrivalsCacheServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class PIDSPeripheral implements IPeripheral {

	protected final BlockPIDSBase.BlockEntityBase blockEntity;
	protected final World world;
	protected final BlockPos pos;

	public PIDSPeripheral(BlockPIDSBase.BlockEntityBase blockEntity, World world, BlockPos pos) {
		this.blockEntity = blockEntity;
		this.world = world;
		this.pos = pos;
	}

	@Nonnull
	@Override
	public String getType() {
		return "mtr_pids";
	}

	@Override
	public void attach(@Nonnull IComputerAccess computer) {
	}

	@Override
	public void detach(@Nonnull IComputerAccess computer) {
	}

	// === Read Methods ===

	@LuaFunction(mainThread = true)
	public final int getMaxArrivals() {
		return blockEntity.maxArrivals;
	}

	@LuaFunction(mainThread = true)
	public final int getDisplayPage() {
		return blockEntity.getDisplayPage();
	}

	@LuaFunction(mainThread = true)
	public final List<Long> getPlatformIds() {
		return new ArrayList<>(blockEntity.getPlatformIds());
	}

	@LuaFunction(mainThread = true)
	public final String getMessage(int index) {
		return blockEntity.getMessage(index - 1); // Lua is 1-indexed
	}

	@LuaFunction(mainThread = true)
	public final boolean getHideArrival(int index) {
		return blockEntity.getHideArrival(index - 1); // Lua is 1-indexed
	}

	@LuaFunction(mainThread = true)
	public final List<Map<String, Object>> getArrivals() {
		if (world.isClient()) {
			return Collections.emptyList();
		}

		LongAVLTreeSet platformIds = blockEntity.getPlatformIds();
		if (platformIds.isEmpty()) {
			return Collections.emptyList();
		}

		ServerWorld serverWorld = (ServerWorld) world;
		ArrivalsCacheServer cache = ArrivalsCacheServer.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		ObjectArrayList<ArrivalResponse> arrivals = cache.requestArrivals(platformIds);

		List<Map<String, Object>> result = new ArrayList<>();
		for (ArrivalResponse arrival : arrivals) {
			Map<String, Object> arrivalData = new HashMap<>();
			arrivalData.put("destination", arrival.getDestination());
			arrivalData.put("arrival", arrival.getArrival());
			arrivalData.put("departure", arrival.getDeparture());
			arrivalData.put("deviation", arrival.getDeviation());
			arrivalData.put("realtime", arrival.getRealtime());
			arrivalData.put("platformId", arrival.getPlatformId());
			arrivalData.put("platformName", arrival.getPlatformName());
			arrivalData.put("routeId", arrival.getRouteId());
			arrivalData.put("routeName", arrival.getRouteName());
			arrivalData.put("routeNumber", arrival.getRouteNumber());
			arrivalData.put("routeColor", arrival.getRouteColor());
			arrivalData.put("carCount", arrival.getCarCount());
			arrivalData.put("circularState", arrival.getCircularState().toString());
			result.add(arrivalData);
		}
		return result;
	}

	// === Write Methods ===

	@LuaFunction(mainThread = true)
	public final void setMessage(int index, String message) {
		if (index < 1 || index > blockEntity.maxArrivals) {
			return;
		}

		String[] messages = new String[blockEntity.maxArrivals];
		boolean[] hideArrivals = new boolean[blockEntity.maxArrivals];

		for (int i = 0; i < blockEntity.maxArrivals; i++) {
			messages[i] = blockEntity.getMessage(i);
			hideArrivals[i] = blockEntity.getHideArrival(i);
		}

		messages[index - 1] = message != null ? message : "";

		blockEntity.setData(messages, hideArrivals, blockEntity.getPlatformIds(), blockEntity.getDisplayPage());
	}

	@LuaFunction(mainThread = true)
	public final void setHideArrival(int index, boolean hide) {
		if (index < 1 || index > blockEntity.maxArrivals) {
			return;
		}

		String[] messages = new String[blockEntity.maxArrivals];
		boolean[] hideArrivals = new boolean[blockEntity.maxArrivals];

		for (int i = 0; i < blockEntity.maxArrivals; i++) {
			messages[i] = blockEntity.getMessage(i);
			hideArrivals[i] = blockEntity.getHideArrival(i);
		}

		hideArrivals[index - 1] = hide;

		blockEntity.setData(messages, hideArrivals, blockEntity.getPlatformIds(), blockEntity.getDisplayPage());
	}

	@LuaFunction(mainThread = true)
	public final void setDisplayPage(int page) {
		String[] messages = new String[blockEntity.maxArrivals];
		boolean[] hideArrivals = new boolean[blockEntity.maxArrivals];

		for (int i = 0; i < blockEntity.maxArrivals; i++) {
			messages[i] = blockEntity.getMessage(i);
			hideArrivals[i] = blockEntity.getHideArrival(i);
		}

		blockEntity.setData(messages, hideArrivals, blockEntity.getPlatformIds(), page);
	}

	@LuaFunction(mainThread = true)
	public final void setPlatformIds(List<Long> platformIdList) {
		String[] messages = new String[blockEntity.maxArrivals];
		boolean[] hideArrivals = new boolean[blockEntity.maxArrivals];

		for (int i = 0; i < blockEntity.maxArrivals; i++) {
			messages[i] = blockEntity.getMessage(i);
			hideArrivals[i] = blockEntity.getHideArrival(i);
		}

		LongAVLTreeSet newPlatformIds = new LongAVLTreeSet();
		if (platformIdList != null) {
			platformIdList.forEach(newPlatformIds::add);
		}

		blockEntity.setData(messages, hideArrivals, newPlatformIds, blockEntity.getDisplayPage());
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof PIDSPeripheral && ((PIDSPeripheral) other).pos.equals(this.pos);
	}
}
