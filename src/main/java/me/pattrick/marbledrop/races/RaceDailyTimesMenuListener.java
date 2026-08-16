package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.MdConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Click + chat handling for RaceDailyTimesMenu. Removing a time is a plain
 * click (see the menu's own javadoc on why removal is done by sorted-list
 * index, not by re-parsing the clicked item's display name); adding one
 * needs free-text input a chest GUI can't take directly, so it follows the
 * same "close the GUI, prompt in chat, capture the next message" pattern
 * TrackGuiListener already uses for naming a new track.
 */
public final class RaceDailyTimesMenuListener implements Listener {

    private final Plugin plugin;
    private final MdConfig cfg;
    private final ScheduledRaceManager scheduledRaces;

    private final Map<UUID, Boolean> awaitingNewTime = new HashMap<>();

    public RaceDailyTimesMenuListener(Plugin plugin, MdConfig cfg, ScheduledRaceManager scheduledRaces) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.scheduledRaces = scheduledRaces;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!RaceDailyTimesMenu.TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        if (slot == RaceDailyTimesMenu.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (slot == RaceDailyTimesMenu.SLOT_BACK) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
            RaceScheduleMenu.open(player, cfg);
            return;
        }

        if (slot == RaceDailyTimesMenu.SLOT_ADD) {
            awaitingNewTime.put(player.getUniqueId(), true);
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Type a time in chat - 24-hour HH:mm (e.g. 14:30). Type 'cancel' to abort.");
            return;
        }

        List<LocalTime> times = RaceDailyTimesMenu.sortedTimes(cfg);
        if (slot < 0 || slot >= times.size()) return; // empty slot / not a time row

        LocalTime removedTime = times.get(slot);
        removeTime(removedTime);

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.9f);
        player.sendMessage(ChatColor.YELLOW + "Removed " + removedTime + " from the schedule.");
        RaceDailyTimesMenu.open(player, cfg);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uid = p.getUniqueId();

        if (!awaitingNewTime.containsKey(uid)) return;

        e.setCancelled(true);
        String msg = e.getMessage().trim();

        if (msg.equalsIgnoreCase("cancel")) {
            awaitingNewTime.remove(uid);
            p.sendMessage(ChatColor.YELLOW + "Cancelled.");
            Bukkit.getScheduler().runTask(plugin, () -> RaceDailyTimesMenu.open(p, cfg));
            return;
        }

        LocalTime parsed;
        try {
            parsed = LocalTime.parse(msg);
        } catch (DateTimeParseException ex) {
            p.sendMessage(ChatColor.RED + "Invalid time - use 24-hour HH:mm (e.g. 14:30). Try again or type 'cancel'.");
            return;
        }

        awaitingNewTime.remove(uid);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (cfg.scheduledRaceDailyTimes().contains(parsed)) {
                p.sendMessage(ChatColor.RED + parsed.toString() + " is already on the schedule.");
            } else {
                addTime(parsed);
                p.sendMessage(ChatColor.GREEN + "Added " + parsed + " to the schedule.");
            }
            RaceDailyTimesMenu.open(p, cfg);
        });
    }

    /** Admin disconnected mid "type a new time" prompt - drop the pending state instead of leaking their UUID forever. */
    @EventHandler(ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent e) {
        awaitingNewTime.remove(e.getPlayer().getUniqueId());
    }

    private void addTime(LocalTime time) {
        List<String> raw = new ArrayList<>(plugin.getConfig().getStringList("races.scheduled.daily-times"));
        raw.add(time.toString());
        saveAndReload(raw);
    }

    private void removeTime(LocalTime time) {
        List<String> raw = new ArrayList<>(plugin.getConfig().getStringList("races.scheduled.daily-times"));
        raw.removeIf(s -> {
            try {
                return LocalTime.parse(s.trim()).equals(time);
            } catch (DateTimeParseException ex) {
                return false; // leave unparsable entries alone rather than guessing
            }
        });
        saveAndReload(raw);
    }

    private void saveAndReload(List<String> raw) {
        plugin.getConfig().set("races.scheduled.daily-times", raw);
        plugin.saveConfig();
        cfg.reload();

        // Same "pull a currently-open window's countdown in if it's now
        // stale" resync /md reload triggers - a GUI edit is just as capable
        // of adding a sooner time as hand-editing config.yml is.
        if (scheduledRaces != null) scheduledRaces.onConfigReloaded();
    }
}
