package me.pattrick.marbledrop.progression.taskmenu;

import me.pattrick.marbledrop.progression.TaskDefinition;
import me.pattrick.marbledrop.progression.TaskManager;
import me.pattrick.marbledrop.progression.TaskType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class TasksMenu {

    public static final String TITLE = ChatColor.DARK_GRAY + "Tasks";

    private final TaskManager taskManager;
    private final NamespacedKey taskIdKey;

    private ItemStack createResetInfoItem() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "Task Resets");

        long dailySecs = taskManager.getSecondsUntilDailyReset();
        long weeklySecs = taskManager.getSecondsUntilWeeklyReset();

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Daily resets in:");
        lore.add(ChatColor.AQUA + taskManager.formatDuration(dailySecs));
        lore.add("");
        lore.add(ChatColor.GRAY + "Weekly resets in:");
        lore.add(ChatColor.LIGHT_PURPLE + taskManager.formatDuration(weeklySecs));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }


    public TasksMenu(TaskManager taskManager, NamespacedKey taskIdKey) {
        this.taskManager = taskManager;
        this.taskIdKey = taskIdKey;
    }

    public void open(Player player) {
        taskManager.ensureResets(player);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        inv.setItem(22, createResetInfoItem());

        List<TaskDefinition> tasks = taskManager.getTasks();
        String trackedId = taskManager.getTrackedTaskId(player);

        int slot = 0;
        for (TaskDefinition t : tasks) {
            if (slot >= inv.getSize()) break;
            inv.setItem(slot++, createTaskItem(player, t, trackedId));
        }

        player.openInventory(inv);
    }

    private ItemStack createTaskItem(Player player, TaskDefinition t, String trackedId) {
        Material mat = (t.type() == TaskType.DAILY) ? Material.PAPER : Material.BOOK;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        int progress = taskManager.getProgress(player, t);
        boolean done = taskManager.isDone(player, t);
        boolean claimed = taskManager.isClaimed(player, t);
        boolean tracked = trackedId != null && trackedId.equalsIgnoreCase(t.id());

        // Store task ID for click handling
        meta.getPersistentDataContainer().set(taskIdKey, PersistentDataType.STRING, t.id());

        String nameColor = (t.type() == TaskType.DAILY)
                ? ChatColor.AQUA.toString()
                : ChatColor.LIGHT_PURPLE.toString();

        meta.setDisplayName(nameColor + t.displayName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Progress: " + ChatColor.YELLOW + progress + "/" + t.goal());
        lore.add(ChatColor.GRAY + "Reward: " + ChatColor.GOLD + t.rewardDust() + " dust");
        lore.add("");

        if (claimed) {
            lore.add(ChatColor.DARK_GRAY + "CLAIMED");
        } else if (done) {
            lore.add(ChatColor.GREEN + "COMPLETED");
        } else {
            lore.add(ChatColor.GRAY + "Click to track");
        }

        if (tracked) {
            lore.add(ChatColor.GOLD + "TRACKED");
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

}
