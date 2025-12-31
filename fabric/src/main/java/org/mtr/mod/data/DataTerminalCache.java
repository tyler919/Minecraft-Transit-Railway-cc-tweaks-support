package org.mtr.mod.data;

import org.mtr.core.data.*;
import org.mtr.core.operation.DataRequest;
import org.mtr.core.operation.DataResponse;
import org.mtr.core.operation.NearbyAreasRequest;
import org.mtr.core.operation.NearbyAreasResponse;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.MinecraftServerHelper;
import org.mtr.mod.Init;

import java.util.UUID;

public final class DataTerminalCache {

	private final World world;
	private long lastUpdateTime = 0;
	private static final long UPDATE_INTERVAL = 5000; // 5 seconds
	private static final long DEFAULT_WAIT_TIMEOUT = 5000; // 5 seconds max wait

	// Cached data
	private final ObjectArrayList<Station> cachedStations = new ObjectArrayList<>();
	private final ObjectArrayList<Depot> cachedDepots = new ObjectArrayList<>();
	private final ObjectArrayList<SimplifiedRoute> cachedRoutes = new ObjectArrayList<>();
	private boolean dataRequested = false;
	private volatile boolean dataReady = false; // True when data has been received at least once

	private static final Object2ObjectAVLTreeMap<String, DataTerminalCache> INSTANCES = new Object2ObjectAVLTreeMap<>();

	private DataTerminalCache(World world) {
		this.world = world;
	}

	/**
	 * Check if data has been received from the simulation core at least once.
	 * @return true if data is ready, false if still waiting for initial load
	 */
	public boolean isDataReady() {
		return dataReady;
	}

	/**
	 * Wait for data to be ready, with a default timeout of 5 seconds.
	 * @return true if data is ready, false if timeout occurred
	 */
	public boolean waitForData() {
		return waitForData(DEFAULT_WAIT_TIMEOUT);
	}

	/**
	 * Wait for data to be ready with a custom timeout.
	 * @param timeoutMs Maximum time to wait in milliseconds
	 * @return true if data is ready, false if timeout occurred
	 */
	public boolean waitForData(long timeoutMs) {
		// Trigger a data request if not already done
		requestDataIfNeeded();

		// If already ready, return immediately
		if (dataReady) {
			return true;
		}

		// Wait for data with timeout
		long startTime = System.currentTimeMillis();
		while (!dataReady && (System.currentTimeMillis() - startTime) < timeoutMs) {
			try {
				Thread.sleep(50); // Check every 50ms
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return dataReady;
			}
		}
		return dataReady;
	}

	/**
	 * Force an immediate data refresh, used for pre-populating cache on server startup.
	 */
	public void forceRefresh() {
		dataRequested = false;
		dataReady = false;
		requestDataIfNeeded();
	}

	public ObjectImmutableList<Station> getStations() {
		requestDataIfNeeded();
		synchronized (cachedStations) {
			return new ObjectImmutableList<>(cachedStations);
		}
	}

	public ObjectImmutableList<Depot> getDepots() {
		requestDataIfNeeded();
		synchronized (cachedDepots) {
			return new ObjectImmutableList<>(cachedDepots);
		}
	}

	public ObjectImmutableList<SimplifiedRoute> getRoutes() {
		requestDataIfNeeded();
		synchronized (cachedRoutes) {
			return new ObjectImmutableList<>(cachedRoutes);
		}
	}

	@SuppressWarnings("unchecked")
	public void getNearbyStations(Position position, java.util.function.Consumer<ObjectImmutableList<Station>> callback) {
		Init.sendMessageC2S(
				OperationProcessor.NEARBY_STATIONS,
				world.getServer(),
				world,
				new NearbyAreasRequest<>(position, 0),
				response -> callback.accept(((NearbyAreasResponse) response).getStations()),
				NearbyAreasResponse.class
		);
	}

	@SuppressWarnings("unchecked")
	public void getNearbyDepots(Position position, java.util.function.Consumer<ObjectImmutableList<Depot>> callback) {
		Init.sendMessageC2S(
				OperationProcessor.NEARBY_DEPOTS,
				world.getServer(),
				world,
				new NearbyAreasRequest<>(position, 0),
				response -> callback.accept(((NearbyAreasResponse) response).getDepots()),
				NearbyAreasResponse.class
		);
	}

	private void requestDataIfNeeded() {
		long currentTime = System.currentTimeMillis();
		if (!dataRequested || currentTime - lastUpdateTime > UPDATE_INTERVAL) {
			dataRequested = true;
			lastUpdateTime = currentTime;
			requestData();
		}
	}

	private void requestData() {
		Init.LOGGER.info("[MTR-CCT] DataTerminalCache: Sending GET_DATA request to Transport-Simulation-Core...");
		final long requestTime = System.currentTimeMillis();

		// Use GET_DATA with a very large radius to get all data
		Init.sendMessageC2S(
				OperationProcessor.GET_DATA,
				world.getServer(),
				world,
				new DataRequest(UUID.randomUUID(), new Position(0, 0, 0), Integer.MAX_VALUE),
				dataResponse -> {
					long responseTime = System.currentTimeMillis() - requestTime;
					Init.LOGGER.info("[MTR-CCT] DataTerminalCache: Received response after {}ms", responseTime);

					// Create temp ClientData to receive the response
					final ClientData tempClientData = new ClientData();
					new DataResponse(new JsonReader(Utilities.getJsonObjectFromData(dataResponse)), tempClientData).write();

					synchronized (cachedStations) {
						cachedStations.clear();
						cachedStations.addAll(tempClientData.stations);
					}
					synchronized (cachedDepots) {
						cachedDepots.clear();
						cachedDepots.addAll(tempClientData.depots);
					}
					synchronized (cachedRoutes) {
						cachedRoutes.clear();
						cachedRoutes.addAll(tempClientData.simplifiedRoutes);
					}

					// Mark data as ready - this signals waiting threads that data is available
					dataReady = true;
					Init.LOGGER.info("[MTR-CCT] DataTerminalCache: Data loaded successfully!");
					Init.LOGGER.info("[MTR-CCT]   Stations: {}", tempClientData.stations.size());
					Init.LOGGER.info("[MTR-CCT]   Depots: {}", tempClientData.depots.size());
					Init.LOGGER.info("[MTR-CCT]   Routes: {}", tempClientData.simplifiedRoutes.size());

					// Log station names for debugging
					if (!tempClientData.stations.isEmpty()) {
						Init.LOGGER.info("[MTR-CCT]   Station names: {}",
							tempClientData.stations.stream()
								.limit(5)
								.map(s -> s.getName())
								.reduce((a, b) -> a + ", " + b)
								.orElse("none"));
						if (tempClientData.stations.size() > 5) {
							Init.LOGGER.info("[MTR-CCT]   ... and {} more", tempClientData.stations.size() - 5);
						}
					}
				},
				DataResponse.class
		);
	}

	public static DataTerminalCache getInstance(ServerWorld serverWorld) {
		final World world = new World(serverWorld.data);
		return INSTANCES.computeIfAbsent(MinecraftServerHelper.getWorldId(world).data.toString(), worldId -> new DataTerminalCache(world));
	}

	/**
	 * Pre-initialize the cache for a world. Called during server startup to ensure
	 * data is available before any CC:T peripherals try to access it.
	 */
	public static void preInitialize(ServerWorld serverWorld) {
		DataTerminalCache cache = getInstance(serverWorld);
		cache.forceRefresh();
		Init.LOGGER.info("DataTerminalCache: Pre-initializing cache for world {}",
				MinecraftServerHelper.getWorldId(new World(serverWorld.data)));
	}

	/**
	 * Clear all cached instances. Called during server shutdown.
	 */
	public static void clearAll() {
		INSTANCES.clear();
	}

	public static void tickAll() {
		// Refresh caches periodically for all instances
		long currentTime = System.currentTimeMillis();
		for (DataTerminalCache cache : INSTANCES.values()) {
			if (currentTime - cache.lastUpdateTime > UPDATE_INTERVAL) {
				cache.requestDataIfNeeded();
			}
		}
	}
}
