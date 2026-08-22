package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.MdConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Admin GUI listing the actual clock times scheduled races run at (see
 * MdConfig#scheduledRaceDailyTimes(), races.scheduled.daily-times in
 * config.yml) - opened from the "Daily Times" button in RaceScheduleMenu.
 * Times are drawn sorted, one Clock item per slot in reading order, so a
 * clicked slot's index doubles as its position in that same sorted list -
 * see RaceDailyTimesMenuListener, which reads the list back the same way
 * rather than round-tripping through the item's display name (config
 * could round-trip a raw string like "14:30:00" through LocalTime#toString()
 * as "14:30", which would silently fail to match by string).
 */
public final class RaceDailyTimesMenu {

    public static final String TITLE = ChatColor.DARK_GREEN + "Scheduled Race Times";

    private static final int MAX_TIMES_SHOWN = 45; // slots 0..44 (bottom row reserved for controls)
    public static final int SLOT_ADD = 48;
    public static final int SLOT_BACK = 49;
    public static final int SLOT_CLOSE = 50;

    private RaceDailyTimesMenu() {}

    /** Same sort order the GUI is drawn in - see the class javadoc on why slot index has to line up with this exactly. */
    public static List<LocalTime> sortedTimes(MdConfig cfg) {
        List<LocalTime> times = new ArrayList<>(cfg.scheduledRaceDailyTimes());
        Collections.sort(times);
        return times;
    }

    public static void open(Player player, MdConfig cfg) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        List<LocalTime> times = sortedTimes(cfg);
        for (int i = 0; i < times.size() && i < MAX_TIMES_SHOWN; i++) {
            inv.setItem(i, item(Material.CLOCK, ChatColor.AQUA + times.get(i).toString(),
                    List.of(ChatColor.GRAY + "Click to remove this time")));
        }

        inv.setItem(SLOT_ADD, item(Material.LIME_DYE, ChatColor.GREEN + "Add a Time",
                List.of(
                        ChatColor.GRAY + "Click, then type a time in chat.",
                        ChatColor.GRAY + "Format: HH:mm, 24-hour, " + cfg.scheduledRaceTimeZone().getId() + ".",
                        ChatColor.GRAY + "Example: 14:30"
                )));
        inv.setItem(SLOT_BACK, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of()));
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, ChatColor.RED + "Close", List.of()));

        player.openInventory(inv);
    }

    private static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }
}
