package me.pattrick.marbledrop.feedback;

import me.pattrick.marbledrop.command.Commands;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * /feedback <message>
 * <p>
 * Posts to the same Cloudflare endpoint (a Pages Function on
 * marblebase.net) that the website's feedback form submits to, tagged
 * with source=minecraft. The endpoint requires a shared secret header
 * for minecraft-sourced submissions (the website's own form doesn't
 * send one, since it can't hold a secret safely in public JS) -- see
 * the website-side patch notes for the corresponding check.
 * <p>
 * Runs the HTTP call off the main thread so a slow/failed network
 * request can't cause a server-side lag spike, then hops back to the
 * main thread only to message the player.
 */
public final class FeedbackCommand implements CommandExecutor {

    private final Plugin plugin;
    private final String endpointUrl;
    private final String secret;
    private final HttpClient httpClient;

    public FeedbackCommand(Plugin plugin, String endpointUrl, String secret) {
        this.plugin = plugin;
        this.endpointUrl = endpointUrl;
        this.secret = secret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player player = Commands.player(sender);
        if (player == null) return true;

        if (args.length == 0) {
            Commands.usage(player, "/feedback <your message>");
            return true;
        }

        String message = String.join(" ", args).trim();
        if (message.isEmpty()) {
            Commands.usage(player, "/feedback <your message>");
            return true;
        }
        if (message.length() > 1000) {
            sender.sendMessage(ChatColor.RED + "Feedback is too long (max 1000 characters).");
            return true;
        }

        if (endpointUrl == null || endpointUrl.isBlank() || endpointUrl.contains("CHANGE_ME")) {
            sender.sendMessage(ChatColor.RED + "Feedback isn't configured on this server yet. Let an admin know.");
            plugin.getLogger().warning("/feedback used but feedback.endpoint-url isn't configured in config.yml.");
            return true;
        }

        player.sendMessage(ChatColor.GRAY + "Sending feedback...");

        String jsonBody = "{"
                + "\"message\":" + jsonString(message) + ","
                + "\"username\":" + jsonString(player.getName()) + ","
                + "\"source\":\"minecraft\""
                + "}";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success;
            String failureReason = null;

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpointUrl))
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .header("x-feedback-secret", secret == null ? "" : secret)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                success = response.statusCode() >= 200 && response.statusCode() < 300;

                if (!success) {
                    failureReason = "HTTP " + response.statusCode() + ": " + response.body();
                }
            } catch (Exception e) {
                success = false;
                failureReason = e.getMessage();
            }

            if (!success) {
                plugin.getLogger().warning("Feedback submission failed: " + failureReason);
            }

            final boolean finalSuccess = success;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalSuccess) {
                    player.sendMessage(ChatColor.GREEN + "Thanks for the feedback!");
                } else {
                    player.sendMessage(ChatColor.RED + "Couldn't send your feedback right now, try again later.");
                }
            });
        });

        return true;
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
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
