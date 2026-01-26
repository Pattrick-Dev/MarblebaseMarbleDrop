package me.pattrick.marbledrop.progression.infusion;

import me.pattrick.marbledrop.HeadDatabase;
import me.pattrick.marbledrop.Marble;
import me.pattrick.marbledrop.MarbleItem;
import me.pattrick.marbledrop.MarbleRarity;
import me.pattrick.marbledrop.MarbleStats;
import me.pattrick.marbledrop.progression.DustManager;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.util.UUID;

public final class InfusionService {

    private final Plugin plugin;
    private final DustManager dust;
    private final RarityRoller rarityRoller;

    private final NamespacedKey K_ATTUNEMENT;

    // Optional compatibility: legacy flag some older checks may still use
    private final NamespacedKey K_LEGACY_MARBLE;

    // Reads the team from the item's PDC (source of truth)
    private final NamespacedKey K_TEAM;

    // NEW: per-player daily infusion cap tracking
    private final NamespacedKey K_INFUSE_DAY;   // int: YYYYMMDD
    private final NamespacedKey K_INFUSE_COUNT; // int: used today

    // Keep 3-arg constructor signature so Main doesn't break
    public InfusionService(Plugin plugin, DustManager dust, me.pattrick.marbledrop.progression.infusion.heads.HeadPool headPool) {
        this.plugin = plugin;
        this.dust = dust;
        this.rarityRoller = new RarityRoller();

        this.K_ATTUNEMENT = new NamespacedKey(plugin, "dust_attunement");
        this.K_LEGACY_MARBLE = new NamespacedKey(plugin, "marble");
        this.K_TEAM = new NamespacedKey(plugin, "marble_team");

        this.K_INFUSE_DAY = new NamespacedKey(plugin, "infuse_day");
        this.K_INFUSE_COUNT = new NamespacedKey(plugin, "infuse_count");
    }

    /**
     * Existing command behavior (gives the item directly).
     */
    public void infuse(Player player, int amount) {
        ItemStack result = infuseToItem(player, amount, 0);
        if (result == null) return;

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), result);
            player.sendMessage(ChatColor.GRAY + "Your inventory is full — the infused marble dropped at your feet.");
        } else {
            player.getInventory().addItem(result);
        }
    }

    /**
     * Produce the infused marble ItemStack for GUIs/animations.
     * bonusValue is hidden value from catalyst items.
     * Returns null if failed (and refunds dust if dust was taken).
     */
    public ItemStack infuseToItem(Player player, int amount, int bonusValue) {
        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Use: /dust infuse <amount>");
            return null;
        }

        int min = 50;
        if (amount < min) {
            player.sendMessage(ChatColor.RED + "Minimum infusion is " + min + " dust.");
            return null;
        }

        // NEW: daily cap (0 = unlimited)
        int cap = plugin.getConfig().getInt("infusion.daily-cap", 0);
        if (cap > 0) {
            int used = getInfusionsUsedToday(player);
            if (used >= cap) {
                player.sendMessage(ChatColor.RED + "You have reached your daily infusion limit (" + cap + ").");
                return null;
            }
        }

        // Take dust AFTER cap check
        if (!dust.takeDust(player, amount)) {
            player.sendMessage(ChatColor.RED + "You don't have enough Marble Dust.");
            return null;
        }

        Integer stored = player.getPersistentDataContainer().get(K_ATTUNEMENT, PersistentDataType.INTEGER);
        int attunement = stored != null ? Math.max(0, stored) : 0;

        int effective = amount + attunement + Math.max(0, bonusValue);

        // Roll rarity
        MarbleRarity rarity = rarityRoller.roll(effective);

        // Update attunement (hidden pity)
        int newAttunement = attunement;
        switch (rarity) {
            case COMMON -> newAttunement += Math.round(amount * 0.35f);
            case UNCOMMON -> newAttunement += Math.round(amount * 0.20f);
            case RARE -> newAttunement = Math.max(0, newAttunement - Math.round(amount * 0.30f));
            case EPIC -> newAttunement = Math.max(0, newAttunement - Math.round(amount * 0.60f));
            case LEGENDARY -> newAttunement = Math.max(0, newAttunement - Math.round(amount * 0.90f));
        }
        newAttunement = Math.min(newAttunement, 25000);
        player.getPersistentDataContainer().set(K_ATTUNEMENT, PersistentDataType.INTEGER, newAttunement);

        // Create marble head using your proven working database
        ItemStack item;
        try {
            item = HeadDatabase.getMarbleHead(player.getDisplayName());
        } catch (Exception ex) {
            dust.addDust(player, amount); // refund
            player.sendMessage(ChatColor.RED + "Infusion failed creating a marble head. Refunded dust.");
            ex.printStackTrace();
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            dust.addDust(player, amount); // refund
            player.sendMessage(ChatColor.RED + "Infusion failed (invalid item meta). Refunded dust.");
            return null;
        }

        String displayName = meta.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = ChatColor.WHITE + "Marble";
            meta.setDisplayName(displayName);
        }

        // TEAM from existing PDC (already set by HeadDatabase/Sampler)
        String team = readTeamFromPdc(meta);
        if (team == null || team.isEmpty()) team = "Neutral";

        // Stats
        MarbleStats stats = StatRoller.rollStats(rarity);
        if (stats == null) stats = new MarbleStats(1, 1, 1, 1, 1);

        // Apply NEW PDC model
        Marble marble = new Marble(UUID.randomUUID().toString(), displayName, team, rarity, stats);
        MarbleItem.applyToMeta(meta, marble);

        // Legacy flag for old checks/debug
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(K_LEGACY_MARBLE, PersistentDataType.BYTE, (byte) 1);

        // Visible lore
        meta.setLore(java.util.List.of(
                ChatColor.GRAY + "Team: " + ChatColor.WHITE + team,
                ChatColor.GRAY + "Rarity: " + rarityColor(rarity) + rarity.name(),
                "",
                ChatColor.GRAY + "Speed: " + ChatColor.WHITE + stats.speed(),
                ChatColor.GRAY + "Control: " + ChatColor.WHITE + stats.control(),
                ChatColor.GRAY + "Momentum: " + ChatColor.WHITE + stats.momentum(),
                ChatColor.GRAY + "Stability: " + ChatColor.WHITE + stats.stability(),
                ChatColor.GRAY + "Luck: " + ChatColor.WHITE + stats.luck()
        ));

        item.setItemMeta(meta);

        // NEW: increment daily infusion count ONLY on success
        if (cap > 0) {
            incrementInfusionsUsedToday(player);
            int usedNow = getInfusionsUsedToday(player);
            player.sendMessage(ChatColor.GRAY + "Infusions today: " + ChatColor.YELLOW + usedNow + ChatColor.GRAY + "/" + ChatColor.YELLOW + cap);
        }

        player.sendMessage(ChatColor.GRAY + "Infusion value: "
                + ChatColor.YELLOW + effective
                + ChatColor.GRAY + " (" + amount + " dust"
                + (bonusValue > 0 ? (" + " + bonusValue + " catalyst") : "")
                + ChatColor.GRAY + ")");

        return item;
    }

    private int getInfusionsUsedToday(Player player) {
        int today = yyyymmdd(LocalDate.now());
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        Integer day = pdc.get(K_INFUSE_DAY, PersistentDataType.INTEGER);
        Integer count = pdc.get(K_INFUSE_COUNT, PersistentDataType.INTEGER);

        if (day == null || day != today || count == null) {
            // reset for a new day / missing values
            pdc.set(K_INFUSE_DAY, PersistentDataType.INTEGER, today);
            pdc.set(K_INFUSE_COUNT, PersistentDataType.INTEGER, 0);
            return 0;
        }

        return Math.max(0, count);
    }

    private void incrementInfusionsUsedToday(Player player) {
        int today = yyyymmdd(LocalDate.now());
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        Integer day = pdc.get(K_INFUSE_DAY, PersistentDataType.INTEGER);
        Integer count = pdc.get(K_INFUSE_COUNT, PersistentDataType.INTEGER);

        if (day == null || day != today || count == null) {
            pdc.set(K_INFUSE_DAY, PersistentDataType.INTEGER, today);
            pdc.set(K_INFUSE_COUNT, PersistentDataType.INTEGER, 1);
        } else {
            pdc.set(K_INFUSE_COUNT, PersistentDataType.INTEGER, Math.max(0, count) + 1);
        }
    }

    private int yyyymmdd(LocalDate d) {
        return (d.getYear() * 10000) + (d.getMonthValue() * 100) + d.getDayOfMonth();
    }

    private String readTeamFromPdc(ItemMeta meta) {
        try {
            return meta.getPersistentDataContainer().get(K_TEAM, PersistentDataType.STRING);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String rarityColor(MarbleRarity r) {
        return switch (r) {
            case COMMON -> ChatColor.WHITE.toString();
            case UNCOMMON -> ChatColor.GREEN.toString();
            case RARE -> ChatColor.AQUA.toString();
            case EPIC -> ChatColor.LIGHT_PURPLE.toString();
            case LEGENDARY -> ChatColor.GOLD.toString();
        };
    }
}
