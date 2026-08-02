package org.mtr.mod.peripheral;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.core.data.*;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mod.block.BlockDataTerminal;
import org.mtr.mod.data.ArrivalsCacheServer;
import org.mtr.mod.data.DataTerminalCache;

import org.mtr.mod.Init;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class DataTerminalPeripheral implements IPeripheral {

	private final BlockDataTerminal.BlockEntity blockEntity;
	private final World world;
	private final BlockPos pos;

	public DataTerminalPeripheral(BlockDataTerminal.BlockEntity blockEntity, World world, BlockPos pos) {
		this.blockEntity = blockEntity;
		this.world = world;
		this.pos = pos;
		Init.LOGGER.info("[MTR-CCT] DataTerminalPeripheral created at ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());
	}

	@Nonnull
	@Override
	public String getType() {
		return "mtr_data_terminal";
	}

	@Override
	public void attach(@Nonnull IComputerAccess computer) {
		// Pre-warm the arrivals cache with all platform IDs when a computer connects
		if (!world.isClient()) {
			ServerWorld serverWorld = (ServerWorld) world;
			DataTerminalCache dataCache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
			ArrivalsCacheServer arrivalsCache = ArrivalsCacheServer.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));

			LongAVLTreeSet allPlatformIds = new LongAVLTreeSet();
			for (Station station : dataCache.getStations()) {
				for (Platform platform : station.savedRails) {
					allPlatformIds.add(platform.getId());
				}
			}

			// Start caching arrivals for all platforms
			if (!allPlatformIds.isEmpty()) {
				arrivalsCache.requestArrivals(allPlatformIds);
			}
		}
	}

	@Override
	public void detach(@Nonnull IComputerAccess computer) {
	}

	// === Station Methods ===

	@LuaFunction(mainThread = true)
	public final List<Map<String, Object>> getStations() {
		Init.LOGGER.info("[MTR-CCT] Lua called: getStations() from terminal at ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());

		if (world.isClient()) {
			Init.LOGGER.warn("[MTR-CCT] getStations() called on client side - returning empty");
			return Collections.emptyList();
		}

		ServerWorld serverWorld = (ServerWorld) world;
		DataTerminalCache cache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));

		Init.LOGGER.info("[MTR-CCT] Cache dataReady={}", cache.isDataReady());

		ObjectImmutableList<Station> stations = cache.getStations();

		Init.LOGGER.info("[MTR-CCT] getStations() returning {} stations", stations.size());

		List<Map<String, Object>> result = new ArrayList<>();
		for (Station station : stations) {
			Map<String, Object> stationData = new HashMap<>();
			stationData.put("id", station.getId());
			stationData.put("name", station.getName());
			stationData.put("color", station.getColor());
			stationData.put("zone1", station.getZone1());
			stationData.put("zone2", station.getZone2());
			stationData.put("zone3", station.getZone3());

			// Get platforms for this station
			List<Map<String, Object>> platforms = new ArrayList<>();
			for (Platform platform : station.savedRails) {
				Map<String, Object> platformData = new HashMap<>();
				platformData.put("id", platform.getId());
				platformData.put("name", platform.getName());
				platforms.add(platformData);
			}
			stationData.put("platforms", platforms);

			result.add(stationData);
		}
		return result;
	}

	// === Depot Methods ===

	@LuaFunction(mainThread = true)
	public final List<Map<String, Object>> getDepots() {
		if (world.isClient()) {
			return Collections.emptyList();
		}

		ServerWorld serverWorld = (ServerWorld) world;
		DataTerminalCache cache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		ObjectImmutableList<Depot> depots = cache.getDepots();

		List<Map<String, Object>> result = new ArrayList<>();
		for (Depot depot : depots) {
			Map<String, Object> depotData = new HashMap<>();
			depotData.put("id", depot.getId());
			depotData.put("name", depot.getName());
			depotData.put("color", depot.getColor());

			// Get sidings for this depot
			List<Map<String, Object>> sidings = new ArrayList<>();
			for (Siding siding : depot.savedRails) {
				Map<String, Object> sidingData = new HashMap<>();
				sidingData.put("id", siding.getId());
				sidingData.put("name", siding.getName());
				sidings.add(sidingData);
			}
			depotData.put("sidings", sidings);

			result.add(depotData);
		}
		return result;
	}

	// === Route Methods ===

	@LuaFunction(mainThread = true)
	public final List<Map<String, Object>> getRoutes() {
		if (world.isClient()) {
			return Collections.emptyList();
		}

		ServerWorld serverWorld = (ServerWorld) world;
		DataTerminalCache cache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		ObjectImmutableList<SimplifiedRoute> routes = cache.getRoutes();

		List<Map<String, Object>> result = new ArrayList<>();
		for (SimplifiedRoute route : routes) {
			Map<String, Object> routeData = new HashMap<>();
			routeData.put("id", route.getId());
			routeData.put("name", route.getName());
			routeData.put("color", route.getColor());
			routeData.put("circularState", route.getCircularState().toString());

			// Get platform IDs along this route
			List<Long> platformIds = new ArrayList<>();
			route.getPlatforms().forEach(platform -> platformIds.add(platform.getPlatformId()));
			routeData.put("platformIds", platformIds);

			result.add(routeData);
		}
		return result;
	}

	// === Arrivals Methods ===

	@LuaFunction(mainThread = true)
	public final List<Map<String, Object>> getArrivalsForPlatform(long platformId) {
		if (world.isClient()) {
			return Collections.emptyList();
		}

		LongAVLTreeSet platformIds = new LongAVLTreeSet();
		platformIds.add(platformId);

		ServerWorld serverWorld = (ServerWorld) world;
		ArrivalsCacheServer cache = ArrivalsCacheServer.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		ObjectArrayList<ArrivalResponse> arrivals = cache.requestArrivals(platformIds);

		return convertArrivalsToList(arrivals);
	}

	@LuaFunction(mainThread = true)
	public final List<Map<String, Object>> getArrivalsForPlatforms(List<?> platformIdList) {
		if (world.isClient() || platformIdList == null || platformIdList.isEmpty()) {
			return Collections.emptyList();
		}

		LongAVLTreeSet platformIds = toPlatformIdSet(platformIdList);

		ServerWorld serverWorld = (ServerWorld) world;
		ArrivalsCacheServer cache = ArrivalsCacheServer.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		ObjectArrayList<ArrivalResponse> arrivals = cache.requestArrivals(platformIds);

		return convertArrivalsToList(arrivals);
	}

	// === Blocking arrivals (single call returns data) ===
	// The non-blocking methods above return empty until the async request lands. These block the
	// (non-main) Lua thread until the server responds, so one call yields data — like waitForData().

	@LuaFunction(mainThread = false)
	public final List<Map<String, Object>> getArrivalsForPlatformNow(long platformId) {
		if (world.isClient()) {
			return Collections.emptyList();
		}
		LongAVLTreeSet platformIds = new LongAVLTreeSet();
		platformIds.add(platformId);
		return convertArrivalsToList(fetchArrivalsBlocking(platformIds));
	}

	@LuaFunction(mainThread = false)
	public final List<Map<String, Object>> getArrivalsForPlatformsNow(List<?> platformIdList) {
		if (world.isClient() || platformIdList == null || platformIdList.isEmpty()) {
			return Collections.emptyList();
		}
		return convertArrivalsToList(fetchArrivalsBlocking(toPlatformIdSet(platformIdList)));
	}

	// CC:Tweaked requires wildcard generics on Lua parameters; convert the list of numbers here.
	private static LongAVLTreeSet toPlatformIdSet(List<?> platformIdList) {
		LongAVLTreeSet platformIds = new LongAVLTreeSet();
		for (Object platformId : platformIdList) {
			if (platformId instanceof Number) {
				platformIds.add(((Number) platformId).longValue());
			}
		}
		return platformIds;
	}

	private ObjectArrayList<ArrivalResponse> fetchArrivalsBlocking(LongAVLTreeSet platformIds) {
		ServerWorld serverWorld = (ServerWorld) world;
		ArrivalsCacheServer cache = ArrivalsCacheServer.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		ObjectArrayList<ArrivalResponse> arrivals = cache.requestArrivalsBlocking(platformIds, 3000);
		Init.LOGGER.info("[MTR-CCT] getArrivals*Now blocking returned {} arrival(s) for {} platform(s)", arrivals.size(), platformIds.size());
		return arrivals;
	}

	// === Data Readiness Methods ===

	/**
	 * Check if the data cache has been populated.
	 * Returns true if station/depot/route data is available.
	 * Use this for non-blocking checks before calling data methods.
	 */
	@LuaFunction(mainThread = true)
	public final boolean isDataReady() {
		if (world.isClient()) {
			return false;
		}
		ServerWorld serverWorld = (ServerWorld) world;
		DataTerminalCache cache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		return cache.isDataReady();
	}

	/**
	 * Wait for the data cache to be populated with a default timeout of 5 seconds.
	 * Call this before getStations()/getDepots()/getRoutes() to ensure data is available.
	 * Returns true if data is ready, false if timeout occurred.
	 */
	@LuaFunction(mainThread = false) // Don't run on main thread since this blocks
	public final boolean waitForData() {
		Init.LOGGER.info("[MTR-CCT] Lua called: waitForData() from terminal at ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());

		if (world.isClient()) {
			Init.LOGGER.warn("[MTR-CCT] waitForData() called on client side - returning false");
			return false;
		}
		ServerWorld serverWorld = (ServerWorld) world;
		DataTerminalCache cache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));

		Init.LOGGER.info("[MTR-CCT] waitForData() starting wait, current dataReady={}", cache.isDataReady());
		boolean result = cache.waitForData();
		Init.LOGGER.info("[MTR-CCT] waitForData() finished, result={}", result);

		return result;
	}

	/**
	 * Wait for the data cache to be populated with a custom timeout.
	 * @param timeoutMs Maximum time to wait in milliseconds
	 * Returns true if data is ready, false if timeout occurred.
	 */
	@LuaFunction(mainThread = false) // Don't run on main thread since this blocks
	public final boolean waitForDataWithTimeout(long timeoutMs) {
		if (world.isClient()) {
			return false;
		}
		ServerWorld serverWorld = (ServerWorld) world;
		DataTerminalCache cache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		return cache.waitForData(timeoutMs);
	}

	// === Utility Methods ===

	/**
	 * Force a refresh of the station/depot/route data cache.
	 * Returns true when the refresh request has been sent.
	 * Use waitForData() afterwards to wait for the data to arrive.
	 */
	@LuaFunction(mainThread = true)
	public final boolean refreshData() {
		if (world.isClient()) {
			return false;
		}
		ServerWorld serverWorld = (ServerWorld) world;
		DataTerminalCache cache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		cache.forceRefresh();
		return true;
	}

	@LuaFunction(mainThread = true)
	public final int refreshArrivalsCache() {
		if (world.isClient()) {
			return 0;
		}

		ServerWorld serverWorld = (ServerWorld) world;
		DataTerminalCache dataCache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		ArrivalsCacheServer arrivalsCache = ArrivalsCacheServer.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));

		LongAVLTreeSet allPlatformIds = new LongAVLTreeSet();
		for (Station station : dataCache.getStations()) {
			for (Platform platform : station.savedRails) {
				allPlatformIds.add(platform.getId());
			}
		}

		// Request arrivals for all platforms to refresh cache
		if (!allPlatformIds.isEmpty()) {
			arrivalsCache.requestArrivals(allPlatformIds);
		}

		return allPlatformIds.size();
	}

	@LuaFunction(mainThread = true)
	public final Map<String, Object> getTerminalPosition() {
		Map<String, Object> position = new HashMap<>();
		position.put("x", pos.getX());
		position.put("y", pos.getY());
		position.put("z", pos.getZ());
		return position;
	}

	@LuaFunction(mainThread = true)
	public final int getStationCount() {
		if (world.isClient()) {
			return 0;
		}
		ServerWorld serverWorld = (ServerWorld) world;
		DataTerminalCache cache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		return cache.getStations().size();
	}

	@LuaFunction(mainThread = true)
	public final int getDepotCount() {
		if (world.isClient()) {
			return 0;
		}
		ServerWorld serverWorld = (ServerWorld) world;
		DataTerminalCache cache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		return cache.getDepots().size();
	}

	@LuaFunction(mainThread = true)
	public final int getRouteCount() {
		if (world.isClient()) {
			return 0;
		}
		ServerWorld serverWorld = (ServerWorld) world;
		DataTerminalCache cache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));
		return cache.getRoutes().size();
	}

	/**
	 * Get diagnostic information about the data terminal and cache status.
	 * Useful for debugging data flow issues.
	 * Returns a table with debug info that can be printed in Lua.
	 */
	@LuaFunction(mainThread = true)
	public final Map<String, Object> getDiagnostics() {
		Init.LOGGER.info("[MTR-CCT] Lua called: getDiagnostics() from terminal at ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());

		Map<String, Object> diagnostics = new HashMap<>();
		diagnostics.put("terminalX", pos.getX());
		diagnostics.put("terminalY", pos.getY());
		diagnostics.put("terminalZ", pos.getZ());
		diagnostics.put("isClientSide", world.isClient());
		diagnostics.put("timestamp", System.currentTimeMillis());

		if (!world.isClient()) {
			ServerWorld serverWorld = (ServerWorld) world;
			DataTerminalCache cache = DataTerminalCache.getInstance(new org.mtr.mapping.holder.ServerWorld(serverWorld));

			diagnostics.put("dataReady", cache.isDataReady());
			diagnostics.put("stationCount", cache.getStations().size());
			diagnostics.put("depotCount", cache.getDepots().size());
			diagnostics.put("routeCount", cache.getRoutes().size());

			// Count total platforms
			int platformCount = 0;
			for (Station station : cache.getStations()) {
				platformCount += station.savedRails.size();
			}
			diagnostics.put("platformCount", platformCount);

			// Get first few station names for verification
			List<String> stationNames = new ArrayList<>();
			for (Station station : cache.getStations()) {
				stationNames.add(station.getName());
				if (stationNames.size() >= 5) break;
			}
			diagnostics.put("sampleStations", stationNames);

			Init.LOGGER.info("[MTR-CCT] Diagnostics: dataReady={}, stations={}, depots={}, routes={}, platforms={}",
					cache.isDataReady(),
					cache.getStations().size(),
					cache.getDepots().size(),
					cache.getRoutes().size(),
					platformCount);
		}

		return diagnostics;
	}

	// === Helper Methods ===

	private List<Map<String, Object>> convertArrivalsToList(ObjectArrayList<ArrivalResponse> arrivals) {
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

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof DataTerminalPeripheral && ((DataTerminalPeripheral) other).pos.equals(this.pos);
	}
}
