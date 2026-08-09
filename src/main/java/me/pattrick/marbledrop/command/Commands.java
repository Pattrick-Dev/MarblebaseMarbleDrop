package me.pattrick.marbledrop.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Shared, standardized checks and messages for every command class in the
 * plugin -- before this, "not a player" and "no permission" each had 4-5
 * different wordings (some uncolored) scattered across the 13 command
 * classes, and usage-line color was split between yellow and red with no
 * pattern. One canonical message per concern, used everywhere.
 * <p>
 * The two check methods return different shapes on purpose: {@link #player}
 * hands back a typed, non-null {@code Player} (or null) so callers get a
 * usable local instead of a boolean plus a manual cast; the permission
 * checks return a plain boolean since there's nothing to bind. Both forms
 * are meant to be used as an early-return guard:
 * <pre>
 *     Player player = Commands.player(sender);
 *     if (player == null) return true;
 *     if (!Commands.requireAdmin(player)) return true;
 * </pre>
 */
public final class Commands {

    public static final String ADMIN = "marbledrop.admin";
    public static final String DEBUG = "marbledrop.debug";

    private Commands() {}

    /** Sends "Players only." and returns null if {@code sender} isn't an in-game player; otherwise returns it cast. */
    public static Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage(ChatColor.RED + "Players only.");
        return null;
    }

    /** Sends "You don't have permission." and returns false if {@code sender} lacks {@link #ADMIN}. */
    public static boolean requireAdmin(CommandSender sender) {
        return requirePermission(sender, ADMIN);
    }

    /** Sends "You don't have permission." and returns false if {@code sender} lacks {@code node}. */
    public static boolean requirePermission(CommandSender sender, String node) {
        if (sender.hasPermission(node)) return true;
        sender.sendMessage(ChatColor.RED + "You don't have permission.");
        return false;
    }

    /** Sends a standard yellow "Usage: " line per argument (each on its own message, for multi-line usage blocks). */
    public static void usage(CommandSender sender, String... lines) {
        for (String line : lines) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: " + line);
        }
    }

    /** Resolves an online player by exact name, or sends a standard "not found" message and returns null. */
    public static Player online(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found (must be online): " + ChatColor.YELLOW + name);
        }
        return target;
    }

    /** Parses a whole number argument, or sends a standard error (naming the field via {@code label}) and returns null. */
    public static Integer intArg(CommandSender sender, String raw, String label) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + label + " must be a whole number.");
            return null;
        }
    }
}
