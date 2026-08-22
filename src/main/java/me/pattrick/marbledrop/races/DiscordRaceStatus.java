package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.MdConfig;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Posts a single Discord embed to a webhook (config: discord.webhook-url)
 * and edits that ONE message forever - every open/started transition, for
 * every race cycle, for the life of the server - rather than posting a
 * fresh message each cycle. Mirrors FeedbackCommand's proven HTTP pattern
 * (plain java.net.http.HttpClient, hand-built JSON, everything off the
 * main thread). No-op everywhere if webhook-url is blank (the default).
 * <p>
 * The message id is persisted to discord-race-status.yml (alongside the
 * webhook url it belongs to) so it survives plugin/server restarts - the
 * whole point is one durable status message, not one per server run. If
 * the stored webhook url doesn't match the currently configured one (the
 * admin pointed it at a different channel), the stored id is discarded
 * and a fresh message is posted there instead. Same self-heal if the
 * message was deleted out from under us on Discord's side - an edit that
 * comes back 404 clears the id and immediately reposts.
 */
public final class DiscordRaceStatus {

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"(\\d+)\"");

    private static final int COLOR_OPEN = 0xF1C40F;    // gold - entries open
    private static final int COLOR_STARTED = 0x2ECC71; // green - race running

    private final Plugin plugin;
    private final MdConfig config;
    private final ScheduledRaceManager scheduledRaces;
    private final RaceManager races;
    private final HttpClient httpClient;
    private final File stateFile;

    // Written from the async HTTP callback, read from the main-thread
    // repeating task - volatile is enough since it's a plain read/write,
    // no compound operations. Deliberately NOT cleared between race
    // cycles (see onClose) - that's what makes this one message forever
    // instead of one per cycle.
    private volatile String messageId;

    private BukkitTask updateTask;

    public DiscordRaceStatus(Plugin plugin, MdConfig config, ScheduledRaceManager scheduledRaces, RaceManager races) {
        this.plugin = plugin;
        this.config = config;
        this.scheduledRaces = scheduledRaces;
        this.races = races;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.stateFile = new File(plugin.getDataFolder(), "discord-race-status.yml");
        loadState();
    }

    private boolean enabled() {
        String url = config.discordWebhookUrl();
        return url != null && !url.isBlank();
    }

    /** Called once a track opens for entries - posts (first ever run) or edits (every run after) the one status message, then starts the periodic countdown edit. */
    public void onOpen(String trackId) {
        if (!enabled()) return;

        pushOrCreate(openEmbed(trackId));

        int intervalSeconds = Math.max(5, config.discordUpdateIntervalSeconds());
        long intervalTicks = intervalSeconds * 20L;
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> pushOrCreate(openEmbed(trackId)), intervalTicks, intervalTicks);
    }

    /** Called once the entry window closes and the race actually starts - final edit for this cycle, then stop updating. The message itself lives on for the next cycle's onOpen(). */
    public void onClose(String trackId) {
        if (!enabled()) return;
        cancelTask();
        pushOrCreate(closedEmbed(trackId));
    }

    /** Called on plugin/manager shutdown - stop updating. The message and its id are left in place; the next onOpen() (this run or a future one) picks it back up. */
    public void shutdown() {
        cancelTask();
    }

    private void cancelTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    /** Edits the persisted message if we have one, otherwise posts a brand new one (first run ever, or the old one is gone). */
    private void pushOrCreate(String embedBody) {
        String id = messageId;
        if (id != null) {
            edit(id, embedBody);
        } else {
            post(embedBody);
        }
    }

    private String openEmbed(String trackId) {
        long seconds = scheduledRaces.secondsUntilNextCycle();
        int joined = races.lobbyCount(trackId);
        int fullDust = config.scheduledRaceWinnerDust();
        int aiDust = config.scheduledRaceWinnerDustVsAi();

        String fields = "["
                + field("Track", "`" + trackId + "`", true)
                + "," + field("Starts In", ScheduledRaceManager.formatDuration(seconds), true)
                + "," + field("Reward", "1st: **" + fullDust + "** Dust (**" + aiDust + "** if AI fills the field)", true)
                + "," + field("Players Joined (" + joined + ")", joinedPlayersList(trackId), false)
                + "]";

        return embed("🏁 Race Open for Entries", "Join in-game with `/race join`", COLOR_OPEN, fields);
    }

    private String closedEmbed(String trackId) {
        // Called before ScheduledRaceManager clears the lobby for this
        // cycle (see resolveCycle - onClose() runs before races.close()),
        // so this is still the actual final field that just went racing.
        String fields = "["
                + field("Track", "`" + trackId + "`", true)
                + "," + field("Racers", joinedPlayersList(trackId), false)
                + "]";
        return embed("🏁 Race Started", "Good luck to everyone racing!", COLOR_STARTED, fields);
    }

    /** Bulleted list of who's currently in trackId's lobby, capped so a big field never blows past Discord's 1024-char embed field value limit. */
    private String joinedPlayersList(String trackId) {
        List<RaceManager.RaceEntry> lobby = races.lobbySnapshot(trackId);
        if (lobby.isEmpty()) return "_Nobody yet - be the first with `/race join`!_";

        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (RaceManager.RaceEntry entry : lobby) {
            if (shown >= 20) {
                sb.append("\n_+ ").append(lobby.size() - shown).append(" more_");
                break;
            }
            String name = Bukkit.getOfflinePlayer(entry.owner).getName();
            if (name == null) name = entry.owner.toString();

            if (shown > 0) sb.append('\n');
            sb.append("• ").append(name);
            shown++;
        }
        return sb.toString();
    }

    private String embed(String title, String description, int color, String fieldsJson) {
        return "{\"embeds\":[{"
                + "\"title\":" + jsonString(title) + ","
                + "\"description\":" + jsonString(description) + ","
                + "\"color\":" + color + ","
                + "\"fields\":" + fieldsJson + ","
                + "\"footer\":{\"text\":\"MarbleDrop\"},"
                + "\"timestamp\":" + jsonString(Instant.now().toString())
                + "}]}";
    }

    private String field(String name, String value, boolean inline) {
        return "{\"name\":" + jsonString(name) + ",\"value\":" + jsonString(value) + ",\"inline\":" + inline + "}";
    }

    private void post(String jsonBody) {
        String url = config.discordWebhookUrl();
        if (url == null || url.isBlank()) return;

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
                        saveState(url, messageId);
                    }
                } else {
                    plugin.getLogger().warning("[Discord] Webhook post failed: HTTP " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Webhook post failed: " + e.getMessage());
            }
        });
    }

    private void edit(String id, String jsonBody) {
        String url = config.discordWebhookUrl();
        if (url == null || url.isBlank()) return;

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
                if (response.statusCode() == 404) {
                    // The message is gone (manually deleted, channel purged, etc.)
                    // - drop the stale id and immediately repost the same content
                    // as a fresh message instead of silently going dark.
                    messageId = null;
                    post(jsonBody);
                } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    plugin.getLogger().warning("[Discord] Webhook edit failed: HTTP " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Webhook edit failed: " + e.getMessage());
            }
        });
    }

    private void loadState() {
        if (!stateFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(stateFile);

        String storedWebhook = yml.getString("webhook-url");
        String storedId = yml.getString("message-id");
        if (storedId == null || storedWebhook == null) return;

        // Only reuse the stored message if it belongs to the webhook we're
        // still pointed at - otherwise it's a leftover from a different
        // channel/server and has no business being edited.
        if (storedWebhook.equals(config.discordWebhookUrl())) {
            this.messageId = storedId;
        }
    }

    private void saveState(String webhookUrl, String id) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("webhook-url", webhookUrl);
        yml.set("message-id", id);
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[Discord] Failed to save race status message id: " + e.getMessage());
        }
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
