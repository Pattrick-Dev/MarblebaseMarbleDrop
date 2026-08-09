package me.pattrick.marbledrop.tutorial;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * While a player is actively taking the tutorial, they're hidden from
 * every other online player and every other online player is hidden from
 * them -- onboarding should feel like a private instance, not something
 * shared with whoever else happens to be online. Two concurrent tutorial-
 * takers stay mutually hidden from each other too, same as anyone else.
 */
final class TutorialVisibility {

    private TutorialVisibility() {}

    /** Call when a player transitions into the active tutorial state. */
    static void enter(Plugin plugin, Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            player.hideEntity(plugin, other);
            other.hideEntity(plugin, player);
        }
    }

    /**
     * Call when a player transitions OUT of the active tutorial state
     * (finish/skip/reset). Restores mutual visibility with every other
     * player who isn't themselves still active -- a still-active player
     * stays hidden on both sides until they finish too.
     */
    static void exit(Plugin plugin, Player player, TutorialManager tutorialManager) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (tutorialManager.isActive(other)) {
                player.hideEntity(plugin, other);
                other.hideEntity(plugin, player);
            } else {
                player.showEntity(plugin, other);
                other.showEntity(plugin, player);
            }
        }
    }

    /**
     * Call on every join, regardless of whether the joining player is
     * themselves active -- a fresh connection inherits no prior hidden/
     * shown state, so this has to reconcile both directions: hide the
     * joiner from/to any already-active player, even if the joiner isn't
     * active themselves.
     */
    static void syncOnJoin(Plugin plugin, Player joining, TutorialManager tutorialManager) {
        boolean joiningActive = tutorialManager.isActive(joining);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(joining)) continue;
            if (joiningActive || tutorialManager.isActive(other)) {
                joining.hideEntity(plugin, other);
                other.hideEntity(plugin, joining);
            }
        }
    }
}
