package me.pattrick.marbledrop.progression;

import me.pattrick.marbledrop.SilentGive;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Shared give/remove/count logic for the three station commands
 * (MarbleRecyclerCommand, InfusionTableCommand, UpgradeStationCommand),
 * which otherwise repeat an almost line-for-line identical sequence with
 * only the StationType/manager and wording swapped.
 * <p>
 * A static helper rather than a common base class on purpose: the three
 * managers share no common interface today (removeRecycler/removeTable/
 * removeStation, count()/getTables().size()/getStations().size()), and
 * there's no fourth station type coming to justify introducing one just
 * for this. Each command class stays an independent, readable switch;
 * this only owns the repeated mechanics, driven off {@link StationType}
 * so none of the three station names are hardcoded here or in the
 * callers anymore.
 */
public final class StationCommands {

    private StationCommands() {}

    /** Gives the player one placeable item of this station type. Always returns true, so callers can write {@code return StationCommands.give(...)}. */
    public static boolean give(Plugin plugin, Player player, StationType type) {
        SilentGive.give(plugin, player, StationItems.create(plugin, type));
        player.sendMessage(ChatColor.GREEN + "Gave you a " + type.displayName()
                + ChatColor.GREEN + " - place it to create a station.");
        return true;
    }

    /** Reports how many stations of this type are currently registered. */
    public static boolean count(Player player, String plural, int amount) {
        player.sendMessage(ChatColor.GRAY + plural + ": " + ChatColor.WHITE + amount);
        return true;
    }

    /**
     * Un-registers whatever station the player is looking at (within 6
     * blocks): {@code unregister} is the manager's own removeX(Location),
     * {@code ambient} its matching ambient cleanup (nullable if that
     * station type has no ambient hologram).
     */
    public static boolean remove(Player player, StationType type, Predicate<Location> unregister, Consumer<Location> ambient) {
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Look at the " + type.displayName()
                    + ChatColor.RED + " (within 6 blocks).");
            return true;
        }

        Location loc = target.getLocation();
        boolean ok = unregister.test(loc);
        if (ok && ambient != null) {
            ambient.accept(loc);
        }

        player.sendMessage(ok
                ? (ChatColor.GREEN + "Removed " + type.displayName() + ChatColor.GREEN + " registration.")
                : (ChatColor.YELLOW + "This is not a registered " + type.displayName() + ChatColor.YELLOW + "."));
        return true;
    }
}
