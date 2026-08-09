package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.marble.MarbleStats;
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

    public TrackCommand(
            TrackManager tracks,
            TrackVisualizer visualizer,
            MarbleRaceEngine raceEngine
    ) {
        this.tracks = tracks;
        this.visualizer = visualizer;
        this.raceEngine = raceEngine;
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
                player.sendMessage(ChatColor.GRAY + "Laps: " + track.getLaps());
                player.sendMessage(ChatColor.GRAY + "Auto-race queue: " + (track.isAutoRaceEligible() ? ChatColor.GREEN + "yes" : ChatColor.RED + "no"));
                player.sendMessage(ChatColor.GRAY + "Watch: " + (track.getWatchLocation() != null ? ChatColor.GREEN + "set" : ChatColor.RED + "not set"));
            }

            case "laps" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track laps <id> <count>");
                    return true;
                }
                String id = args[1].toLowerCase();
                MarbleTrack track = tracks.getTrack(id);
                if (track == null) {
                    player.sendMessage(ChatColor.RED + "Track not found.");
                    return true;
                }
                int laps;
                try {
                    laps = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    player.sendMessage(ChatColor.RED + "Laps must be a number.");
                    return true;
                }
                track.setLaps(laps);
                tracks.saveNow();
                player.sendMessage(ChatColor.GREEN + "Set '" + id + "' to " + track.getLaps() + " lap(s).");
            }

            case "autorace" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track autorace <id> on|off");
                    return true;
                }
                String id = args[1].toLowerCase();
                MarbleTrack track = tracks.getTrack(id);
                if (track == null) {
                    player.sendMessage(ChatColor.RED + "Track not found.");
                    return true;
                }
                boolean on = args[2].equalsIgnoreCase("on");
                boolean off = args[2].equalsIgnoreCase("off");
                if (!on && !off) {
                    player.sendMessage(ChatColor.RED + "Usage: /md track autorace <id> on|off");
                    return true;
                }
                track.setAutoRaceEligible(on);
                tracks.saveNow();
                player.sendMessage(on
                        ? ChatColor.GREEN + "'" + id + "' added to the scheduled race queue."
                        : ChatColor.YELLOW + "'" + id + "' removed from the scheduled race queue.");
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

                MarbleRunner runner = new MarbleRunner(track, track.getPoint(0), null,
                        new MarbleStats(50, 50, 50, 50, 50), track.getLaps(), null);
                raceEngine.addRunner(runner);

                player.sendMessage("§aMarble started on track §e" + id);
            }

            default -> {
                player.sendMessage(ChatColor.YELLOW + "Use /md track to open the admin track GUI.");
                player.sendMessage(ChatColor.GRAY + "Or: create/addpoint/info/delete/show/hide/run/setwatch/clearwatch/laps/autorace");
            }
        }

        return true;
    }
}
