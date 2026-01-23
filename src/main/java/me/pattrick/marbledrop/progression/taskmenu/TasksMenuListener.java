package me.pattrick.marbledrop.progression.taskmenu;

import me.pattrick.marbledrop.progression.TaskDefinition;
import me.pattrick.marbledrop.progression.TaskManager;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class TasksMenuListener implements Listener {

    private final TaskManager taskManager;
    private final TasksMenu menu;
    private final NamespacedKey K_TASK_ID;

    public TasksMenuListener(Plugin plugin, TaskManager taskManager) {
        this.taskManager = taskManager;
        this.K_TASK_ID = new NamespacedKey(plugin, "tasks_menu_task_id");
        this.menu = new TasksMenu(taskManager, K_TASK_ID);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(TasksMenu.TITLE)) return;

        e.setCancelled(true);

        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null) return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        Player player = (Player) e.getWhoClicked();

        String taskId = meta.getPersistentDataContainer().get(K_TASK_ID, PersistentDataType.STRING);
        if (taskId == null) return;

        TaskDefinition t = taskManager.getTaskById(taskId);
        if (t == null) return;

        // completed tasks can't be tracked
        if (taskManager.isDone(player, t) || taskManager.isClaimed(player, t)) {
            player.sendMessage(ChatColor.GRAY + "That task is already completed.");
            player.sendActionBar("");
            menu.open(player);
            return;
        }


        String tracked = taskManager.getTrackedTaskId(player);
        if (tracked != null && tracked.equalsIgnoreCase(t.id())) {
            taskManager.clearTrackedTask(player);
            player.sendActionBar("");
            player.sendMessage(ChatColor.GRAY + "Stopped tracking: " + ChatColor.WHITE + t.displayName());
        } else {
            taskManager.setTrackedTask(player, t.id());
            player.sendMessage(ChatColor.GREEN + "Now tracking: " + ChatColor.WHITE + t.displayName());
        }

        // refresh
        menu.open(player);
    }

    // Helper used by TasksMenu to embed task id in item meta
    public NamespacedKey getTaskIdKey() {
        return K_TASK_ID;
    }
}
