package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.MdConfig;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;

/**
 * Click handling for RaceScheduleMenu. Stateless -- every click re-reads
 * the current value straight from MdConfig, writes the adjustment back to
 * config.yml, reloads MdConfig, and redraws, so there's no per-player
 * state to track (unlike menus tied to a specific block/marble).
 */
public final class RaceScheduleMenuListener implements Listener {

    private final Plugin plugin;
    private final MdConfig cfg;

    public RaceScheduleMenuListener(Plugin plugin, MdConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!RaceScheduleMenu.TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        if (slot == RaceScheduleMenu.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (slot == RaceScheduleMenu.SLOT_ENABLED) {
            boolean next = !cfg.scheduledRaceEnabled();
            plugin.getConfig().set("races.scheduled.enabled", next);
            plugin.saveConfig();
            cfg.reload();

            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
            RaceScheduleMenu.open(player, cfg);
            return;
        }

        RaceScheduleMenu.Field field = RaceScheduleMenu.Field.bySlot(slot);
        if (field == null) return;

        int current = RaceScheduleMenu.currentValue(field, cfg);
        int delta = field.step * (event.isShiftClick() ? 5 : 1);
        int next = event.isRightClick() ? current - delta : current + delta;
        next = Math.max(field.min, Math.min(field.max, next));

        plugin.getConfig().set(field.path, next);
        plugin.saveConfig();
        cfg.reload();

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
        RaceScheduleMenu.open(player, cfg);
    }
}
