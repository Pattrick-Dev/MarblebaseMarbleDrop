package me.pattrick.marbledrop.tutorial;

import me.pattrick.marbledrop.MdConfig;
import me.pattrick.marbledrop.SilentGive;
import me.pattrick.marbledrop.progression.DustManager;
import me.pattrick.marbledrop.progression.StationType;
import me.pattrick.marbledrop.races.ScheduledRaceManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives the forced, linear onboarding tutorial.
 * <p>
 * Per-player progress (active/step/done) is persisted on the player's
 * PersistentDataContainer so it survives relogs. Per-player *transient*
 * bookkeeping used only while mid-step (boss bars, baseline snapshots
 * for the infusion/upgrade pollers) lives in-memory, keyed by UUID, so
 * concurrent players each get an independent instance of every step --
 * nothing here is shared/global across players.
 */
public final class TutorialManager {

    private final Plugin plugin;
    private final DustManager dustManager;
    private final MdConfig config;
    private final TutorialLocationStore locationStore;
    private final TutorialCraftFrameManager craftFrames;
    private final TutorialTabListPrivacy tabPrivacy;
    private final ScheduledRaceManager scheduledRaceManager;

    private final NamespacedKey K_ACTIVE;
    private final NamespacedKey K_STEP;
    private final NamespacedKey K_DONE;

    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    // Baselines snapshotted the moment a player enters INFUSION/UPGRADE,
    // so the completion poller only fires on a genuine NEW result for
    // THIS player's tutorial run, not on marbles/stats they already had.
    private final Map<UUID, Integer> infusionBaselineCount = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> upgradeBaselineTotal = new ConcurrentHashMap<>();

    // CRAFT step bookkeeping: which of the three recipes a player has
    // crafted so far this run (its size doubles as "which recipe is next"
    // into CRAFT_ORDER), and the highest stage index ingredients have
    // already been given for, so relogging mid-stage doesn't hand out a
    // second batch of the same recipe's ingredients.
    private final Map<UUID, Set<StationType>> craftedInStep = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> craftIngredientsGivenStage = new ConcurrentHashMap<>();

    private static final StationType[] CRAFT_ORDER = {
            StationType.INFUSION_TABLE, StationType.UPGRADE_STATION, StationType.RECYCLER
    };

    public TutorialManager(Plugin plugin, DustManager dustManager, MdConfig config, TutorialLocationStore locationStore,
                            TutorialCraftFrameManager craftFrames, TutorialTabListPrivacy tabPrivacy,
                            ScheduledRaceManager scheduledRaceManager) {
        this.plugin = plugin;
        this.dustManager = dustManager;
        this.config = config;
        this.locationStore = locationStore;
        this.craftFrames = craftFrames;
        this.tabPrivacy = tabPrivacy;
        this.scheduledRaceManager = scheduledRaceManager;
        this.K_ACTIVE = new NamespacedKey(plugin, "tutorial_active");
        this.K_STEP = new NamespacedKey(plugin, "tutorial_step");
        this.K_DONE = new NamespacedKey(plugin, "tutorial_done");
    }

    public TutorialLocationStore locations() {
        return locationStore;
    }

    /** Nullable - see TutorialTabListPrivacy#createIfAvailable. Used by TutorialListener's own join-time visibility sync. */
    TutorialTabListPrivacy tabPrivacy() {
        return tabPrivacy;
    }

    // ---------------- State queries ----------------

    public boolean hasCompleted(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return pdc.getOrDefault(K_DONE, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    public boolean isActive(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return pdc.getOrDefault(K_ACTIVE, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    public TutorialStep getStep(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int ordinal = pdc.getOrDefault(K_STEP, PersistentDataType.INTEGER, 0);
        TutorialStep[] all = TutorialStep.values();
        if (ordinal < 0 || ordinal >= all.length) return all[0];
        return all[ordinal];
    }

    private void setStep(Player player, TutorialStep step) {
        player.getPersistentDataContainer().set(K_STEP, PersistentDataType.INTEGER, step.ordinal());
    }

    private void setActive(Player player, boolean active) {
        player.getPersistentDataContainer().set(K_ACTIVE, PersistentDataType.BYTE, (byte) (active ? 1 : 0));
    }

    int infusionBaseline(Player player) {
        return infusionBaselineCount.getOrDefault(player.getUniqueId(), 0);
    }

    int upgradeBaseline(Player player) {
        return upgradeBaselineTotal.getOrDefault(player.getUniqueId(), -1);
    }

    // ---------------- Lifecycle ----------------

    /** Begins the tutorial fresh. Call on a player's first join. */
    public void start(Player player) {
        setActive(player, true);
        setStep(player, TutorialStep.TASKS);
        TutorialVisibility.enter(plugin, player, tabPrivacy);
        enterStep(player, TutorialStep.TASKS);
    }

    /** Re-shows the boss bar/chat for whatever step the player was on. Call on relog if active. */
    public void resume(Player player) {
        TutorialStep step = getStep(player);
        if (step == TutorialStep.COMPLETE) {
            finish(player);
            return;
        }
        enterStep(player, step);
    }

    /**
     * The single entry point every real-gameplay completion hook calls
     * (sheep killed, marble received, marble upgraded, marble recycled,
     * race finished). Verifies the player is still actually on the step
     * being completed before doing anything - this is what makes it
     * safe against duplicate/late/out-of-order events, and against two
     * different players' completions ever being able to cross-affect
     * each other (everything here is keyed off the individual player).
     */
    public void completeStep(Player player, TutorialStep justCompleted) {
        if (!isActive(player)) return;
        if (getStep(player) != justCompleted) return; // already moved on, or event fired late/twice - ignore

        int reward = justCompleted.rewardDust();
        if (reward > 0) {
            dustManager.addDust(player, reward);
            player.sendMessage(ChatColor.GREEN + "+" + reward + " Dust");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

        TutorialStep next = justCompleted.next();

        // Immediately lock the step forward so a duplicate event firing
        // a tick later can't double-complete the same step (see guard
        // above) and so the player can't spam the same action for repeat
        // rewards while the delay below is running.
        setStep(player, next);

        int delayTicks = processingDelayTicks(justCompleted);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (next == TutorialStep.COMPLETE) {
                finish(player);
            } else {
                enterStep(player, next);
            }
        }, delayTicks);
    }

    private int processingDelayTicks(TutorialStep justCompleted) {
        return switch (justCompleted) {
            case INFUSION, UPGRADE -> 70; // ~3.5s to let them read/process what they got
            // Extra room: the real recycler's own confirmation message, then
            // the tutorial's own (separately delayed) marble-back message,
            // then this step's title/hint would otherwise all land within a
            // couple seconds of each other - too much text at once.
            case RECYCLER -> 100; // ~5s
            case TASKS -> 40;     // ~2s
            default -> 30;
        };
    }

    private void finish(Player player) {
        setActive(player, false);
        player.getPersistentDataContainer().set(K_DONE, PersistentDataType.BYTE, (byte) 1);

        // Undo the Creative->Adventure swap from enterStep() now that
        // they've got "full access" - Adventure was only ever a tutorial
        // safety rail, not where a finished player should be left standing.
        if (player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        infusionBaselineCount.remove(player.getUniqueId());
        upgradeBaselineTotal.remove(player.getUniqueId());
        craftedInStep.remove(player.getUniqueId());
        craftIngredientsGivenStage.remove(player.getUniqueId());
        craftFrames.clearForPlayer(player);
        removeBossBar(player);
        TutorialVisibility.exit(plugin, player, this, tabPrivacy);

        dustManager.addDust(player, TutorialStep.COMPLETE.rewardDust());

        // Scheduled races' entry-window boss bar is hidden from anyone who
        // hasn't finished the tutorial yet (see ScheduledRaceManager) - now
        // that they have, drop them straight into whichever window's
        // currently running instead of making them wait for the next one.
        scheduledRaceManager.onTutorialFinished(player);

        Location postLoc = locationStore.getPostTutorialLocation();
        if (postLoc != null) {
            player.teleport(postLoc);
        }

        player.sendMessage(ChatColor.GOLD + "Tutorial complete! " +
                ChatColor.GREEN + "+" + TutorialStep.COMPLETE.rewardDust() + " Dust" +
                ChatColor.GOLD + ". Full access unlocked. Good luck out there!");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // ---------------- Admin controls ----------------

    /** Wipes tutorial progress back to a fresh, unstarted state. Does NOT re-enter/auto-start it - the player runs /md tutorial start themselves when ready. */
    public void reset(Player player) {
        boolean wasActive = isActive(player);
        removeBossBar(player);
        infusionBaselineCount.remove(player.getUniqueId());
        upgradeBaselineTotal.remove(player.getUniqueId());
        craftedInStep.remove(player.getUniqueId());
        craftIngredientsGivenStage.remove(player.getUniqueId());
        craftFrames.clearForPlayer(player);
        player.getPersistentDataContainer().remove(K_DONE);
        setActive(player, false);
        setStep(player, TutorialStep.TASKS);
        if (wasActive) TutorialVisibility.exit(plugin, player, this, tabPrivacy);
    }

    public void skip(Player player) {
        boolean wasActive = isActive(player);
        removeBossBar(player);
        infusionBaselineCount.remove(player.getUniqueId());
        upgradeBaselineTotal.remove(player.getUniqueId());
        craftedInStep.remove(player.getUniqueId());
        craftIngredientsGivenStage.remove(player.getUniqueId());
        craftFrames.clearForPlayer(player);
        setActive(player, false);
        player.getPersistentDataContainer().set(K_DONE, PersistentDataType.BYTE, (byte) 1);
        if (wasActive) TutorialVisibility.exit(plugin, player, this, tabPrivacy);
        player.sendMessage(ChatColor.GRAY + "Tutorial skipped by an admin.");
    }

    // ---------------- Presentation ----------------

    /** Enters a step: teleport (if a checkpoint is set), boss bar, chat instructions, baseline snapshot. */
    private void enterStep(Player player, TutorialStep step) {
        int stepNumber = step.ordinal() + 1;
        int total = TutorialStep.totalSteps();

        // The tutorial hands out real ingredients/dust and expects normal
        // survival restrictions (can't just pull items from a creative
        // inventory) - if an admin/staff member starts it while still in
        // Creative (e.g. from building), drop them to Adventure so the
        // rest of the flow behaves the same as it would for a real player.
        if (player.getGameMode() == GameMode.CREATIVE) {
            player.setGameMode(GameMode.ADVENTURE);
        }

        // Safety net: never leave a station GUI open across a room change.
        // The upgrade GUI is normally closed the moment a real upgrade is
        // detected (see TutorialUpgradeHook), but this covers any other
        // GUI that might still be open when a step transition teleports
        // the player away.
        player.closeInventory();
        craftFrames.clearForPlayer(player);

        Location checkpoint = locationStore.get(step);
        if (checkpoint != null) {
            player.teleport(checkpoint);
        }

        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), id ->
                plugin.getServer().createBossBar(" ", BarColor.YELLOW, BarStyle.SOLID));

        bar.setTitle(ChatColor.YELLOW + "Tutorial (" + stepNumber + "/" + total + "): " +
                ChatColor.WHITE + step.title());
        bar.setProgress(Math.min(1.0, (double) (stepNumber - 1) / total));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        bar.setVisible(true);

        player.sendMessage(" ");
        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + step.title());
        player.sendMessage(ChatColor.GRAY + step.hint());

        if (step == TutorialStep.TASKS) {
            player.sendMessage(ChatColor.GRAY + "Tasks are your main source of Dust - the currency spent at " +
                    "Infusion Tables and Upgrade Stations. Check /tasks anytime for your daily and weekly list.");
        } else if (step == TutorialStep.CRAFT) {
            giveNextCraftIngredients(player);
            // Fresh=true is safe to force-open a GUI here (unlike after each
            // individual craft, see TutorialCraftHook): this only runs on
            // fresh entry into the step or a relog resume, both moments
            // with nothing else open.
            showCraftPreviewForCurrentTarget(player, true);
        } else if (step == TutorialStep.INFUSION) {
            infusionBaselineCount.put(player.getUniqueId(), TutorialMarbleUtil.countMarbles(player));
            int cap = config.infusionDailyCap();
            player.sendMessage(ChatColor.GRAY + (cap > 0
                    ? "You can perform up to " + cap + " infusions per day - the limit resets daily."
                    : "Infusions have no daily limit on this server."));
        } else if (step == TutorialStep.UPGRADE) {
            upgradeBaselineTotal.put(player.getUniqueId(), TutorialMarbleUtil.highestStatTotal(player));
        }
    }

    /** Which recipe the player should craft next, or null once all three are done. */
    public StationType currentCraftTarget(Player player) {
        int stage = craftedInStep.getOrDefault(player.getUniqueId(), Set.of()).size();
        return stage < CRAFT_ORDER.length ? CRAFT_ORDER[stage] : null;
    }

    /**
     * Shows the current recipe: the physical item-frame display if one is
     * configured (see TutorialCraftFrameManager), otherwise falls back to
     * TutorialCraftGui - a forced GUI popup on fresh entry, or a plain
     * chat message on later stages so an in-progress crafting table isn't
     * interrupted.
     */
    public void showCraftPreviewForCurrentTarget(Player player, boolean freshEntry) {
        StationType target = currentCraftTarget(player);
        if (target == null) return;

        if (craftFrames.isConfigured()) {
            craftFrames.displayForPlayer(player, target);
        } else if (freshEntry) {
            TutorialCraftGui.open(plugin, player, target);
        } else {
            TutorialCraftGui.sendChatPreview(plugin, player, target);
        }
    }

    /**
     * Hands over exactly what's needed for the CURRENT recipe only (see
     * StationRecipes) - one recipe at a time, not all three at once.
     * Guarded per-stage so a relog mid-recipe (which re-enters via
     * resume() -> enterStep()) doesn't hand out a second batch of the same
     * recipe's ingredients; TutorialCraftHook calls this again itself
     * after each craft to advance to the next recipe's ingredients.
     */
    public void giveNextCraftIngredients(Player player) {
        StationType target = currentCraftTarget(player);
        if (target == null) return; // all three already crafted

        UUID id = player.getUniqueId();
        int stage = craftedInStep.getOrDefault(id, Set.of()).size();
        Integer lastGivenStage = craftIngredientsGivenStage.get(id);
        if (lastGivenStage != null && lastGivenStage >= stage) return;

        craftIngredientsGivenStage.put(id, stage);
        player.sendMessage(ChatColor.GRAY + explanationFor(target));
        SilentGive.give(plugin, player, ingredientsFor(target));
    }

    /** What the station the player is about to learn actually does once placed. */
    private String explanationFor(StationType type) {
        return switch (type) {
            case INFUSION_TABLE -> "Infusion Table - spend Dust (plus an optional catalyst) here to create brand new marbles.";
            case UPGRADE_STATION -> "Upgrade Station - spend Dust here to redistribute one of your marble's stats.";
            case RECYCLER -> "Recycler - break a marble you don't want back down into Dust.";
        };
    }

    private ItemStack[] ingredientsFor(StationType type) {
        return switch (type) {
            case INFUSION_TABLE -> new ItemStack[]{
                    new ItemStack(Material.AMETHYST_SHARD, 4),
                    new ItemStack(Material.GLOWSTONE, 4),
                    new ItemStack(Material.CAULDRON, 1)
            };
            case UPGRADE_STATION -> new ItemStack[]{
                    new ItemStack(Material.IRON_INGOT, 4),
                    new ItemStack(Material.DIAMOND, 4),
                    new ItemStack(Material.SMITHING_TABLE, 1)
            };
            case RECYCLER -> new ItemStack[]{
                    new ItemStack(Material.IRON_INGOT, 4),
                    new ItemStack(Material.REDSTONE, 4),
                    new ItemStack(Material.GRINDSTONE, 1)
            };
        };
    }

    /**
     * Records that the player has crafted (and had taken back) one of the
     * three station recipes during the CRAFT step. Returns the set of
     * types crafted so far this run; the caller completes the step once
     * every StationType is present, otherwise calls giveNextCraftIngredients
     * again to hand over the next recipe.
     */
    public Set<StationType> recordCraft(Player player, StationType type) {
        Set<StationType> set = craftedInStep.computeIfAbsent(
                player.getUniqueId(), k -> EnumSet.noneOf(StationType.class));
        set.add(type);
        return set;
    }

    private void removeBossBar(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    public void handleQuit(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
        craftFrames.clearForPlayer(player);
        if (tabPrivacy != null) tabPrivacy.clear(player);
        // NOTE: baselines are intentionally left in memory keyed by UUID --
        // they're harmless to keep and get overwritten if the player
        // re-enters that step later (e.g. after a relog mid-step).
    }

    /**
     * Removes every currently-tracked boss bar from every viewer. Call on
     * plugin disable - boss bars aren't tied to the plugin's lifecycle, so
     * without this a reload orphans one for anyone mid-tutorial: still
     * visible to them, but no longer reachable by the fresh TutorialManager
     * the next onEnable() creates, which then hands out a brand new bar next
     * time their step re-renders. Repeated reloads stack these up.
     */
    public void shutdown() {
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }
        bossBars.clear();
    }
}
