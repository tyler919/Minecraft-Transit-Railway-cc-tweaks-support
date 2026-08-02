package org.mtr.mod.watcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.mtr.mod.Init;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads (and, on first run, writes a template for) config/mtr-watcher.json on the machine
 * the mod is running on. The GitHub token lives here — NEVER inside the shipped jar — so that
 * enabling the watcher on a remote server just means the operator drops in their own token.
 */
public final class WatcherConfig {

	public String githubOwner = "tyler919";
	public String githubRepo = "Minecraft-Transit-Railway-cc-tweaks-support";
	public String githubToken = "";
	public int pollSeconds = 5;
	public int maxIssuesPerHour = 12;

	public boolean hasToken() {
		return githubToken != null && !githubToken.trim().isEmpty();
	}

	public String repoPath() {
		return githubOwner + "/" + githubRepo;
	}

	/** config/mtr-watcher.json under the server run directory. */
	public static Path configPath(Path runDirectory) {
		return runDirectory.resolve("config").resolve("mtr-watcher.json");
	}

	/**
	 * Read the config; if the file does not exist, write a commented template with an empty
	 * token and return defaults so the operator knows exactly what to fill in.
	 */
	public static WatcherConfig load(Path runDirectory) {
		final Gson gson = new GsonBuilder().setPrettyPrinting().create();
		final Path path = configPath(runDirectory);
		final WatcherConfig config = new WatcherConfig();
		try {
			if (Files.exists(path)) {
				final String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
				final JsonObject json = gson.fromJson(text, JsonObject.class);
				if (json != null) {
					if (json.has("githubOwner")) config.githubOwner = json.get("githubOwner").getAsString();
					if (json.has("githubRepo")) config.githubRepo = json.get("githubRepo").getAsString();
					if (json.has("githubToken")) config.githubToken = json.get("githubToken").getAsString();
					if (json.has("pollSeconds")) config.pollSeconds = Math.max(2, json.get("pollSeconds").getAsInt());
					if (json.has("maxIssuesPerHour")) config.maxIssuesPerHour = Math.max(1, json.get("maxIssuesPerHour").getAsInt());
				}
			} else {
				Files.createDirectories(path.getParent());
				final JsonObject template = new JsonObject();
				template.addProperty("_comment", "MTR CC:Tweaked log watcher. Put a GitHub token with 'issues:write' on the fork here, then run /mtr watcher start.");
				template.addProperty("githubOwner", config.githubOwner);
				template.addProperty("githubRepo", config.githubRepo);
				template.addProperty("githubToken", "");
				template.addProperty("pollSeconds", config.pollSeconds);
				template.addProperty("maxIssuesPerHour", config.maxIssuesPerHour);
				Files.write(path, gson.toJson(template).getBytes(StandardCharsets.UTF_8));
				Init.LOGGER.info("[MTR-CCT] Wrote watcher config template to {}", path);
			}
		} catch (IOException | RuntimeException e) {
			Init.LOGGER.error("[MTR-CCT] Failed to load watcher config", e);
		}
		return config;
	}
}
