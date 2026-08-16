package me.pattrick.marbledrop.races;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The physical "build kit" handed to whoever creates (or resumes
 * building) a track: five right-click tools - point, undo, preview
 * toggle, set watch, finish - covering the whole build loop without
 * ever touching TrackGui's editor menu.
 * <p>
 * With ProtocolLib (see TrackBuildInventoryManager), every tool is a
 * packet-only overlay in a fixed hotbar slot (see {@link Tool#hotbarSlot}):
 * nothing is really added to the player's inventory, so there's nothing
 * real to remove again on finish either - ending the session just
 * resyncs the client back to their untouched real inventory. Without
 * ProtocolLib, the same five tools are handed out as ordinary real
 * items instead, identified purely by material + exact display name
 * (see {@link Tool#matchReal(ItemStack)}) since there's no fake-item
 * state to check in that path.
 */
public final class TrackCreationKit {

    public enum Tool {
        POINT(0, "track_point", Material.BLAZE_ROD, ChatColor.GOLD + "Point Tool",
                "Right-click a block to add a point there"),
        UNDO(1, "track_undo", Material.CLOCK, ChatColor.YELLOW + "Undo Last Point",
                "Right-click to remove the last waypoint"),
        PREVIEW(2, "track_preview", Material.SPYGLASS, ChatColor.AQUA + "Toggle Track Preview",
                "Right-click to show/hide the particle preview"),
        WATCH(3, "track_watch", Material.COMPASS, ChatColor.LIGHT_PURPLE + "Set Watch Spot (Here)",
                "Right-click to set the spectator watch spot here"),
        FINISH(4, "track_finish", Material.EMERALD, ChatColor.GREEN + "" + ChatColor.BOLD + "Finish Track",
                "Right-click when you're done building");

        public final int hotbarSlot;
        public final String tag;
        public final Material material;
        public final String displayName;
        public final String description;

        Tool(int hotbarSlot, String tag, Material material, String displayName, String description) {
            this.hotbarSlot = hotbarSlot;
            this.tag = tag;
            this.material = material;
            this.displayName = displayName;
            this.description = description;
        }

        public ItemStack icon() {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + description);
                lore.add(ChatColor.DARK_GRAY + "Admin build tool");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }

        /** The tool assigned to this fixed hotbar slot, or null if none is. */
        public static Tool bySlot(int hotbarSlot) {
            for (Tool t : values()) {
                if (t.hotbarSlot == hotbarSlot) return t;
            }
            return null;
        }

        /** Fallback-path identification: matches a real item by material + exact display name (no PDC involved). */
        public static Tool matchReal(ItemStack item) {
            if (item == null) return null;
            ItemMeta meta = item.getItemMeta();
            if (meta == null || meta.getDisplayName() == null) return null;

            for (Tool t : values()) {
                if (item.getType() == t.material && meta.getDisplayName().equals(t.displayName)) return t;
            }
            return null;
        }
    }

    private TrackCreationKit() {}

    /**
     * Enters build mode (see TrackBuildInventoryManager), gives every
     * build tool, and turns the particle preview on right away, so a
     * builder sees the track taking shape from the very first point
     * instead of needing to remember to toggle it on.
     */
    public static void giveKit(TrackBuildInventoryManager buildInventory, TrackVisualizer visualizer, Player player, String trackId) {
        buildInventory.enter(player, trackId);
        RaceInventoryOverlay overlay = buildInventory.overlay();

        for (Tool tool : Tool.values()) {
            if (overlay != null) {
                overlay.show(player, tool.hotbarSlot, tool.tag, tool.icon());
            } else {
                player.getInventory().addItem(tool.icon());
            }
        }

        visualizer.show(player, trackId);
    }
}
