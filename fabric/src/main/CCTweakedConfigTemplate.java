package org.mtr.init;

import net.fabricmc.loader.api.FabricLoader;
import org.mtr.mod.Init;
import org.mtr.mod.peripheral.MTRPeripheralProvider;

public final class CCTweakedConfig {

	private static boolean registered = false;

	public static void register() {
		if (registered) {
			return;
		}
		if (FabricLoader.getInstance().isModLoaded("computercraft")) {
			try {
				MTRPeripheralProvider.register();
				Init.LOGGER.info("CC: Tweaked detected - MTR peripherals registered");
				registered = true;
			} catch (Exception e) {
				Init.LOGGER.error("Failed to register CC: Tweaked peripherals", e);
			}
		}
	}
}
