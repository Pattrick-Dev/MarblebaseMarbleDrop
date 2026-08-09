package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.MdConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Admin GUI for tuning races.scheduled.* (see config.yml) live, in-game --
 * no restart, no manual file editing. Writes straight to config.yml
 * (RaceScheduleMenuListener, same plugin.getConfig().set()+saveConfig()
 * pattern CommandKit's debug toggle already uses) then MdConfig#reload(),
 * which ScheduledRaceManager reads fresh every time it schedules a cycle
 * -- so a change here takes effect starting the next cycle, no different
 * from editing config.yml and running /md reload.
 */
public final class RaceScheduleMenu {

    public static final String TITLE = ChatColor.DARK_GREEN + "Race Schedule Settings";

    public static final int SLOT_INFO = 4;
    public static final int SLOT_ENABLED = 10;
    public static final int SLOT_WINNER_DUST = 11;
    public static final int SLOT_WINNER_DUST_AI = 12;
    public static final int SLOT_AI_FILL = 14;
    public static final int SLOT_AI_SHOW = 15;
    public static final int SLOT_CLOSE = 22;

    public enum Field {
        WINNER_DUST(SLOT_WINNER_DUST, "races.scheduled.winner-dust", "Winner Dust", Material.GLOWSTONE_DUST, 10, 0, 2000, "dust"),
        WINNER_DUST_AI(SLOT_WINNER_DUST_AI, "races.scheduled.winner-dust-vs-ai", "Winner Dust (vs AI)", Material.REDSTONE, 5, 0, 2000, "dust"),
        AI_FILL(SLOT_AI_FILL, "races.scheduled.ai-fill-count", "AI Fill Count", Material.SKELETON_SKULL, 1, 0, 10, "AI"),
        AI_SHOW(SLOT_AI_SHOW, "races.scheduled.ai-show-count", "AI Show Count", Material.WITHER_SKELETON_SKULL, 1, 0, 10, "AI");

        public final int slot;
        public final String path;
        public final String label;
        public final Material icon;
        public final int step;
        public final int min;
        public final int max;
        public final String unit;

        Field(int slot, String path, String label, Material icon, int step, int min, int max, String unit) {
            this.slot = slot;
            this.path = path;
            this.label = label;
            this.icon = icon;
            this.step = step;
            this.min = min;
            this.max = max;
            this.unit = unit;
        }

        public static Field bySlot(int slot) {
            for (Field f : values()) if (f.slot == slot) return f;
            return null;
        }
    }

    private RaceScheduleMenu() {}

    public static int currentValue(Field field, MdConfig cfg) {
        return switch (field) {
            case WINNER_DUST -> cfg.scheduledRaceWinnerDust();
            case WINNER_DUST_AI -> cfg.scheduledRaceWinnerDustVsAi();
            case AI_FILL -> cfg.scheduledRaceAiFillCount();
            case AI_SHOW -> cfg.scheduledRaceAiShowCount();
        };
    }

    public static void open(Player player, MdConfig cfg) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        inv.setItem(SLOT_INFO, item(Material.BOOK, ChatColor.LIGHT_PURPLE + "Schedule Settings",
                List.of(
                        ChatColor.GRAY + "Left-click: +step",
                        ChatColor.GRAY + "Right-click: -step",
                        ChatColor.GRAY + "Shift-click: x5 the step",
                        "",
                        ChatColor.DARK_GRAY + "Applies starting the next",
                        ChatColor.DARK_GRAY + "scheduled race cycle."
                )));

        inv.setItem(SLOT_ENABLED, toggleItem("Scheduled Races", cfg.scheduledRaceEnabled()));

        for (Field field : Field.values()) {
            inv.setItem(field.slot, numberItem(field, currentValue(field, cfg)));
        }

        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, ChatColor.RED + "Close", List.of()));

        player.openInventory(inv);
    }

    private static ItemStack toggleItem(String label, boolean on) {
        Material mat = on ? Material.LIME_DYE : Material.GRAY_DYE;
        String name = (on ? ChatColor.GREEN : ChatColor.RED) + label + ": " + (on ? "ON" : "OFF");
        return item(mat, name, List.of(ChatColor.GRAY + "Click to toggle"));
    }

    private static ItemStack numberItem(Field field, int value) {
        return item(field.icon, ChatColor.AQUA + field.label + ": " + ChatColor.WHITE + value + " " + field.unit,
                List.of(
                        ChatColor.GRAY + "Left-click: +" + field.step,
                        ChatColor.GRAY + "Right-click: -" + field.step,
                        ChatColor.GRAY + "Shift-click: x5"
                ));
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
