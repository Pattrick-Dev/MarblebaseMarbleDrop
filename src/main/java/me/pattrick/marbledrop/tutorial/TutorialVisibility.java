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
 * <p>
 * {@code tabPrivacy} is nullable (null when ProtocolLib isn't installed --
 * see {@link TutorialTabListPrivacy#createIfAvailable}); when present, it
 * keeps hidden players' names in the tab list instead of letting
 * hideEntity's default "remove them entirely" behavior make it look like
 * they disconnected.
 */
final class TutorialVisibility {

    private TutorialVisibility() {}

    /** Call when a player transitions into the active tutorial state. */
    static void enter(Plugin plugin, Player player, TutorialTabListPrivacy tabPrivacy) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            hide(plugin, player, other, tabPrivacy);
        }
    }

    /**
     * Call when a player transitions OUT of the active tutorial state
     * (finish/skip/reset). Restores mutual visibility with every other
     * player who isn't themselves still active -- a still-active player
     * stays hidden on both sides until they finish too.
     */
    static void exit(Plugin plugin, Player player, TutorialManager tutorialManager, TutorialTabListPrivacy tabPrivacy) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (tutorialManager.isActive(other)) {
                hide(plugin, player, other, tabPrivacy);
            } else {
                player.showEntity(plugin, other);
                other.showEntity(plugin, player);
                if (tabPrivacy != null) {
                    tabPrivacy.stopKeepingListed(player, other);
                    tabPrivacy.stopKeepingListed(other, player);
                }
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
    static void syncOnJoin(Plugin plugin, Player joining, TutorialManager tutorialManager, TutorialTabListPrivacy tabPrivacy) {
        boolean joiningActive = tutorialManager.isActive(joining);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(joining)) continue;
            if (joiningActive || tutorialManager.isActive(other)) {
                hide(plugin, joining, other, tabPrivacy);
            }
        }
    }

    /** Mutually hides two players' entities, registering the tab-list protection (if available) before hideEntity can strip it. */
    private static void hide(Plugin plugin, Player a, Player b, TutorialTabListPrivacy tabPrivacy) {
        if (tabPrivacy != null) {
            tabPrivacy.keepListed(a, b);
            tabPrivacy.keepListed(b, a);
        }
        a.hideEntity(plugin, b);
        b.hideEntity(plugin, a);
    }
}
