package org.mtr.mod.data;

import org.mtr.core.operation.ArrivalResponse;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongCollection;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public abstract class ArrivalsCache {

	private long nextRequest;
	private final Long2IntAVLTreeMap queuedPlatformIdsWithAge = new Long2IntAVLTreeMap();
	private final ObjectArrayList<ArrivalResponse> arrivalResponseCache = new ObjectArrayList<>();
	private final int cachedMillis;
	private static final int PERSISTENT_AGE = 5;

	protected ArrivalsCache(int cachedMillis) {
		this.cachedMillis = cachedMillis;
	}

	public final ObjectArrayList<ArrivalResponse> requestArrivals(LongCollection platformIds) {
		if (queuedPlatformIdsWithAge.isEmpty() && canSendRequest()) {
			nextRequest = System.currentTimeMillis() + 100;
		}

		platformIds.forEach(platformId -> queuedPlatformIdsWithAge.put(platformId, 0));

		final ObjectArrayList<ArrivalResponse> arrivals = new ObjectArrayList<>();
		arrivalResponseCache.forEach(arrivalResponse -> {
			if (platformIds.contains(arrivalResponse.getPlatformId())) {
				arrivals.add(arrivalResponse);
			}
		});

		return arrivals;
	}

	/**
	 * Synchronous arrivals fetch: fire a single request and BLOCK the calling thread until the
	 * server responds (or the timeout elapses), then return the arrivals for the requested
	 * platforms. This mirrors DataTerminalCache.waitForData() so a single Lua call returns data,
	 * instead of the non-blocking requestArrivals() which returns empty until tick() fills the
	 * cache. MUST be called off the main server thread (LuaFunction mainThread = false).
	 */
	public final ObjectArrayList<ArrivalResponse> requestArrivalsBlocking(LongCollection platformIds, long timeoutMs) {
		final ObjectArrayList<ArrivalResponse> holder = new ObjectArrayList<>();
		final CountDownLatch latch = new CountDownLatch(1);

		requestArrivalsFromServer(new LongAVLTreeSet(platformIds), arrivalResponseList -> {
			// refresh the shared cache too, so later non-blocking calls benefit
			arrivalResponseCache.clear();
			arrivalResponseCache.addAll(arrivalResponseList);
			holder.addAll(arrivalResponseList);
			latch.countDown();
		});

		try {
			latch.await(timeoutMs, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		final ObjectArrayList<ArrivalResponse> result = new ObjectArrayList<>();
		holder.forEach(arrivalResponse -> {
			if (platformIds.contains(arrivalResponse.getPlatformId())) {
				result.add(arrivalResponse);
			}
		});
		return result;
	}

	public final void tick() {
		if (!queuedPlatformIdsWithAge.isEmpty() && canSendRequest()) {
			final LongAVLTreeSet platformIds = new LongAVLTreeSet(queuedPlatformIdsWithAge.keySet());

			requestArrivalsFromServer(platformIds, arrivalResponseList -> {
				arrivalResponseCache.clear();
				arrivalResponseCache.addAll(arrivalResponseList);
			});

			platformIds.forEach(platformId -> queuedPlatformIdsWithAge.compute(platformId, (key, age) -> age > PERSISTENT_AGE ? null : age + 1));
			nextRequest = System.currentTimeMillis() + cachedMillis;
		}
	}

	private boolean canSendRequest() {
		return System.currentTimeMillis() >= nextRequest;
	}

	public abstract long getMillisOffset();

	protected abstract void requestArrivalsFromServer(LongAVLTreeSet platformIds, Consumer<ObjectList<ArrivalResponse>> callback);
}
