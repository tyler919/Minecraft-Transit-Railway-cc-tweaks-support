package org.mtr.mod.watcher;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Creates an issue on the fork via the GitHub REST API using the operator's token.
 * Java-8 safe (HttpsURLConnection, no HttpClient) so it compiles for every MTR target version.
 * Missing labels are created automatically by GitHub on issue creation.
 */
public final class IssueReporter {

	private static final Gson GSON = new Gson();

	private IssueReporter() {
	}

	/** POST a new issue; returns the html_url of the created issue. Throws on any non-201 response. */
	public static String createIssue(WatcherConfig config, String title, String body, List<String> labels) throws IOException {
		if (!config.hasToken()) {
			throw new IOException("No GitHub token configured in config/mtr-watcher.json");
		}

		final JsonObject payload = new JsonObject();
		payload.addProperty("title", title);
		payload.addProperty("body", body);
		if (labels != null && !labels.isEmpty()) {
			final JsonArray labelArray = new JsonArray();
			for (String label : labels) {
				labelArray.add(label);
			}
			payload.add("labels", labelArray);
		}

		final URL url = new URL("https://api.github.com/repos/" + config.githubOwner + "/" + config.githubRepo + "/issues");
		final HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
		try {
			connection.setRequestMethod("POST");
			connection.setConnectTimeout(15000);
			connection.setReadTimeout(20000);
			connection.setRequestProperty("Authorization", "Bearer " + config.githubToken.trim());
			connection.setRequestProperty("Accept", "application/vnd.github+json");
			connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
			connection.setRequestProperty("User-Agent", "mtr-cc-tweaks-watcher");
			connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			connection.setDoOutput(true);

			final byte[] out = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
			try (OutputStream os = connection.getOutputStream()) {
				os.write(out);
			}

			final int code = connection.getResponseCode();
			if (code == 201) {
				final JsonObject response = GSON.fromJson(read(connection.getInputStream()), JsonObject.class);
				return response != null && response.has("html_url") ? response.get("html_url").getAsString() : "(created)";
			}
			throw new IOException("GitHub returned HTTP " + code + ": " + read(connection.getErrorStream()));
		} finally {
			connection.disconnect();
		}
	}

	private static String read(InputStream stream) throws IOException {
		if (stream == null) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}
		}
		return sb.toString();
	}
}
