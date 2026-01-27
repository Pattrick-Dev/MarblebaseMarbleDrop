package me.pattrick.marbledrop.progression.infusion;

import me.pattrick.marbledrop.HeadDatabase;
import me.pattrick.marbledrop.Marble;
import me.pattrick.marbledrop.MarbleItem;
import me.pattrick.marbledrop.MarbleRarity;
import me.pattrick.marbledrop.MarbleStats;
import me.pattrick.marbledrop.progression.DustManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private final RarityRoller rarityRoller;

    private final NamespacedKey K_ATTUNEMENT;

    // Optional compatibility: legacy flag some older checks may still use
    private final NamespacedKey K_LEGACY_MARBLE;

    // Reads the team from the item's PDC (source of truth)
    private final NamespacedKey K_TEAM;

    // per-player daily infusion cap tracking
    private final NamespacedKey K_INFUSE_DAY;   // int: YYYYMMDD
    private final NamespacedKey K_INFUSE_COUNT; // int: used today

    // reads rarity from marble PDC (for catalyst marbles)
    private final NamespacedKey K_RARITY;

    // Permission that bypasses daily infusion cap (and also avoids incrementing the counter)
    private static final String PERM_BYPASS_INFUSION_LIMIT = "marbledrop.infusion.bypass";

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

        this.K_RARITY = new NamespacedKey(plugin, "rarity");
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
     * NEW: Produce the infused marble ItemStack with optional marble catalyst rarity bias.
     * catalystRarity comes from an "old marble" inserted as a catalyst.
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

        // NEW: daily cap (0 = unlimited) + admin bypass
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
        meta.setLore(List.of(
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

        // ---- NEW: broadcast rare discoveries with hover tooltip ----
//        broadcastMarbleDiscovery(player, team, rarity, stats);
        // -----------------------------------------------------------

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
     * Hoverable broadcast (RARE+ by default):
     * ✨ <PLAYER> discovered a <RARITY> Marble!
     * Hovering over <RARITY> shows team + stats.
     */
    private void broadcastMarbleDiscovery(Player player, String team, MarbleRarity rarity, MarbleStats stats) {
        // Broadcast threshold: RARE+
        if (rarity.ordinal() < MarbleRarity.RARE.ordinal()) return;

        NamedTextColor rarityColor = rarityNamedColor(rarity);

        Component hover = Component.text("")
                .append(Component.text("Team: ", NamedTextColor.GRAY))
                .append(Component.text(team, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Rarity: ", NamedTextColor.GRAY))
                .append(Component.text(rarity.name(), rarityColor))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Speed: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(stats.speed()), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Control: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(stats.control()), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Momentum: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(stats.momentum()), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Stability: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(stats.stability()), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Luck: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(stats.luck()), NamedTextColor.WHITE));

        Component rarityWord = Component.text(rarity.name(), rarityColor)
                .hoverEvent(HoverEvent.showText(hover));

        Component msg = Component.text("✨ ", NamedTextColor.GOLD)
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" discovered a ", NamedTextColor.GRAY))
                .append(rarityWord)
                .append(Component.text(" Marble! [BMD]", NamedTextColor.GRAY));

        Bukkit.broadcast(msg);
    }

    private NamedTextColor rarityNamedColor(MarbleRarity r) {
        return switch (r) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.AQUA;
            case EPIC -> NamedTextColor.LIGHT_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
        };
    }

    /**
     * Reads rarity from a catalyst marble item.
     * Returns null if item is not a marble catalyst.
     */
    public MarbleRarity readMarbleCatalystRarity(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // must be a marble (legacy flag)
        Byte isMarble = pdc.get(K_LEGACY_MARBLE, PersistentDataType.BYTE);
        if (isMarble == null || isMarble != (byte) 1) {
            return null;
        }

        // preferred: PDC rarity
        String rarityStr = pdc.get(K_RARITY, PersistentDataType.STRING);
        if (rarityStr != null && !rarityStr.isEmpty()) {
            MarbleRarity parsed = parseRaritySafe(rarityStr);
            if (parsed != null) return parsed;
        }

        // fallback: lore parsing ("Rarity: <RARITY>")
        List<String> lore = meta.getLore();
        if (lore != null) {
            for (String line : lore) {
                if (line == null) continue;
                String stripped = ChatColor.stripColor(line);
                if (stripped == null) continue;

                if (stripped.toLowerCase(Locale.ROOT).startsWith("rarity:")) {
                    String after = stripped.substring("rarity:".length()).trim();
                    MarbleRarity parsed = parseRaritySafe(after);
                    if (parsed != null) return parsed;
                }
            }
        }

        // legacy unknown rarity -> treat as COMMON
        return MarbleRarity.COMMON;
    }

    /**
     * Catalyst bias while keeping your project rarity enum as the public type.
     *
     * IMPORTANT: RarityRoller currently returns me.pattrick.marbledrop.marble.MarbleRarity,
     * so we convert it into me.pattrick.marbledrop.MarbleRarity by name().
     */
    private MarbleRarity rollWithCatalystBias(int effectiveValue, MarbleRarity catalystRarity) {
        MarbleRarity best = toProjectRarity(RarityRoller.roll(effectiveValue));

        if (catalystRarity == null) {
            return best;
        }

        int extraRolls;
        double floorChance;

        // Match the updated "safer" catalyst tuning from RarityRoller
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
            MarbleRarity rolled = toProjectRarity(RarityRoller.roll(effectiveValue));
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

    /**
     * Converts the roller's enum type (me.pattrick.marbledrop.marble.MarbleRarity)
     * into your project enum type (me.pattrick.marbledrop.MarbleRarity).
     */
    private MarbleRarity toProjectRarity(me.pattrick.marbledrop.marble.MarbleRarity rollerRarity) {
        if (rollerRarity == null) return MarbleRarity.COMMON;
        try {
            return MarbleRarity.valueOf(rollerRarity.name());
        } catch (IllegalArgumentException ex) {
            return MarbleRarity.COMMON;
        }
    }

    private MarbleRarity parseRaritySafe(String raw) {
        if (raw == null) return null;

        String cleaned = ChatColor.stripColor(raw);
        if (cleaned == null) cleaned = raw;

        cleaned = cleaned.trim()
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);

        try {
            return MarbleRarity.valueOf(cleaned);
        } catch (IllegalArgumentException ex) {
            return null;
        }
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
