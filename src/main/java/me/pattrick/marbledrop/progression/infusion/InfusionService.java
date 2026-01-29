package me.pattrick.marbledrop.progression.infusion;

import me.pattrick.marbledrop.HeadDatabase;
import me.pattrick.marbledrop.marble.MarbleData;
import me.pattrick.marbledrop.marble.MarbleItem;
import me.pattrick.marbledrop.marble.MarbleKeys;
import me.pattrick.marbledrop.marble.MarbleRarity;
import me.pattrick.marbledrop.marble.MarbleStats;
import me.pattrick.marbledrop.progression.DustManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class InfusionService {

    private final Plugin plugin;
    private final DustManager dust;

    private final NamespacedKey K_ATTUNEMENT;

    // per-player daily infusion cap tracking
    private final NamespacedKey K_INFUSE_DAY;   // int: YYYYMMDD
    private final NamespacedKey K_INFUSE_COUNT; // int: used today

    // Permission that bypasses daily infusion cap (and also avoids incrementing the counter)
    private static final String PERM_BYPASS_INFUSION_LIMIT = "marbledrop.infusion.bypass";

    // Keep 3-arg constructor signature so Main doesn't break
    public InfusionService(Plugin plugin, DustManager dust, me.pattrick.marbledrop.progression.infusion.heads.HeadPool headPool) {
        this.plugin = plugin;
        this.dust = dust;

        this.K_ATTUNEMENT = new NamespacedKey(plugin, "dust_attunement");

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
     * Backwards compatible method used by existing code.
     * bonusValue is hidden value from catalyst items.
     */
    public ItemStack infuseToItem(Player player, int amount, int bonusValue) {
        return infuseToItem(player, amount, bonusValue, null);
    }

    /**
     * Produce the infused marble ItemStack with optional marble catalyst rarity bias.
     * catalystRarity comes from a marble catalyst (MODERN schema).
     *
     * Returns null if failed (and refunds dust if dust was taken).
     */
    public ItemStack infuseToItem(Player player, int amount, int bonusValue, MarbleRarity catalystRarity) {
        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Use: /dust infuse <amount>");
            return null;
        }

        int min = 50;
        if (amount < min) {
            player.sendMessage(ChatColor.RED + "Minimum infusion is " + min + " dust.");
            return null;
        }

        // daily cap (0 = unlimited) + admin bypass
        int cap = plugin.getConfig().getInt("infusion.daily-cap", 0);
        boolean bypass = player.isOp() || player.hasPermission(PERM_BYPASS_INFUSION_LIMIT);

        if (cap > 0 && !bypass) {
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

        // Roll rarity (biased if catalyst marble rarity present)
        MarbleRarity rarity = rollWithCatalystBias(effective, catalystRarity);

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

        // Ensure a name exists
        String displayName = meta.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = ChatColor.WHITE + "Marble";
            meta.setDisplayName(displayName);
        }

        // TEAM: modern key only (if your head generator doesn't set it yet, it will default)
        String team = readTeamFromModernPdc(meta);
        if (team == null || team.isEmpty()) team = "Neutral";

        // Stats: modern stats only
        MarbleStats stats = StatRoller.rollStats(rarity);


        // Write MODERN schema (single system)
        String marbleKey = teamKeyFromTeam(team);

        MarbleData data = new MarbleData(
                UUID.randomUUID(),
                marbleKey,
                team,
                rarity,
                stats,
                player.getUniqueId(),
                System.currentTimeMillis(),
                0,
                1
        );

        // Set any cosmetic/lore changes, then write schema LAST.
        meta.setLore(List.of(
                ChatColor.GRAY + "Team: " + ChatColor.WHITE + team,
                ChatColor.GRAY + "Rarity: " + rarityColor(rarity) + rarity.name(),
                "",
                ChatColor.GRAY + "Speed: " + ChatColor.WHITE + stats.get(me.pattrick.marbledrop.marble.MarbleStat.SPEED),
                ChatColor.GRAY + "Accel: " + ChatColor.WHITE + stats.get(me.pattrick.marbledrop.marble.MarbleStat.ACCEL),
                ChatColor.GRAY + "Handling: " + ChatColor.WHITE + stats.get(me.pattrick.marbledrop.marble.MarbleStat.HANDLING),
                ChatColor.GRAY + "Stability: " + ChatColor.WHITE + stats.get(me.pattrick.marbledrop.marble.MarbleStat.STABILITY),
                ChatColor.GRAY + "Boost: " + ChatColor.WHITE + stats.get(me.pattrick.marbledrop.marble.MarbleStat.BOOST)
        ));
        item.setItemMeta(meta);

        // ✅ The one-and-only marble schema
        MarbleItem.write(item, data);

        // increment daily infusion count ONLY on success (and do not increment for bypass users)
        if (cap > 0 && !bypass) {
            incrementInfusionsUsedToday(player);
            int usedNow = getInfusionsUsedToday(player);
            player.sendMessage(ChatColor.GRAY + "Infusions today: " + ChatColor.YELLOW + usedNow + ChatColor.GRAY + "/" + ChatColor.YELLOW + cap);
        }

        player.sendMessage(ChatColor.GRAY + "Infusion value: "
                + ChatColor.YELLOW + effective
                + ChatColor.GRAY + " (" + amount + " dust"
                + (bonusValue > 0 ? (" + " + bonusValue + " catalyst") : "")
                + (catalystRarity != null ? (ChatColor.GRAY + " + " + rarityColor(catalystRarity) + catalystRarity.name() + ChatColor.GRAY + " marble") : "")
                + ChatColor.GRAY + ")");

        return item;
    }

    /**
     * Reads catalyst rarity from a MODERN marble item.
     * Returns null if the item is not a modern marble.
     */
    public MarbleRarity readMarbleCatalystRarity(ItemStack item) {
        if (item == null) return null;
        if (!MarbleItem.isMarble(item)) return null;

        MarbleData data = MarbleItem.read(item);
        if (data == null) return null;

        return data.getRarity();
    }

    private MarbleRarity rollWithCatalystBias(int effectiveValue, MarbleRarity catalystRarity) {
        // Base roll (modern roller)
        MarbleRarity best = RarityRoller.roll(effectiveValue);

        if (catalystRarity == null) {
            return best;
        }

        int extraRolls;
        double floorChance;

        switch (catalystRarity) {
            case UNCOMMON -> {
                extraRolls = 1;
                floorChance = 0.20;
            }
            case RARE -> {
                extraRolls = 1;
                floorChance = 0.30;
            }
            case EPIC -> {
                extraRolls = 2;
                floorChance = 0.40;
            }
            case LEGENDARY -> {
                extraRolls = 2;
                floorChance = 0.50;
            }
            default -> {
                extraRolls = 0;
                floorChance = 0.0;
            }
        }

        // Extra rerolls – take best
        for (int i = 0; i < extraRolls; i++) {
            MarbleRarity rolled = RarityRoller.roll(effectiveValue);
            if (rolled.ordinal() > best.ordinal()) {
                best = rolled;
            }
        }

        // Soft floor chance
        if (best.ordinal() < catalystRarity.ordinal()) {
            if (ThreadLocalRandom.current().nextDouble() < floorChance) {
                best = catalystRarity;
            }
        }

        return best;
    }



    private String teamKeyFromTeam(String team) {
        if (team == null) return "neutral";
        String cleaned = ChatColor.stripColor(team);
        if (cleaned == null) cleaned = team;

        cleaned = cleaned.trim().toLowerCase(Locale.ROOT);
        if (cleaned.isEmpty()) return "neutral";

        cleaned = cleaned.replace(' ', '_');

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            }
        }
        String out = sb.toString();
        return out.isEmpty() ? "neutral" : out;
    }

    private int getInfusionsUsedToday(Player player) {
        int today = yyyymmdd(LocalDate.now());
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        Integer day = pdc.get(K_INFUSE_DAY, PersistentDataType.INTEGER);
        Integer count = pdc.get(K_INFUSE_COUNT, PersistentDataType.INTEGER);

        if (day == null || day != today || count == null) {
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

    private String readTeamFromModernPdc(ItemMeta meta) {
        try {
            return meta.getPersistentDataContainer().get(MarbleKeys.TEAM_KEY, PersistentDataType.STRING);
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
