package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.MdConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Posts a race-open announcement to a Discord webhook (config: discord.webhook-url)
 * and edits that SAME message periodically with a live-ish countdown, instead of
 * spamming a new message every tick - mirrors FeedbackCommand's proven pattern
 * (plain java.net.http.HttpClient, hand-built JSON, everything off the main thread).
 * No-op everywhere if webhook-url is blank (the default).
 */
public final class DiscordRaceStatus {

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"(\\d+)\"");

    private final Plugin plugin;
    private final MdConfig config;
    private final ScheduledRaceManager scheduledRaces;
    private final RaceManager races;
    private final HttpClient httpClient;

    // Written from the async HTTP callback, read from the main-thread
    // repeating task - volatile is enough since it's a plain read/write,
    // no compound operations.
    private volatile String messageId;

    private BukkitTask updateTask;

    public DiscordRaceStatus(Plugin plugin, MdConfig config, ScheduledRaceManager scheduledRaces, RaceManager races) {
        this.plugin = plugin;
        this.config = config;
        this.scheduledRaces = scheduledRaces;
        this.races = races;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    private boolean enabled() {
        String url = config.discordWebhookUrl();
        return url != null && !url.isBlank();
    }

    /** Called once a track opens for entries - posts the initial message and starts the periodic edit. */
    public void onOpen(String trackId) {
        if (!enabled()) return;

        messageId = null;
        post(content(trackId));

        int intervalSeconds = Math.max(5, config.discordUpdateIntervalSeconds());
        long intervalTicks = intervalSeconds * 20L;
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            String id = messageId;
            if (id != null) edit(id, content(trackId));
        }, intervalTicks, intervalTicks);
    }

    /** Called once the entry window closes and the race actually starts - final edit, then stop updating. */
    public void onClose(String trackId) {
        if (!enabled()) return;
        cancelTask();

        String id = messageId;
        messageId = null;
        if (id != null) {
            edit(id, "🏁 **Race on `" + trackId + "` has started!**");
        }
    }

    /** Called on plugin/manager shutdown - stop updating without claiming the race "started". */
    public void shutdown() {
        cancelTask();
        messageId = null;
    }

    private void cancelTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private String content(String trackId) {
        long seconds = scheduledRaces.secondsUntilNextCycle();
        int joined = races.lobbyCount(trackId);

        return "🏁 **Race open on `" + trackId + "`** - starts in **"
                + ScheduledRaceManager.formatDuration(seconds) + "** - "
                + joined + " player" + (joined == 1 ? "" : "s") + " joined\n"
                + "Join in-game with `/md join`";
    }

    private void post(String content) {
        String url = config.discordWebhookUrl();
        if (url == null || url.isBlank()) return;

        String jsonBody = "{\"content\":" + jsonString(content) + "}";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(appendWait(url)))
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    Matcher m = ID_PATTERN.matcher(response.body());
                    if (m.find()) {
                        messageId = m.group(1);
                    }
                } else {
                    plugin.getLogger().warning("[Discord] Webhook post failed: HTTP " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Webhook post failed: " + e.getMessage());
            }
        });
    }

    private void edit(String id, String content) {
        String url = config.discordWebhookUrl();
        if (url == null || url.isBlank()) return;

        String jsonBody = "{\"content\":" + jsonString(content) + "}";
        String editUrl = stripQuery(url) + "/messages/" + id;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(editUrl))
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    plugin.getLogger().warning("[Discord] Webhook edit failed: HTTP " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Webhook edit failed: " + e.getMessage());
            }
        });
    }

    private String appendWait(String url) {
        return url + (url.contains("?") ? "&" : "?") + "wait=true";
    }

    private String stripQuery(String url) {
        int idx = url.indexOf('?');
        return idx < 0 ? url : url.substring(0, idx);
    }

    private String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
