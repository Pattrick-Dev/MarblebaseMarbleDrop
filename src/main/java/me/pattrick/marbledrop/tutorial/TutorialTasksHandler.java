package me.pattrick.marbledrop.tutorial;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The TASKS tutorial step's mini-task: kill one spawned sheep. This is
 * intentionally NOT the real task/progression system -- it's a simple,
 * self-contained, one-shot objective just for onboarding, shown via its
 * own small GUI (TutorialTaskGui) rather than the real Tasks menu.
 * <p>
 * Only one sheep can be active per player at a time (re-running /md
 * tasks while one is alive just reopens the GUI, it doesn't spawn a
 * second or re-give a weapon), and only a kill by the owning player
 * counts, so this can't be farmed for repeat rewards. The sheep never
 * drops loot or XP, regardless of who/what kills it.
 */
public final class TutorialTasksHandler implements Listener {

    private final TutorialManager tutorialManager;

    // player UUID -> the specific sheep entity UUID assigned to them
    private final Map<UUID, UUID> activeSheep = new ConcurrentHashMap<>();

    public TutorialTasksHandler(TutorialManager tutorialManager) {
        this.tutorialManager = tutorialManager;
    }

    /** Called by TutorialListener when a player runs /md tasks on the TASKS step. */
    public void beginTask(Player player) {
        UUID existing = activeSheep.get(player.getUniqueId());
        if (existing != null) {
            // Already have one out there -- just reopen the GUI as a reminder,
            // don't spawn a second sheep or hand out a second weapon.
            TutorialTaskGui.open(player);
            return;
        }

        Location loc = findSafeSheepSpawn(player);
        Sheep sheep = (Sheep) loc.getWorld().spawnEntity(loc, EntityType.SHEEP);
        sheep.setCustomName(ChatColor.YELLOW + player.getName() + "'s Task Sheep");
        sheep.setCustomNameVisible(true);
        sheep.setRemoveWhenFarAway(false);

        activeSheep.put(player.getUniqueId(), sheep.getUniqueId());

        player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
        player.sendMessage(ChatColor.GREEN + "Task: " + ChatColor.WHITE + "kill the sheep that just spawned! " +
                ChatColor.GRAY + "(A weapon was added to your inventory.)");

        TutorialTaskGui.open(player);
    }

    /**
     * Finds an open spot near the player to spawn the task sheep. A blind
     * fixed offset can land inside a wall (the sheep then suffocates
     * immediately and dies with no killer, which -- while not a hard
     * softlock, since onSheepDeath clears the assignment -- is a bad
     * first impression). Falls back to the player's own square, which is
     * always safe since they're already standing there.
     */
    private Location findSafeSheepSpawn(Player player) {
        Location base = player.getLocation();
        int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}};

        for (int[] off : offsets) {
            Location candidate = base.clone().add(off[0], 0, off[1]);
            if (isOpenGround(candidate)) return candidate;
        }
        return base.clone();
    }

    private boolean isOpenGround(Location loc) {
        Block feet = loc.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);
        return !feet.getType().isSolid() && !head.getType().isSolid() && ground.getType().isSolid();
    }

    /** Purely cosmetic GUI -- nothing in it should be takeable. */
    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (!TutorialTaskGui.TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onSheepDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Sheep sheep)) return;

        UUID sheepId = sheep.getUniqueId();

        UUID ownerId = null;
        for (Map.Entry<UUID, UUID> entry : activeSheep.entrySet()) {
            if (entry.getValue().equals(sheepId)) {
                ownerId = entry.getKey();
                break;
            }
        }
        if (ownerId == null) return; // not a tutorial sheep, ignore entirely

        // Tutorial prop -- never drops loot or XP, no matter who/what kills it.
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player killer = sheep.getKiller();
        if (killer == null || !killer.getUniqueId().equals(ownerId)) {
            // Someone/something else killed this player's sheep. Clear the
            // assignment so /md tasks gives them a fresh one instead of
            // soft-locking the step.
            activeSheep.remove(ownerId);
            return;
        }

        activeSheep.remove(ownerId);
        removeTutorialSword(killer);
        tutorialManager.completeStep(killer, TutorialStep.TASKS);
    }

    /**
     * Inventory.removeItem(new ItemStack(WOODEN_SWORD)) matches by full
     * item similarity, which includes durability -- a fresh (undamaged)
     * comparison ItemStack won't match the player's sword once it's taken
     * any damage from hitting the sheep, so it silently fails to remove
     * anything. Scan for the material directly instead.
     */
    private void removeTutorialSword(Player player) {
        var inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].getType() == Material.WOODEN_SWORD) {
                inv.setItem(i, null);
                return;
            }
        }
        if (inv.getItemInOffHand().getType() == Material.WOODEN_SWORD) {
            inv.setItemInOffHand(null);
        }
    }

    public void handleQuit(Player player) {
        activeSheep.remove(player.getUniqueId());
        // The sheep entity itself is left alone -- it just becomes a normal
        // (loot-clearing no longer applies once it's not tracked) sheep.
    }
}
