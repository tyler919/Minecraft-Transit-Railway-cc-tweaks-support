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

import org.mtr.mod.Init;
import net.minecraft.block.entity.BlockEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class PIDSPeripheral implements IPeripheral {

	protected final BlockPIDSBase.BlockEntityBase blockEntity;
	protected final BlockPIDSBase.BlockEntityBase dataBlockEntity; // The block entity that actually stores data
	protected final World world;
	protected final BlockPos pos;

	public PIDSPeripheral(BlockPIDSBase.BlockEntityBase blockEntity, World world, BlockPos pos) {
		this.blockEntity = blockEntity;
		this.world = world;
		this.pos = pos;

		// For multi-block PIDS, find the block entity that actually stores the data
		// (e.g., for vertical PIDS, only the UPPER half stores data)
		BlockPos dataPos = blockEntity.getBlockPosWithData.apply(
				new org.mtr.mapping.holder.World(world),
				new org.mtr.mapping.holder.BlockPos(pos)
		).data;

		if (dataPos.equals(pos)) {
			this.dataBlockEntity = blockEntity;
			Init.LOGGER.info("[MTR-CCT] PIDSPeripheral created at ({}, {}, {}) - this block stores data",
					pos.getX(), pos.getY(), pos.getZ());
		} else {
			// Get the block entity at the data position
			BlockEntity entity = world.getBlockEntity(dataPos);
			if (entity instanceof BlockPIDSBase.BlockEntityBase) {
				this.dataBlockEntity = (BlockPIDSBase.BlockEntityBase) entity;
				Init.LOGGER.info("[MTR-CCT] PIDSPeripheral created at ({}, {}, {}) - data stored at ({}, {}, {})",
						pos.getX(), pos.getY(), pos.getZ(),
						dataPos.getX(), dataPos.getY(), dataPos.getZ());
			} else {
				// Fallback to original block entity if we can't find the data block
				this.dataBlockEntity = blockEntity;
				Init.LOGGER.warn("[MTR-CCT] PIDSPeripheral at ({}, {}, {}) - could not find data block at ({}, {}, {}), using original",
						pos.getX(), pos.getY(), pos.getZ(),
						dataPos.getX(), dataPos.getY(), dataPos.getZ());
			}
		}
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
		return dataBlockEntity.maxArrivals;
	}

	@LuaFunction(mainThread = true)
	public final int getDisplayPage() {
		return dataBlockEntity.getDisplayPage();
	}

	@LuaFunction(mainThread = true)
	public final List<Long> getPlatformIds() {
		List<Long> ids = new ArrayList<>(dataBlockEntity.getPlatformIds());
		Init.LOGGER.info("[MTR-CCT] getPlatformIds() returning {} platform IDs", ids.size());
		return ids;
	}

	@LuaFunction(mainThread = true)
	public final String getMessage(int index) {
		return dataBlockEntity.getMessage(index - 1); // Lua is 1-indexed
	}

	@LuaFunction(mainThread = true)
	public final boolean getHideArrival(int index) {
		return dataBlockEntity.getHideArrival(index - 1); // Lua is 1-indexed
	}

	@LuaFunction(mainThread = true)
	public final List<Map<String, Object>> getArrivals() {
		Init.LOGGER.info("[MTR-CCT] getArrivals() called from PIDS at ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());

		if (world.isClient()) {
			Init.LOGGER.warn("[MTR-CCT] getArrivals() called on client side");
			return Collections.emptyList();
		}

		LongAVLTreeSet platformIds = dataBlockEntity.getPlatformIds();
		Init.LOGGER.info("[MTR-CCT] PIDS has {} platform IDs configured", platformIds.size());

		if (platformIds.isEmpty()) {
			Init.LOGGER.warn("[MTR-CCT] getArrivals() - no platform IDs configured on this PIDS");
			return Collections.emptyList();
		}

		ServerWorld serverWorld = (ServerWorld) world;
		ArrivalsCacheServer cache = ArrivalsCacheServer.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		ObjectArrayList<ArrivalResponse> arrivals = cache.requestArrivals(platformIds);

		Init.LOGGER.info("[MTR-CCT] getArrivals() returning {} arrivals", arrivals.size());
		return buildArrivalsResult(arrivals);
	}

	/**
	 * Blocking variant: fire a request and wait for the server response so a single call returns
	 * data, instead of getArrivals() which is empty until the async cache fills. mainThread = false.
	 */
	@LuaFunction(mainThread = false)
	public final List<Map<String, Object>> getArrivalsNow() {
		if (world.isClient()) {
			return Collections.emptyList();
		}
		LongAVLTreeSet platformIds = dataBlockEntity.getPlatformIds();
		if (platformIds == null || platformIds.isEmpty()) {
			Init.LOGGER.warn("[MTR-CCT] getArrivalsNow() - no platform IDs configured on this PIDS");
			return Collections.emptyList();
		}
		ServerWorld serverWorld = (ServerWorld) world;
		ArrivalsCacheServer cache = ArrivalsCacheServer.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		ObjectArrayList<ArrivalResponse> arrivals = cache.requestArrivalsBlocking(platformIds, 3000);
		Init.LOGGER.info("[MTR-CCT] getArrivalsNow() (blocking) returning {} arrivals", arrivals.size());
		return buildArrivalsResult(arrivals);
	}

	private List<Map<String, Object>> buildArrivalsResult(ObjectArrayList<ArrivalResponse> arrivals) {
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
		if (index < 1 || index > dataBlockEntity.maxArrivals) {
			return;
		}

		String[] messages = new String[dataBlockEntity.maxArrivals];
		boolean[] hideArrivals = new boolean[dataBlockEntity.maxArrivals];

		for (int i = 0; i < dataBlockEntity.maxArrivals; i++) {
			messages[i] = dataBlockEntity.getMessage(i);
			hideArrivals[i] = dataBlockEntity.getHideArrival(i);
		}

		messages[index - 1] = message != null ? message : "";

		dataBlockEntity.setData(messages, hideArrivals, dataBlockEntity.getPlatformIds(), dataBlockEntity.getDisplayPage());
	}

	@LuaFunction(mainThread = true)
	public final void setHideArrival(int index, boolean hide) {
		if (index < 1 || index > dataBlockEntity.maxArrivals) {
			return;
		}

		String[] messages = new String[dataBlockEntity.maxArrivals];
		boolean[] hideArrivals = new boolean[dataBlockEntity.maxArrivals];

		for (int i = 0; i < dataBlockEntity.maxArrivals; i++) {
			messages[i] = dataBlockEntity.getMessage(i);
			hideArrivals[i] = dataBlockEntity.getHideArrival(i);
		}

		hideArrivals[index - 1] = hide;

		dataBlockEntity.setData(messages, hideArrivals, dataBlockEntity.getPlatformIds(), dataBlockEntity.getDisplayPage());
	}

	@LuaFunction(mainThread = true)
	public final void setDisplayPage(int page) {
		String[] messages = new String[dataBlockEntity.maxArrivals];
		boolean[] hideArrivals = new boolean[dataBlockEntity.maxArrivals];

		for (int i = 0; i < dataBlockEntity.maxArrivals; i++) {
			messages[i] = dataBlockEntity.getMessage(i);
			hideArrivals[i] = dataBlockEntity.getHideArrival(i);
		}

		dataBlockEntity.setData(messages, hideArrivals, dataBlockEntity.getPlatformIds(), page);
	}

	@LuaFunction(mainThread = true)
	public final void setPlatformIds(List<?> platformIdList) {
		// CC:Tweaked requires wildcard generics on Lua parameters; convert here.
		String[] messages = new String[dataBlockEntity.maxArrivals];
		boolean[] hideArrivals = new boolean[dataBlockEntity.maxArrivals];

		for (int i = 0; i < dataBlockEntity.maxArrivals; i++) {
			messages[i] = dataBlockEntity.getMessage(i);
			hideArrivals[i] = dataBlockEntity.getHideArrival(i);
		}

		LongAVLTreeSet newPlatformIds = new LongAVLTreeSet();
		if (platformIdList != null) {
			for (Object platformId : platformIdList) {
				if (platformId instanceof Number) {
					newPlatformIds.add(((Number) platformId).longValue());
				}
			}
		}

		dataBlockEntity.setData(messages, hideArrivals, newPlatformIds, dataBlockEntity.getDisplayPage());
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof PIDSPeripheral && ((PIDSPeripheral) other).pos.equals(this.pos);
	}
}
