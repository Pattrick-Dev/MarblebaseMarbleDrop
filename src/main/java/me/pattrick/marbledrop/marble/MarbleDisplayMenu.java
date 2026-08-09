package me.pattrick.marbledrop.marble;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Per-marble display customization -- right-click your own placed marble
 * to toggle whether THAT specific one shows a hologram and/or particles
 * (see MarbleDisplayAmbient, MarbleDisplayMenuListener). Settings live
 * directly on the block's own PersistentDataContainer
 * (SHOW_HOLOGRAM/SHOW_PARTICLES), not a separate registry -- the block is
 * already the natural per-marble storage, same as PLACED_BY/PLACED_NAME.
 * Both default to enabled when absent (a marble placed before this
 * existed, or freshly placed, has neither key set yet).
 */
public final class MarbleDisplayMenu {

    public static final String TITLE = ChatColor.DARK_GRAY + "Marble Display Settings";

    public static final int SLOT_INFO = 4;
    public static final int SLOT_HOLOGRAM = 11;
    public static final int SLOT_PARTICLES = 13;
    public static final int SLOT_STYLE = 15;
    public static final int SLOT_CLOSE = 22;

    private MarbleDisplayMenu() {}

    public static boolean isHologramEnabled(PersistentDataContainer pdc) {
        Byte v = pdc.get(MarbleKeys.SHOW_HOLOGRAM, PersistentDataType.BYTE);
        return v == null || v != 0;
    }

    public static boolean isParticlesEnabled(PersistentDataContainer pdc) {
        Byte v = pdc.get(MarbleKeys.SHOW_PARTICLES, PersistentDataType.BYTE);
        return v == null || v != 0;
    }

    public static MarbleParticleStyle styleOf(PersistentDataContainer pdc) {
        return MarbleParticleStyle.byId(pdc.get(MarbleKeys.PARTICLE_STYLE, PersistentDataType.STRING));
    }

    public static void open(Player player, MarbleData data, PersistentDataContainer pdc, PlayerProfile texture) {
        boolean hologramOn = isHologramEnabled(pdc);
        boolean particlesOn = isParticlesEnabled(pdc);
        MarbleParticleStyle style = styleOf(pdc);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        inv.setItem(SLOT_INFO, infoItem(data, texture));
        inv.setItem(SLOT_HOLOGRAM, toggleItem("Hologram", hologramOn));
        inv.setItem(SLOT_PARTICLES, toggleItem("Particles", particlesOn));
        inv.setItem(SLOT_STYLE, styleItem(style));
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, ChatColor.RED + "Close", List.of()));

        player.openInventory(inv);
    }

    /** Info item wears the same head texture as the marble it represents, instead of a plain Steve head. */
    private static ItemStack infoItem(MarbleData data, PlayerProfile texture) {
        MarbleRarity rarity = data.getRarity() == null ? MarbleRarity.COMMON : data.getRarity();
        ItemStack it = item(Material.PLAYER_HEAD, ChatColor.YELLOW + "Display Settings",
                List.of(ChatColor.GRAY + "Rarity: " + MarbleItem.rarityColor(rarity) + rarity.name()));

        if (texture != null && it.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setPlayerProfile(texture);
            it.setItemMeta(skullMeta);
        }

        return it;
    }

    private static ItemStack styleItem(MarbleParticleStyle style) {
        return item(style.icon(), ChatColor.AQUA + "Particle Style: " + ChatColor.WHITE + style.displayName(),
                List.of(ChatColor.GRAY + "Click to choose a style"));
    }

    private static ItemStack toggleItem(String label, boolean on) {
        Material mat = on ? Material.LIME_DYE : Material.GRAY_DYE;
        String name = (on ? ChatColor.GREEN : ChatColor.RED) + label + ": " + (on ? "ON" : "OFF");
        return item(mat, name, List.of(ChatColor.GRAY + "Click to toggle"));
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
