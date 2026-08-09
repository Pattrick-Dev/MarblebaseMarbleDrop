package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.MdConfig;
import me.pattrick.marbledrop.command.Commands;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RaceCommand implements CommandExecutor {

    private final RaceManager races;
    private final TrackManager tracks;
    private final RaceWatchManager watch;
    private final ScheduledRaceManager scheduledRaces;
    private final MdConfig config;

    public RaceCommand(RaceManager races, TrackManager tracks, RaceWatchManager watch, ScheduledRaceManager scheduledRaces,
                        MdConfig config) {
        this.races = races;
        this.tracks = tracks;
        this.watch = watch;
        this.scheduledRaces = scheduledRaces;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        Player player = Commands.player(sender);
        if (player == null) return true;

        // default: open GUI
        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu")) {
            RaceGui.open(player, tracks, races);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            case "open" -> {
                if (!Commands.requireAdmin(player)) return true;
                if (args.length < 2) {
                    Commands.usage(player, "/md race open <trackId>");
                    return true;
                }
                races.open(player, args[1].toLowerCase());
                return true;
            }

            case "close" -> {
                if (!Commands.requireAdmin(player)) return true;
                if (args.length < 2) {
                    Commands.usage(player, "/md race close <trackId>");
                    return true;
                }
                races.close(player, args[1].toLowerCase());
                return true;
            }

            case "start" -> {
                if (!Commands.requireAdmin(player)) return true;
                if (args.length < 2) {
                    Commands.usage(player, "/md race start <trackId>");
                    return true;
                }
                races.start(player, args[1].toLowerCase());
                return true;
            }

            case "clear" -> {
                if (!Commands.requireAdmin(player)) return true;
                if (args.length < 2) {
                    Commands.usage(player, "/md race clear <trackId>");
                    return true;
                }
                races.clear(args[1].toLowerCase());
                player.sendMessage(ChatColor.YELLOW + "Cleared race entries for '" + args[1].toLowerCase() + "'.");
                return true;
            }

            case "watch" -> {
                if (args.length < 2) {
                    Commands.usage(player, "/md race watch <trackId>");
                    return true;
                }
                watch.start(player, args[1].toLowerCase());
                return true;
            }

            case "unwatch", "leavewatch" -> {
                watch.stop(player, true);
                return true;
            }

            case "test", "testrace" -> {
                if (!Commands.requireAdmin(player)) return true;
                if (args.length < 2) {
                    Commands.usage(player, "/md race test <trackId> [aiCount] [noself]");
                    player.sendMessage(ChatColor.YELLOW + "   or: /md race test loadout <trackId>  (pick a loadout to test with)");
                    return true;
                }

                // "test loadout <trackId>" -- opens the same loadout GUI a
                // real lobby entry uses (see RaceLoadoutGui); the pick is
                // remembered as this admin's preference and runTestRace()
                // reads it automatically, no lobby entry required.
                if (args[1].equalsIgnoreCase("loadout")) {
                    if (args.length < 3) {
                        Commands.usage(player, "/md race test loadout <trackId>");
                        return true;
                    }
                    RaceLoadoutGui.open(player, races, args[2].toLowerCase());
                    return true;
                }

                String testTrackId = args[1].toLowerCase();
                int aiCount = 3;
                boolean includeSelf = true;
                for (int i = 2; i < args.length; i++) {
                    String arg = args[i].toLowerCase();
                    if (arg.equals("noself") || arg.equals("nomarble")) {
                        includeSelf = false;
                    } else {
                        try {
                            aiCount = Integer.parseInt(arg);
                        } catch (NumberFormatException ignored) {
                            player.sendMessage(ChatColor.RED + "Ignoring unrecognized argument '" + arg + "'.");
                        }
                    }
                }
                races.runTestRace(player, testTrackId, aiCount, includeSelf);
                return true;
            }

            case "next" -> {
                String openId = scheduledRaces.openTrackId();
                String activeId = scheduledRaces.activeCycleTrackId();

                if (openId != null) {
                    player.sendMessage(ChatColor.GREEN + "A race on '" + openId + "' is open right now -- " +
                            ChatColor.AQUA + "/md join" + ChatColor.GREEN + "! (" + races.lobbyCount(openId) + " joined so far)");
                } else if (activeId != null) {
                    player.sendMessage(ChatColor.YELLOW + "A race on '" + activeId + "' is currently running -- " +
                            "check back once it finishes.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "No scheduled race is open right now. Next one opens in about " +
                            scheduledRaces.minutesUntilNextCycle() + " minute(s).");
                }
                return true;
            }

            case "join" -> {
                String trackId = scheduledRaces.openTrackId();
                if (trackId == null) {
                    if (scheduledRaces.activeCycleTrackId() != null) {
                        player.sendMessage(ChatColor.RED + "A scheduled race is already running -- check back once it finishes.");
                    } else {
                        player.sendMessage(ChatColor.RED + "No scheduled race is open right now.");
                        player.sendMessage(ChatColor.GRAY + "Next one opens in about " +
                                scheduledRaces.minutesUntilNextCycle() + " minute(s).");
                    }
                    return true;
                }
                races.enter(player, trackId, player.getInventory().getItemInMainHand());
                return true;
            }

            case "leave" -> {
                String trackId = races.findTrackIdForPlayer(player.getUniqueId());
                if (trackId == null) {
                    player.sendMessage(ChatColor.RED + "You're not entered in any race.");
                    return true;
                }
                races.leave(player, trackId);
                return true;
            }

            case "forcecycle" -> {
                if (!Commands.requireAdmin(player)) return true;
                scheduledRaces.forceCycleNow();
                player.sendMessage(ChatColor.YELLOW + "Forced the scheduled race cycle to run now.");
                return true;
            }

            case "purge" -> {
                if (!Commands.requireAdmin(player)) return true;
                races.purgeAllRunners();
                scheduledRaces.releaseStuckCycle();
                player.sendMessage(ChatColor.YELLOW + "Force-removed all active marble runners.");
                return true;
            }

            case "schedule" -> {
                if (!Commands.requireAdmin(player)) return true;
                RaceScheduleMenu.open(player, config);
                return true;
            }

            default -> {
                usage(player);
                return true;
            }
        }
    }

    private void usage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Race commands:");
        player.sendMessage(ChatColor.GRAY + "/md race" + ChatColor.DARK_GRAY + " (open menu)");
        player.sendMessage(ChatColor.GRAY + "/md race watch <trackId>" + ChatColor.DARK_GRAY + " (watch w/ inventory saved)");
        player.sendMessage(ChatColor.GRAY + "/md race unwatch" + ChatColor.DARK_GRAY + " (restore inventory)");
        player.sendMessage(ChatColor.GRAY + "/md race open <trackId> " + ChatColor.DARK_GRAY + "(admin)");
        player.sendMessage(ChatColor.GRAY + "/md race close <trackId> " + ChatColor.DARK_GRAY + "(admin)");
        player.sendMessage(ChatColor.GRAY + "/md race start <trackId> " + ChatColor.DARK_GRAY + "(admin)");
        player.sendMessage(ChatColor.GRAY + "/md race clear <trackId> " + ChatColor.DARK_GRAY + "(admin)");
        player.sendMessage(ChatColor.GRAY + "/md race test <trackId> [aiCount] [noself] " + ChatColor.DARK_GRAY + "(admin, debug mode only)");
        player.sendMessage(ChatColor.GRAY + "/md race test loadout <trackId> " + ChatColor.DARK_GRAY + "(admin, pick a loadout to test with)");
        player.sendMessage(ChatColor.GRAY + "/md race purge " + ChatColor.DARK_GRAY + "(admin, removes all active runners)");
        player.sendMessage(ChatColor.GRAY + "/md join " + ChatColor.DARK_GRAY + "(join the current scheduled race)");
        player.sendMessage(ChatColor.GRAY + "/md leave " + ChatColor.DARK_GRAY + "(leave whatever race you're entered in)");
        player.sendMessage(ChatColor.GRAY + "/md race next " + ChatColor.DARK_GRAY + "(time until the next scheduled race)");
        player.sendMessage(ChatColor.GRAY + "/md race forcecycle " + ChatColor.DARK_GRAY + "(admin, runs the scheduled race cycle now)");
        player.sendMessage(ChatColor.GRAY + "/md race schedule " + ChatColor.DARK_GRAY + "(admin, GUI to tune scheduled race settings)");
    }
}
