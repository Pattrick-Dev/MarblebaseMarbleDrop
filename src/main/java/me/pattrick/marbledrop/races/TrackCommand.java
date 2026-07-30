package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.MdConfig;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TrackCommand implements CommandExecutor {

    private static final String PERM = "marbledrop.admin";

    private final TrackManager tracks;
    private final MarbleRaceEngine raceEngine;
    private final TrackVisualizer visualizer;
    private final MdConfig config;

    public TrackCommand(
            TrackManager tracks,
            TrackVisualizer visualizer,
            MarbleRaceEngine raceEngine,
            MdConfig config
    ) {
        this.tracks = tracks;
        this.visualizer = visualizer;
        this.raceEngine = raceEngine;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Please run this command as a player!");
            return true;
        }

        if (!player.hasPermission(PERM)) {
            player.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        // ✅ GUI-first
        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu")) {
            TrackGui.openList(player, tracks, 0);
            return true;
        }

        // Keep existing commands as backup tools
        String sub = args[0].toLowerCase();

        switch (sub) {

            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track create <id>");
                    return true;
                }

                String id = args[1].toLowerCase();

                if (!tracks.createTrack(id, player.getWorld())) {
                    player.sendMessage(ChatColor.RED + "Track already exists.");
                    return true;
                }

                player.sendMessage(ChatColor.GREEN + "Created track '" + id + "'.");
            }

            case "addpoint" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track addpoint <id>");
                    return true;
                }

                String id = args[1].toLowerCase();

                if (!tracks.addPoint(id, player.getLocation())) {
                    player.sendMessage(ChatColor.RED + "Track not found.");
                    return true;
                }

                int count = tracks.getTrack(id).size();
                player.sendMessage(ChatColor.GREEN + "Added point #" + count + " to '" + id + "'.");
            }

            case "setwatch" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track setwatch <id>");
                    return true;
                }
                String id = args[1].toLowerCase();
                if (!tracks.setWatchLocation(id, player.getLocation())) {
                    player.sendMessage(ChatColor.RED + "Track not found (or wrong world).");
                    return true;
                }
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Set watch spot for '" + id + "'.");
            }

            case "clearwatch" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track clearwatch <id>");
                    return true;
                }
                String id = args[1].toLowerCase();
                if (!tracks.clearWatchLocation(id)) {
                    player.sendMessage(ChatColor.RED + "Track not found.");
                    return true;
                }
                player.sendMessage(ChatColor.YELLOW + "Cleared watch spot for '" + id + "'.");
            }

            case "autorace" -> {
                if (args.length < 3 || (!args[2].equalsIgnoreCase("on") && !args[2].equalsIgnoreCase("off"))) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track autorace <id> <on|off>");
                    return true;
                }

                String id = args[1].toLowerCase();
                boolean on = args[2].equalsIgnoreCase("on");

                if (!tracks.setAutoRaceEligible(id, on)) {
                    player.sendMessage(ChatColor.RED + "Track not found.");
                    return true;
                }

                player.sendMessage(ChatColor.GREEN + "Track '" + id + "' is now " +
                        (on ? "eligible for" : "excluded from") + " the scheduled server race.");
            }

            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track info <id>");
                    return true;
                }

                String id = args[1].toLowerCase();
                MarbleTrack track = tracks.getTrack(id);

                if (track == null) {
                    player.sendMessage(ChatColor.RED + "Track not found.");
                    return true;
                }

                player.sendMessage(ChatColor.YELLOW + "Track: " + id);
                player.sendMessage(ChatColor.GRAY + "World: " + track.getWorld().getName());
                player.sendMessage(ChatColor.GRAY + "Points: " + track.size());
                player.sendMessage(ChatColor.GRAY + "Watch: " + (track.getWatchLocation() != null ? ChatColor.GREEN + "set" : ChatColor.RED + "not set"));
                player.sendMessage(ChatColor.GRAY + "Scheduled race eligible: " + (track.isAutoRaceEligible() ? ChatColor.GREEN + "yes" : ChatColor.RED + "no"));
            }

            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track delete <id>");
                    return true;
                }

                String id = args[1].toLowerCase();

                if (!tracks.removeTrack(id)) {
                    player.sendMessage(ChatColor.RED + "Track not found.");
                    return true;
                }

                player.sendMessage(ChatColor.GREEN + "Deleted track '" + id + "'.");
            }

            case "show" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /md track show <id>");
                    return true;
                }
                visualizer.show(player, args[1].toLowerCase());
            }

            case "hide" -> visualizer.hide(player);

            case "run" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /md track run <id>");
                    return true;
                }

                String id = args[1].toLowerCase();
                MarbleTrack track = tracks.getTrack(id);

                if (track == null || track.size() < 2) {
                    player.sendMessage("§cTrack not found or not enough points.");
                    return true;
                }

                TrackPhysics physics = new TrackPhysics(track.getSpline(), config.raceWallSearchRadius());
                MarbleRunner runner = new MarbleRunner(physics, track.getPoint(0), null, 0.02, 0.35, 0.50, null);
                raceEngine.addRunner(runner);

                player.sendMessage("§aMarble started on track §e" + id);
            }

            default -> {
                player.sendMessage(ChatColor.YELLOW + "Use /md track to open the admin track GUI.");
                player.sendMessage(ChatColor.GRAY + "Or: create/addpoint/info/delete/show/hide/run/setwatch/clearwatch/autorace");
            }
        }

        return true;
    }
}
