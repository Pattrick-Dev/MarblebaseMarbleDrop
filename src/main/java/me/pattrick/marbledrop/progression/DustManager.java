package me.pattrick.marbledrop.progression;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Virtual currency stored on the player's PersistentDataContainer.
 */
public final class DustManager {

    private final NamespacedKey K_DUST;

    public DustManager(Plugin plugin) {
        this.K_DUST = new NamespacedKey(plugin, "marble_dust");
    }

    public int getDust(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Integer val = pdc.get(K_DUST, PersistentDataType.INTEGER);
        return val != null ? Math.max(0, val) : 0;
    }

    public void setDust(Player player, int amount) {
        int safe = Math.max(0, amount);
        player.getPersistentDataContainer().set(K_DUST, PersistentDataType.INTEGER, safe);
    }

    public void addDust(Player player, int amount) {
        if (amount <= 0) return;
        int current = getDust(player);
        setDust(player, current + amount);
    }

    public boolean takeDust(Player player, int amount) {
        if (amount <= 0) return true;
        int current = getDust(player);
        if (current < amount) return false;
        setDust(player, current - amount);
        return true;
    }
}
