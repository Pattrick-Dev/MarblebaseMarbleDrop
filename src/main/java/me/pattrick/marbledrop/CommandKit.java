package me.pattrick.marbledrop;

import me.pattrick.marbledrop.command.Commands;
import me.pattrick.marbledrop.progression.infusion.heads.GeyserSkullSync;
import me.pattrick.marbledrop.progression.infusion.heads.HeadPool;
import me.pattrick.marbledrop.progression.upgrades.UpgradeStationCommand;
import me.pattrick.marbledrop.races.ScheduledRaceManager;
import me.pattrick.marbledrop.update.UpdateChecker;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Routes every MarbleDrop command to its per-feature CommandExecutor.
 * Pure dispatch - every actual check (player-only, permission) lives in
 * the target command itself now, not duplicated here. An earlier version
 * re-checked "is this sender a player" before delegating to nearly every
 * route, even though the target command almost always re-checked it too;
 * worse, that router-level check ran BEFORE commands like
 * DustAdminCommand/TasksAdminCommand, which were written to work from
 * console, silently blocking them from ever being reached that way.
 * <p>
 * One instance of this class is registered as the executor for EVERY
 * top-level command it handles (md, race, tutorial, team, dust, station,
 * tasks, recipes - see Main) - {@link #onCommand} tells them apart via
 * {@code cmd.getName()}, not via args[0]. This replaced an earlier design
 * where everything lived under a single "/md <sub>" command; player-facing
 * features (race, tutorial, team, dust) and the station admin tools got
 * promoted to their own top-level commands for a shorter, more natural feel
 * ("/race join" instead of "/md join") - "/md" itself now only covers
 * genuinely administrative/meta stuff (reload, version, update, debug, pdc)
 * plus track building, which is 100% admin-only anyway. This was a clean
 * break - the old "/md race"/"/md join"/"/md table" etc. forms are gone,
 * not aliased.
 */
public class CommandKit implements CommandExecutor {

    private final JavaPlugin plugin;
    private final MdConfig mdConfig;

    private final CommandExecutor infusionTableCommand;
    private final CommandExecutor dustCommand;
    private final CommandExecutor dustAdminCommand;
    private final CommandExecutor marbleRecyclerCommand;
    private final CommandExecutor tasksCommand;
    private final CommandExecutor tasksAdminCommand;
    private final CommandExecutor upgradeStationCommand;
    private final CommandExecutor trackCommand;
    private final CommandExecutor raceCommand;
    private final CommandExecutor teamCommand;
    private final CommandExecutor tutorialCommand;
    private final CommandExecutor recipesCommand;
    private final ScheduledRaceManager scheduledRaceManager;
    private final UpdateChecker updateChecker;
    private final HeadPool headPool;

    private final File filePath;
    private FileConfiguration config;

    public CommandKit(
            JavaPlugin plugin,
            MdConfig mdConfig,
            CommandExecutor infusionTableCommand,
            CommandExecutor dustCommand,
            CommandExecutor dustAdminCommand,
            CommandExecutor marbleRecyclerCommand,
            CommandExecutor tasksCommand,
            CommandExecutor tasksAdminCommand,
            UpgradeStationCommand upgradeStationCommand,
            CommandExecutor trackCommand,
            CommandExecutor raceCommand,
            CommandExecutor teamCommand,
            CommandExecutor tutorialCommand,
            CommandExecutor recipesCommand,
            ScheduledRaceManager scheduledRaceManager,
            UpdateChecker updateChecker,
            HeadPool headPool
    ) {

        this.plugin = plugin;
        this.mdConfig = mdConfig;

        this.infusionTableCommand = infusionTableCommand;
        this.dustCommand = dustCommand;
        this.dustAdminCommand = dustAdminCommand;
        this.marbleRecyclerCommand = marbleRecyclerCommand;
        this.tasksCommand = tasksCommand;
        this.tasksAdminCommand = tasksAdminCommand;
        this.upgradeStationCommand = upgradeStationCommand;
        this.trackCommand = trackCommand;
        this.raceCommand = raceCommand;
        this.teamCommand = teamCommand;
        this.tutorialCommand = tutorialCommand;
        this.recipesCommand = recipesCommand;
        this.scheduledRaceManager = scheduledRaceManager;
        this.updateChecker = updateChecker;
        this.headPool = headPool;

        this.filePath = new File(plugin.getDataFolder(), "config.yml");
        this.config = YamlConfiguration.loadConfiguration(this.filePath);
    }

    private static String[] shiftArgs(String[] args, int by) {
        if (args.length <= by) return new String[0];
        String[] out = new String[args.length - by];
        System.arraycopy(args, by, out, 0, out.length);
        return out;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();

        switch (name) {

            case "tasks" -> {
                return handleTasks(sender, cmd, label, args);
            }

            case "recipes" -> {
                return recipesCommand.onCommand(sender, cmd, label, args);
            }

            case "race" -> {
                return raceCommand.onCommand(sender, cmd, label, args);
            }

            case "tutorial" -> {
                return tutorialCommand.onCommand(sender, cmd, label, args);
            }

            case "team" -> {
                // Temporarily disabled - the re-issue button in this GUI
                // (TeamMenuListener#handleMemberClick, SHIFT_LEFT branch)
                // hands out a fresh marble without reclaiming the
                // previously issued one, so it's an unlimited free-marble
                // exploit right now. Re-enable once that's fixed.
                if (sender instanceof Player p) {
                    p.sendMessage(ChatColor.RED + "/team is temporarily disabled.");
                }
                return true;
            }

            case "dust" -> {
                return handleDust(sender, cmd, label, args);
            }

            case "station" -> {
                return handleStation(sender, cmd, label, args);
            }

            case "md" -> {
                return handleMd(sender, cmd, label, args);
            }

            default -> {
                sender.sendMessage(ChatColor.RED + "Use: /md");
                return true;
            }
        }
    }

    private boolean handleDust(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            return dustAdminCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }
        return dustCommand.onCommand(sender, cmd, label, args);
    }

    /** /station <table|recycler|upgrade> <give|remove|count> - a thin dispatcher over the 3 existing station commands, which already share their actual give/remove/count logic via StationCommands. */
    private boolean handleStation(CommandSender sender, Command cmd, String label, String[] args) {
        if (!Commands.requireAdmin(sender)) return true;

        if (args.length == 0) {
            Commands.usage(sender, "/station <table|recycler|upgrade> <give|remove|count>");
            return true;
        }

        String type = args[0].toLowerCase();
        String[] rest = shiftArgs(args, 1);

        return switch (type) {
            case "table" -> infusionTableCommand.onCommand(sender, cmd, label, rest);
            case "recycler" -> marbleRecyclerCommand.onCommand(sender, cmd, label, rest);
            case "upgrade" -> upgradeStationCommand.onCommand(sender, cmd, label, rest);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown station type '" + type + "'. Use table, recycler, or upgrade.");
                yield true;
            }
        };
    }

    /** Everything still under /md: admin/meta tools (reload, version, update, debug, pdc) plus track building, which stays here since it's 100% admin-only anyway. */
    private boolean handleMd(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) sendHelp(p);
            else sender.sendMessage("Use /md help");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            case "track" -> {
                return trackCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
            }

            case "help" -> {
                if (sender instanceof Player p) sendHelp(p);
                else sender.sendMessage("Commands: /md reload");
                return true;
            }

            case "reload" -> {
                if (!Commands.requireAdmin(sender)) return true;

                plugin.reloadConfig();
                if (mdConfig != null) mdConfig.reload();
                this.config = YamlConfiguration.loadConfiguration(this.filePath);

                // If a daily-time just got added that's sooner than whatever
                // the currently-open scheduled race window is counting down
                // to, pull it in to match instead of leaving it stuck
                // counting down to the stale time - see the method javadoc.
                if (scheduledRaceManager != null) scheduledRaceManager.onConfigReloaded();

                sender.sendMessage(ChatColor.GREEN + "MarbleDrop config reloaded.");
                return true;
            }

            case "version" -> {
                String running = updateChecker != null ? updateChecker.runningVersion() : plugin.getDescription().getVersion();
                sender.sendMessage(ChatColor.GREEN + "MarbleDrop " + ChatColor.YELLOW + "v" + running);

                String staged = updateChecker != null ? updateChecker.stagedVersion() : null;
                if (staged != null) {
                    sender.sendMessage(ChatColor.GRAY + "v" + staged + " is staged - restart the server to apply it.");
                }
                return true;
            }

            case "update" -> {
                if (!Commands.requireAdmin(sender)) return true;
                if (updateChecker == null) {
                    sender.sendMessage(ChatColor.RED + "Update checker isn't available.");
                    return true;
                }
                sender.sendMessage(ChatColor.GRAY + "Checking GitHub for a newer version...");
                updateChecker.checkNowFor(sender);
                return true;
            }

            // Regenerates plugins/Geyser-Spigot/custom_skulls.json from
            // heads.yml on demand - e.g. after adding a new marble head
            // without a full /md reload, or if Geyser was installed after
            // MarbleDrop already started. Geyser only reads the file at
            // its OWN startup, so this alone won't push new textures to
            // already-connected Bedrock players - a Geyser restart is
            // still needed for it to take effect.
            case "geysersync" -> {
                if (!Commands.requireAdmin(sender)) return true;
                GeyserSkullSync.sync(plugin, headPool);
                sender.sendMessage(ChatColor.GREEN + "Synced marble head textures to Geyser's custom_skulls.json "
                        + ChatColor.GRAY + "(restart/reload Geyser to apply).");
                return true;
            }

            case "debug" -> {
                Player player = Commands.player(sender);
                if (player == null) return true;
                if (!Commands.requirePermission(player, Commands.DEBUG)) return true;

                boolean next = !plugin.getConfig().getBoolean("debug.enabled", false);
                plugin.getConfig().set("debug.enabled", next);
                plugin.saveConfig();

                player.sendMessage(ChatColor.GRAY + "Debug: " +
                        (next ? ChatColor.GREEN + "enabled." : ChatColor.RED + "disabled."));

                if (mdConfig != null) mdConfig.reload();
                return true;
            }

            // Deliberately silent (no message) on failure, unlike every
            // other subcommand here - this is a raw PDC inspector for
            // devs, not something worth announcing the existence of to
            // whoever's poking at commands they don't have.
            case "pdc" -> {
                if (!(sender instanceof Player player)) return true;
                if (!player.hasPermission(Commands.DEBUG)) return true;

                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null || item.getType().isAir()) return true;

                ItemMeta meta = item.getItemMeta();
                if (meta == null) return true;

                PersistentDataContainer pdc = meta.getPersistentDataContainer();

                NamespacedKey legacyMarble = new NamespacedKey(plugin, "marble");
                NamespacedKey legacyTeam = new NamespacedKey(plugin, "marble_team");

                player.sendMessage(ChatColor.GOLD + "=== Marble Debug ===");
                player.sendMessage("legacy marble=" +
                        pdc.getOrDefault(legacyMarble, PersistentDataType.BYTE, (byte) 0));
                player.sendMessage("legacy team=" +
                        pdc.getOrDefault(legacyTeam, PersistentDataType.STRING, "null"));

                return true;
            }

            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /md help.");
                return true;
            }
        }
    }

    private boolean handleTasks(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            return tasksAdminCommand.onCommand(sender, cmd, label, shiftArgs(args, 1));
        }
        return tasksCommand.onCommand(sender, cmd, label, args);
    }

    private void sendHelp(Player player) {
        boolean admin = player.hasPermission("marbledrop.admin");

        if (admin) {
            player.sendMessage(ChatColor.GREEN + "MarbleDrop Admin Commands\n" +
                    ChatColor.DARK_GREEN + "/race\n" +
                    ChatColor.DARK_GREEN + "/tutorial\n" +
                    ChatColor.DARK_GREEN + "/team\n" +
                    ChatColor.DARK_GREEN + "/dust\n" +
                    ChatColor.DARK_GREEN + "/station\n" +
                    ChatColor.DARK_GREEN + "/tasks\n" +
                    ChatColor.DARK_GREEN + "/recipes\n" +
                    ChatColor.DARK_GREEN + "/md track\n" +
                    ChatColor.DARK_GREEN + "/md version\n" +
                    ChatColor.DARK_GREEN + "/md reload\n" +
                    ChatColor.DARK_GREEN + "/md update\n" +
                    ChatColor.DARK_GREEN + "/md geysersync\n" +
                    ChatColor.DARK_GREEN + "/md debug\n" +
                    ChatColor.DARK_GREEN + "/md pdc");
        } else {
            player.sendMessage(ChatColor.GREEN + "MarbleDrop Commands\n" +
                    ChatColor.DARK_GREEN + "/race\n" +
                    ChatColor.DARK_GREEN + "/tutorial\n" +
                    ChatColor.DARK_GREEN + "/team\n" +
                    ChatColor.DARK_GREEN + "/dust\n" +
                    ChatColor.DARK_GREEN + "/tasks\n" +
                    ChatColor.DARK_GREEN + "/recipes\n" +
                    ChatColor.DARK_GREEN + "/md version\n");
        }
    }
}
