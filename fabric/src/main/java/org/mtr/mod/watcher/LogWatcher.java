package org.mtr.mod.watcher;

import org.mtr.mod.Init;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-mod log watcher. Enabled per-session with /mtr watcher start. While running it tails the
 * server's own logs/latest.log, detects crashes and [MTR-CCT] errors, de-dups by a normalized
 * signature, and opens a de-duplicated issue on the fork. Runs entirely on the machine hosting
 * the mod, so bug reports reach the fork from servers the maintainer has no direct access to.
 */
public final class LogWatcher {

	private static final LogWatcher INSTANCE = new LogWatcher();

	private static final Pattern LINE = Pattern.compile("^\\[[\\d:]+]\\s*\\[[^/\\]]+/([A-Z]+)]:?\\s?(.*)$");
	private static final Pattern AT_LINE = Pattern.compile("^\\s+at\\s+[\\w.$]+");
	private static final Pattern CAUSED = Pattern.compile("^(Caused by:|\\s*\\.\\.\\.\\s*\\d+\\s*more)");
	private static final Pattern EXCEPTION = Pattern.compile("([\\w.$]+(?:Exception|Error|Throwable))(?::\\s*(.*))?");
	private static final Pattern VOLATILE = Pattern.compile("(0x[0-9a-fA-F]+|@[0-9a-fA-F]+|\\d+|\\([^)]*\\.java:\\d+\\))");

	private volatile boolean running = false;
	private Thread thread;
	private Path logFile;
	private WatcherConfig config;
	private long offset = 0;
	private int filedCount = 0;
	private final Set<String> seen = Collections.synchronizedSet(new HashSet<>());
	private final Deque<Long> recentIssueMillis = new ArrayDeque<>();

	private LogWatcher() {
	}

	public static LogWatcher getInstance() {
		return INSTANCE;
	}

	public boolean isRunning() {
		return running;
	}

	public synchronized boolean start(Path runDirectory, Consumer<String> feedback) {
		if (running) {
			feedback.accept("Watcher is already running.");
			return false;
		}
		config = WatcherConfig.load(runDirectory);
		if (!config.hasToken()) {
			feedback.accept("No GitHub token set. Add one to " + WatcherConfig.configPath(runDirectory) + " then run /mtr watcher start again.");
			return false;
		}
		logFile = runDirectory.resolve("logs").resolve("latest.log");
		final File file = logFile.toFile();
		offset = file.exists() ? file.length() : 0; // baseline: only report errors from here on
		running = true;
		thread = new Thread(this::pollLoop, "mtr-log-watcher");
		thread.setDaemon(true);
		thread.start();
		Init.LOGGER.info("[MTR-CCT] Watcher started; reporting to {}", config.repoPath());
		feedback.accept("Watcher started. Reporting crashes + [MTR-CCT] errors to " + config.repoPath() + ".");
		return true;
	}

	public synchronized void stop(Consumer<String> feedback) {
		if (!running) {
			if (feedback != null) feedback.accept("Watcher is not running.");
			return;
		}
		running = false;
		if (thread != null) {
			thread.interrupt();
			thread = null;
		}
		Init.LOGGER.info("[MTR-CCT] Watcher stopped");
		if (feedback != null) feedback.accept("Watcher stopped. Filed " + filedCount + " issue(s) this session.");
	}

	public void status(Consumer<String> feedback) {
		if (running) {
			feedback.accept("Watcher: RUNNING -> " + config.repoPath() + " | issues filed this session: " + filedCount);
		} else {
			feedback.accept("Watcher: stopped.");
		}
	}

	/** /mtr watcher test — verify the token + connectivity end to end. */
	public void test(Path runDirectory, Consumer<String> feedback) {
		final WatcherConfig cfg = running ? config : WatcherConfig.load(runDirectory);
		if (!cfg.hasToken()) {
			feedback.accept("No GitHub token set in " + WatcherConfig.configPath(runDirectory) + ".");
			return;
		}
		fileNow(cfg, "Watcher test — pipe check",
				"This is a test issue from `/mtr watcher test` confirming the watcher can reach the fork.",
				Collections.singletonList("auto-report"), feedback);
	}

	/**
	 * /mtr watcher report — grab the last ~1000 characters of the log, whatever they are, and open
	 * an issue labeled only 'log' (no bug label). A manual snapshot, independent of start/stop.
	 */
	public void report(Path runDirectory, Consumer<String> feedback) {
		final WatcherConfig cfg = running ? config : WatcherConfig.load(runDirectory);
		if (!cfg.hasToken()) {
			feedback.accept("No GitHub token set in " + WatcherConfig.configPath(runDirectory) + ".");
			return;
		}
		final Path file = runDirectory.resolve("logs").resolve("latest.log");
		final String tail = readTail(file.toFile(), 1000);
		if (tail.isEmpty()) {
			feedback.accept("Could not read " + file + ".");
			return;
		}
		final String body = "**Manual log snapshot** (`/mtr watcher report`) — last ~1000 characters of the log.\n\n```\n" + tail + "\n```";
		fileNow(cfg, "Log snapshot", body, Collections.singletonList("log"), feedback);
	}

	// --- internals ---------------------------------------------------------

	private void pollLoop() {
		while (running) {
			try {
				Thread.sleep(Math.max(2, config.pollSeconds) * 1000L);
				scanNew();
			} catch (InterruptedException e) {
				return;
			} catch (Throwable t) {
				Init.LOGGER.error("[MTR-CCT] Watcher poll error", t);
			}
		}
	}

	private void scanNew() {
		final File file = logFile.toFile();
		if (!file.exists()) {
			return;
		}
		final long size = file.length();
		if (size < offset) {
			offset = 0; // log rotated / truncated
		}
		if (size == offset) {
			return;
		}
		final String chunk;
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
			raf.seek(offset);
			final byte[] buffer = new byte[(int) Math.min(size - offset, 1_000_000)];
			raf.readFully(buffer);
			chunk = new String(buffer, StandardCharsets.UTF_8);
			offset += buffer.length;
		} catch (IOException e) {
			return;
		}
		final String[] lines = chunk.split("\r?\n", -1);
		for (Event event : detect(lines)) {
			if (!seen.add(event.signature)) {
				continue;
			}
			if (!allowRate()) {
				Init.LOGGER.warn("[MTR-CCT] Watcher rate cap reached ({}/hr); deferring further reports", config.maxIssuesPerHour);
				break;
			}
			try {
				final String url = IssueReporter.createIssue(config, event.title, event.body(), Collections.singletonList("auto-report"));
				filedCount++;
				Init.LOGGER.info("[MTR-CCT] Watcher filed issue: {}", url);
			} catch (IOException e) {
				Init.LOGGER.error("[MTR-CCT] Watcher failed to file issue", e);
			}
		}
	}

	private List<Event> detect(String[] lines) {
		final List<Event> events = new ArrayList<>();
		int i = 0;
		while (i < lines.length) {
			final String line = lines[i];
			final Matcher lineMatcher = LINE.matcher(line);
			final boolean matched = lineMatcher.matches();
			final String level = matched ? lineMatcher.group(1) : "";
			final String message = matched ? lineMatcher.group(2) : line;

			if (line.contains("Preparing crash report") || line.contains("crash-reports/crash-")) {
				final List<String> block = collectTrace(lines, i);
				events.add(new Event("crash", "Server crash detected", message, block));
				i += block.size();
				continue;
			}

			final boolean mentionsCct = line.contains("[MTR-CCT]") || line.contains("org.mtr.mod.peripheral");
			final boolean looksError = "ERROR".equals(level) || "FATAL".equals(level) || EXCEPTION.matcher(message).find();
			if (mentionsCct && looksError) {
				final List<String> block = collectTrace(lines, i);
				final Matcher exc = EXCEPTION.matcher(String.join("\n", block));
				final String shortName = exc.find() ? lastSegment(exc.group(1)) : trim(message, 60);
				events.add(new Event("cct", trim("CC:Tweaked error: " + shortName, 110),
						exc.reset().find() ? exc.group(0) : message, block));
				i += block.size();
				continue;
			}
			i++;
		}
		return events;
	}

	private List<String> collectTrace(String[] lines, int start) {
		final List<String> block = new ArrayList<>();
		block.add(lines[start]);
		int j = start + 1;
		while (j < lines.length && block.size() < 40
				&& (AT_LINE.matcher(lines[j]).find() || CAUSED.matcher(lines[j]).find() || EXCEPTION.matcher(lines[j]).find())) {
			block.add(lines[j]);
			j++;
		}
		return block;
	}

	private boolean allowRate() {
		final long now = System.currentTimeMillis();
		while (!recentIssueMillis.isEmpty() && now - recentIssueMillis.peekFirst() > 3_600_000L) {
			recentIssueMillis.pollFirst();
		}
		if (recentIssueMillis.size() >= config.maxIssuesPerHour) {
			return false;
		}
		recentIssueMillis.addLast(now);
		return true;
	}

	private void fileNow(WatcherConfig cfg, String title, String body, List<String> labels, Consumer<String> feedback) {
		try {
			final String url = IssueReporter.createIssue(cfg, title, body, labels);
			Init.LOGGER.info("[MTR-CCT] Watcher filed issue: {}", url);
			feedback.accept("Filed: " + url);
		} catch (IOException e) {
			Init.LOGGER.error("[MTR-CCT] Watcher failed to file issue", e);
			feedback.accept("Failed to file issue: " + e.getMessage());
		}
	}

	private static String readTail(File file, int chars) {
		if (!file.exists()) {
			return "";
		}
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
			final long length = raf.length();
			final long from = Math.max(0, length - chars);
			raf.seek(from);
			final byte[] buffer = new byte[(int) (length - from)];
			raf.readFully(buffer);
			return new String(buffer, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "";
		}
	}

	private static String normalize(String text) {
		return VOLATILE.matcher(text).replaceAll("#").trim();
	}

	private static String sha1(String text) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-1");
			final byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
			final StringBuilder sb = new StringBuilder();
			for (int k = 0; k < 6 && k < hash.length; k++) {
				sb.append(String.format("%02x", hash[k]));
			}
			return sb.toString();
		} catch (Exception e) {
			return Integer.toHexString(text.hashCode());
		}
	}

	private static String lastSegment(String name) {
		final int dot = name.lastIndexOf('.');
		return dot >= 0 ? name.substring(dot + 1) : name;
	}

	private static String trim(String text, int max) {
		final String clean = text.trim();
		return clean.length() > max ? clean.substring(0, max) : clean;
	}

	private static final class Event {
		final String kind;
		final String title;
		final String signature;
		final List<String> block;

		Event(String kind, String title, String signatureKey, List<String> block) {
			this.kind = kind;
			this.title = title;
			this.signature = kind + "-" + sha1(kind + "|" + normalize(signatureKey));
			this.block = block == null ? new ArrayList<>() : block;
		}

		String body() {
			final StringBuilder excerpt = new StringBuilder();
			for (String line : block) {
				excerpt.append(line).append('\n');
				if (excerpt.length() > 5500) {
					break;
				}
			}
			return "**Auto-filed by the in-mod watcher** (`/mtr watcher start`).\n\n"
					+ "- **Type:** " + kind + "\n"
					+ "- **Signature:** `" + signature + "`\n\n"
					+ "### Log excerpt\n```\n" + excerpt.toString().trim() + "\n```\n\n"
					+ "_De-duplicated by signature; the same error will not be re-filed while the watcher runs._";
		}
	}
}
