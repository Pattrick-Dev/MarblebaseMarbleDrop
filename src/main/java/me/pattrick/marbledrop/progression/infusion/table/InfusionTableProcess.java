package me.pattrick.marbledrop.progression.infusion.table;

import me.pattrick.marbledrop.progression.infusion.heads.SkullUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InfusionTableProcess {

    private InfusionTableProcess() {}

    // Lock map: tableKey -> owner UUID
    private static final Map<String, UUID> LOCKS = new ConcurrentHashMap<>();

    /**
     * Cache heads grouped by team so we can build "one per team" decoy lists.
     * teamKey -> list of heads
     */
    private static volatile Map<String, List<ItemStack>> HEADS_BY_TEAM_CACHE = null;

    public static boolean isLocked(Block cauldron) {
        return LOCKS.containsKey(keyOf(cauldron));
    }

    /**
     * Attempt to lock this cauldron for a player. Returns true if lock acquired.
     */
    public static boolean tryLock(Block cauldron, Player owner) {
        String key = keyOf(cauldron);
        return LOCKS.putIfAbsent(key, owner.getUniqueId()) == null;
    }

    /**
     * Unlock this cauldron if currently locked by this player.
     */
    public static void unlock(Block cauldron, Player owner) {
        String key = keyOf(cauldron);
        UUID current = LOCKS.get(key);
        if (current != null && current.equals(owner.getUniqueId())) {
            LOCKS.remove(key);
        }
    }

    /**
     * Force unlock (used internally for safety).
     */
    private static void forceUnlock(Block cauldron) {
        LOCKS.remove(keyOf(cauldron));
    }

    private static String keyOf(Block b) {
        Location l = b.getLocation();
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    /**
     * YAML ONLY format:
     *
     * heads:
     *   1:
     *     base64: "..."
     *     name: "§a§lSomething"   (optional)
     *     team: "Limers"
     *
     * - team is used for grouping (lowercased)
     * - base64 is applied via SkullUtil.applyBase64
     *
     * This method is intentionally LOUD if something is wrong.
     */
    private static Map<String, List<ItemStack>> getHeadsByTeam(JavaPlugin plugin) {
        Map<String, List<ItemStack>> cached = HEADS_BY_TEAM_CACHE;
        if (cached != null) return cached;

        synchronized (InfusionTableProcess.class) {
            if (HEADS_BY_TEAM_CACHE != null) return HEADS_BY_TEAM_CACHE;

            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                // noinspection ResultOfMethodCallIgnored
                dataFolder.mkdirs();
            }

            File headsFile = new File(dataFolder, "heads.yml");
            if (!headsFile.exists()) {
                plugin.getLogger().warning("[Infusion] heads.yml missing at: " + headsFile.getAbsolutePath());
                plugin.getLogger().warning("[Infusion] Result: no decoy textures will load; animation will show only the result marble.");
                HEADS_BY_TEAM_CACHE = Collections.emptyMap();
                return HEADS_BY_TEAM_CACHE;
            }

            Map<String, List<ItemStack>> byTeam = new HashMap<>();

            int totalEntries = 0;
            int loadedHeads = 0;
            int skippedMissingTeam = 0;
            int skippedMissingBase64 = 0;
            int skippedApplyFailed = 0;

            try {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(headsFile);

                ConfigurationSection headsSec = cfg.getConfigurationSection("heads");
                if (headsSec == null) {
                    plugin.getLogger().warning("[Infusion] heads.yml loaded but missing top-level 'heads:' section: " + headsFile.getAbsolutePath());
                    plugin.getLogger().warning("[Infusion] Result: no decoy textures will load; animation will show only the result marble.");
                    HEADS_BY_TEAM_CACHE = Collections.emptyMap();
                    return HEADS_BY_TEAM_CACHE;
                }

                for (String id : headsSec.getKeys(false)) {
                    ConfigurationSection entry = headsSec.getConfigurationSection(id);
                    if (entry == null) continue;

                    totalEntries++;

                    String base64 = entry.getString("base64");
                    String team = entry.getString("team");
                    String name = entry.getString("name", "");

                    if (team == null || team.trim().isEmpty()) {
                        skippedMissingTeam++;
                        plugin.getLogger().warning("[Infusion] heads.yml entry '" + id + "' missing 'team' (skipping).");
                        continue;
                    }

                    if (base64 == null || base64.trim().isEmpty()) {
                        skippedMissingBase64++;
                        plugin.getLogger().warning("[Infusion] heads.yml entry '" + id + "' missing 'base64' (skipping).");
                        continue;
                    }

                    String teamKey = team.trim().toLowerCase(Locale.ROOT);

                    ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
                    SkullMeta meta = (SkullMeta) head.getItemMeta();
                    if (meta == null) {
                        plugin.getLogger().warning("[Infusion] Unable to get SkullMeta for entry '" + id + "' (skipping).");
                        continue;
                    }

                    try {
                        SkullUtil.applyBase64(meta, base64);
                    } catch (Exception ex) {
                        skippedApplyFailed++;
                        plugin.getLogger().warning("[Infusion] SkullUtil.applyBase64 failed for entry '" + id + "' (team=" + team + "): " + ex.getMessage());
                        continue;
                    }

                    if (name != null && !name.trim().isEmpty()) {
                        meta.setDisplayName(name);
                    }

                    head.setItemMeta(meta);
                    byTeam.computeIfAbsent(teamKey, k -> new ArrayList<>()).add(head);
                    loadedHeads++;
                }

            } catch (Exception e) {
                plugin.getLogger().severe("[Infusion] Failed to read/parse heads.yml: " + headsFile.getAbsolutePath());
                plugin.getLogger().severe("[Infusion] Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                HEADS_BY_TEAM_CACHE = Collections.emptyMap();
                return HEADS_BY_TEAM_CACHE;
            }

            // Summary log (always helpful, not spammy)
            plugin.getLogger().info("[Infusion] heads.yml parsed. entries=" + totalEntries
                    + ", loadedHeads=" + loadedHeads
                    + ", teams=" + byTeam.size()
                    + ", skippedMissingTeam=" + skippedMissingTeam
                    + ", skippedMissingBase64=" + skippedMissingBase64
                    + ", skippedApplyFailed=" + skippedApplyFailed);

            if (byTeam.isEmpty()) {
                plugin.getLogger().warning("[Infusion] heads.yml produced 0 valid heads. Animation will not show decoy texture swaps.");
                plugin.getLogger().warning("[Infusion] Check that 'heads:' exists and each entry has 'team' + 'base64'. File: " + headsFile.getAbsolutePath());
            }

            HEADS_BY_TEAM_CACHE = byTeam.isEmpty() ? Collections.emptyMap() : byTeam;
            return HEADS_BY_TEAM_CACHE;
        }
    }

    /**
     * Build a decoy list that contains at most ONE head per team, shuffled.
     * This guarantees no team repeats during the animation.
     */
    private static List<ItemStack> buildUniqueTeamDecoys(JavaPlugin plugin) {
        Map<String, List<ItemStack>> byTeam = getHeadsByTeam(plugin);
        if (byTeam.isEmpty()) return Collections.emptyList();

        List<ItemStack> decoys = new ArrayList<>();
        Random rng = new Random();

        for (Map.Entry<String, List<ItemStack>> e : byTeam.entrySet()) {
            List<ItemStack> teamHeads = e.getValue();
            if (teamHeads == null || teamHeads.isEmpty()) continue;
            ItemStack pick = teamHeads.get(rng.nextInt(teamHeads.size()));
            decoys.add(pick.clone());
        }

        if (decoys.isEmpty()) return Collections.emptyList();

        Collections.shuffle(decoys);
        return decoys;
    }

    public static void run(Player player, Block cauldron, ItemStack marble) {
        // Guard in case this is called directly
        if (!isLocked(cauldron)) {
            tryLock(cauldron, player);
        }

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(InfusionTableProcess.class);

        World w = cauldron.getWorld();
        Location base = cauldron.getLocation().add(0.5, 1.0, 0.5);
        Location cauldronTop = cauldron.getLocation().add(0.5, 0.95, 0.5);
        Location standBase = cauldron.getLocation().add(0.5, 1.25, 0.5);

        final ItemStack resultMarble = marble.clone();
        final List<ItemStack> decoys = buildUniqueTeamDecoys(plugin);

        if (decoys.size() <= 1) {
            plugin.getLogger().warning("[Infusion] Decoy list size=" + decoys.size()
                    + " (animation swaps will be invisible). Ensure heads.yml has multiple teams with valid base64.");
        }

        ArmorStand stand = w.spawn(standBase, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            as.setSmall(true);
            as.setInvulnerable(true);
            as.setSilent(true);

            if (!decoys.isEmpty()) {
                as.getEquipment().setHelmet(decoys.get(0).clone());
            } else {
                as.getEquipment().setHelmet(resultMarble.clone());
            }
        });

        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 1f);

        new BukkitRunnable() {
            // --- timing knobs ---
            final int TOTAL_TICKS = 120;        // 6s spin
            final int HOLD_TICKS = 20;          // 1s hold
            final int REVEAL_EARLY_TICKS = 10;  // reveal winner 0.5s before end

            // bob tuning
            final double BOB_AMPLITUDE = 0.10;
            final double BOB_SPEED = 0.22;

            // spin ends facing forward
            final double STARTING_SPIN_ANGLE = Math.PI * 14.0; // 7 turns
            // --------------------

            int elapsed = 0;

            int decoyIndex = 0;
            int swapCooldown = 0;

            boolean holding = false;
            int holdLeft = HOLD_TICKS;

            boolean revealedResult = false;

            // Announce once, and ONLY after the award
            boolean announced = false;

            private void spawnWhitePoof(Location loc) {
                w.spawnParticle(Particle.POOF, loc, 28, 0.26, 0.20, 0.26, 0.03);
                w.spawnParticle(Particle.SMOKE, loc, 18, 0.24, 0.18, 0.24, 0.02);
            }

            private void spawnRevealBurst(Location loc) {
                w.spawnParticle(Particle.FIREWORK, loc, 80, 0.42, 0.30, 0.42, 0.20);
                w.spawnParticle(Particle.END_ROD, loc, 22, 0.30, 0.22, 0.30, 0.08);
                w.spawnParticle(Particle.CRIT, loc, 28, 0.30, 0.22, 0.30, 0.18);
                w.spawnParticle(Particle.ENCHANT, loc, 36, 0.30, 0.30, 0.30, 0.02);
                w.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.WHITE);

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 2.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.55f, 1.6f);
            }

            private int rarityRank(String rarity) {
                if (rarity == null) return 0;
                String r = rarity.trim().toUpperCase(Locale.ROOT);
                return switch (r) {
                    case "UNCOMMON" -> 1;
                    case "RARE" -> 2;
                    case "EPIC" -> 3;
                    case "LEGENDARY" -> 4;
                    default -> 0; // COMMON/unknown
                };
            }

            private NamedTextColor rarityColor(String rarity) {
                if (rarity == null) return NamedTextColor.WHITE;
                String r = rarity.trim().toUpperCase(Locale.ROOT);
                return switch (r) {
                    case "UNCOMMON" -> NamedTextColor.GREEN;
                    case "RARE" -> NamedTextColor.AQUA;
                    case "EPIC" -> NamedTextColor.LIGHT_PURPLE;
                    case "LEGENDARY" -> NamedTextColor.GOLD;
                    default -> NamedTextColor.WHITE;
                };
            }

            private String findLoreValue(List<String> lore, String key) {
                if (lore == null) return null;
                for (String line : lore) {
                    if (line == null) continue;
                    String stripped = ChatColor.stripColor(line);
                    if (stripped == null) continue;

                    if (stripped.toLowerCase(Locale.ROOT).startsWith(key.toLowerCase(Locale.ROOT) + ":")) {
                        return stripped.substring((key + ":").length()).trim();
                    }
                }
                return null;
            }

            private Component buildHoverFromItem(ItemStack item) {
                if (item == null || !item.hasItemMeta()) return Component.empty();

                ItemMeta meta = item.getItemMeta();
                List<String> lore = meta != null ? meta.getLore() : null;

                String team = findLoreValue(lore, "Team");
                if (team == null || team.isEmpty()) team = "Unknown";

                String rarity = findLoreValue(lore, "Rarity");
                if (rarity == null || rarity.isEmpty()) rarity = "COMMON";

                String speed = findLoreValue(lore, "Speed");
                String control = findLoreValue(lore, "Control");
                String momentum = findLoreValue(lore, "Momentum");
                String stability = findLoreValue(lore, "Stability");
                String luck = findLoreValue(lore, "Luck");

                return Component.text("")
                        .append(Component.text("Team: ", NamedTextColor.GRAY))
                        .append(Component.text(team, NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text("Rarity: ", NamedTextColor.GRAY))
                        .append(Component.text(rarity.toUpperCase(Locale.ROOT), rarityColor(rarity)))
                        .append(Component.newline())
                        .append(Component.newline())
                        .append(Component.text("Speed: ", NamedTextColor.GRAY))
                        .append(Component.text(speed != null ? speed : "?", NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text("Control: ", NamedTextColor.GRAY))
                        .append(Component.text(control != null ? control : "?", NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text("Momentum: ", NamedTextColor.GRAY))
                        .append(Component.text(momentum != null ? momentum : "?", NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text("Stability: ", NamedTextColor.GRAY))
                        .append(Component.text(stability != null ? stability : "?", NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text("Luck: ", NamedTextColor.GRAY))
                        .append(Component.text(luck != null ? luck : "?", NamedTextColor.WHITE));
            }

            private void broadcastIfNeededAfterAward(ItemStack itemJustGiven) {
                if (announced) return;
                announced = true;

                if (itemJustGiven == null || !itemJustGiven.hasItemMeta()) return;

                ItemMeta meta = itemJustGiven.getItemMeta();
                List<String> lore = meta != null ? meta.getLore() : null;

                String rarity = findLoreValue(lore, "Rarity");
                if (rarity == null || rarity.isEmpty()) rarity = "COMMON";

                if (rarityRank(rarity) < rarityRank("EPIC")) return;

                Component hover = buildHoverFromItem(itemJustGiven);

                Component rarityComp =
                        Component.text(rarity.toUpperCase(Locale.ROOT), rarityColor(rarity))
                                .hoverEvent(HoverEvent.showText(hover));

                Component msg =
                        Component.text(player.getName(), NamedTextColor.YELLOW)
                                .append(Component.text(" found a ", NamedTextColor.GRAY))
                                .append(rarityComp)
                                .append(Component.text(" marble!", NamedTextColor.GRAY));

                Bukkit.broadcast(msg);
            }

            private void finish(boolean giveItem) {
                try {
                    if (stand != null && stand.isValid()) {
                        Location popLoc = stand.getLocation().clone().add(0, 0.7, 0);

                        spawnWhitePoof(popLoc);

                        w.spawnParticle(Particle.FIREWORK, popLoc, 22, 0.24, 0.18, 0.24, 0.10);
                        w.spawnParticle(Particle.END_ROD, popLoc, 10, 0.20, 0.16, 0.20, 0.05);
                        w.spawnParticle(Particle.CRIT, popLoc, 18, 0.25, 0.20, 0.25, 0.15);
                        w.spawnParticle(Particle.ENCHANT, popLoc, 24, 0.25, 0.25, 0.25, 0.02);

                        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 1.3f);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.8f);

                        stand.remove();
                    }

                    if (giveItem) {
                        final ItemStack award = resultMarble.clone();
                        final boolean dropped = (player.getInventory().firstEmpty() == -1);

                        if (dropped) {
                            w.dropItemNaturally(player.getLocation(), award);
                        } else {
                            player.getInventory().addItem(award);
                        }

                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (dropped) {
                                broadcastIfNeededAfterAward(award);
                                return;
                            }

                            for (ItemStack it : player.getInventory().getContents()) {
                                if (it != null && it.isSimilar(award)) {
                                    broadcastIfNeededAfterAward(award);
                                    return;
                                }
                            }

                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                for (ItemStack it2 : player.getInventory().getContents()) {
                                    if (it2 != null && it2.isSimilar(award)) {
                                        broadcastIfNeededAfterAward(award);
                                        return;
                                    }
                                }
                            }, 2L);

                        }, 2L);
                    }

                } finally {
                    forceUnlock(cauldron);
                    cancel();
                }
            }

            private int swapDelayFor(int t) {
                double p = Math.min(1.0, Math.max(0.0, t / (double) TOTAL_TICKS));
                double eased = 1.0 - Math.pow(1.0 - p, 3);

                int delay = 1 + (int) Math.round(eased * 7.0); // 1..8
                if (t > TOTAL_TICKS - 20) delay += 2;          // up to ~10
                return Math.max(1, delay);
            }

            private void spawnCauldronActiveParticles(int t) {
                if (t % 2 == 0) {
                    w.spawnParticle(Particle.BUBBLE_POP, cauldronTop, 6, 0.20, 0.02, 0.20, 0.01);
                    w.spawnParticle(Particle.SPLASH, cauldronTop, 2, 0.18, 0.01, 0.18, 0.02);
                }

                if (t % 4 == 0) {
                    w.spawnParticle(Particle.SMOKE, cauldronTop.clone().add(0, 0.05, 0),
                            2, 0.12, 0.05, 0.12, 0.01);
                }

                if (t % 6 == 0) {
                    w.spawnParticle(Particle.ENCHANT, base, 10, 0.22, 0.12, 0.22, 0.02);
                }

                if (t % 8 == 0) {
                    w.spawnParticle(Particle.PORTAL, base, 6, 0.18, 0.18, 0.18, 0.02);
                }
            }

            private void beginHold() {
                holding = true;
                holdLeft = HOLD_TICKS;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 2.0f);
            }

            private double computedHeadAngle(int t) {
                double p = Math.min(1.0, Math.max(0.0, t / (double) TOTAL_TICKS));
                double eased = 1.0 - Math.pow(1.0 - p, 3);
                return STARTING_SPIN_ANGLE * (1.0 - eased);
            }

            private void revealResultNow() {
                if (revealedResult) return;
                revealedResult = true;

                if (stand != null && stand.isValid()) {
                    stand.getEquipment().setHelmet(resultMarble.clone());
                    Location burstLoc = stand.getLocation().clone().add(0, 0.7, 0);
                    spawnRevealBurst(burstLoc);
                }

                swapCooldown = Integer.MAX_VALUE;
            }

            @Override
            public void run() {
                if (stand == null || !stand.isValid()) {
                    finish(false);
                    return;
                }

                spawnCauldronActiveParticles(elapsed);

                if (holding) {
                    double holdBob = 0.02 * Math.sin((HOLD_TICKS - holdLeft) * 0.25);

                    stand.setHeadPose(new EulerAngle(0.0, 0.0, 0.0));

                    Location loc = standBase.clone().add(0, holdBob, 0);
                    stand.teleport(loc);

                    if (holdLeft % 5 == 0) {
                        w.spawnParticle(Particle.ENCHANT, base, 10, 0.20, 0.20, 0.20, 0.02);
                    }

                    holdLeft--;
                    if (holdLeft <= 0) {
                        finish(true);
                    }
                    return;
                }

                if (!revealedResult && elapsed >= (TOTAL_TICKS - REVEAL_EARLY_TICKS)) {
                    revealResultNow();
                }

                if (!revealedResult && !decoys.isEmpty()) {
                    int swapDelay = swapDelayFor(elapsed);

                    if (swapCooldown <= 0) {
                        decoyIndex = (decoyIndex + 1) % decoys.size();
                        stand.getEquipment().setHelmet(decoys.get(decoyIndex).clone());
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.30f, 1.85f);
                        swapCooldown = swapDelay;
                    } else {
                        swapCooldown--;
                    }
                }

                double headAngle = computedHeadAngle(elapsed);
                stand.setHeadPose(new EulerAngle(0.0, headAngle, 0.0));

                double bob = BOB_AMPLITUDE * Math.sin(elapsed * BOB_SPEED);
                Location animLoc = standBase.clone().add(0, bob, 0);
                stand.teleport(animLoc);

                if (elapsed % 3 == 0) {
                    w.spawnParticle(Particle.ENCHANT, base, 8, 0.25, 0.25, 0.25, 0.02);
                    w.spawnParticle(Particle.PORTAL, base, 6, 0.2, 0.2, 0.2, 0.02);
                }

                if (elapsed % 20 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.6f);
                }

                elapsed++;

                if (elapsed >= TOTAL_TICKS) {
                    stand.setHeadPose(new EulerAngle(0.0, 0.0, 0.0));

                    if (!revealedResult) {
                        revealResultNow();
                    }

                    beginHold();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
