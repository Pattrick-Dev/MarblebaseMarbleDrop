package me.pattrick.marbledrop.progression;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;

/**
 * The three craftable "station" blocks. Each wraps a vanilla block
 * (cauldron, smithing table, grindstone) that would otherwise behave
 * normally -- only a block placed from a StationItems-tagged ItemStack
 * of the matching type is treated as a real station.
 */
public enum StationType {

    INFUSION_TABLE(Material.CAULDRON, ChatColor.AQUA + "Infusion Cauldron", List.of(
            ChatColor.GRAY + "Place to create a marble",
            ChatColor.GRAY + "infusion station."
    )),
    UPGRADE_STATION(Material.SMITHING_TABLE, ChatColor.LIGHT_PURPLE + "Upgrade Station", List.of(
            ChatColor.GRAY + "Place to create a marble",
            ChatColor.GRAY + "upgrade station."
    )),
    RECYCLER(Material.GRINDSTONE, ChatColor.GOLD + "Marble Recycler", List.of(
            ChatColor.GRAY + "Place to create a marble",
            ChatColor.GRAY + "recycling station."
    ));

    private final Material blockType;
    private final String displayName;
    private final List<String> lore;

    StationType(Material blockType, String displayName, List<String> lore) {
        this.blockType = blockType;
        this.displayName = displayName;
        this.lore = lore;
    }

    public Material blockType() {
        return blockType;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }
}
