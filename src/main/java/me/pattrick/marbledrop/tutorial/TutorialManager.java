package me.pattrick.marbledrop.tutorial;

import me.pattrick.marbledrop.progression.DustManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
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
    private final TutorialLocationStore locationStore;

    private final NamespacedKey K_ACTIVE;
    private final NamespacedKey K_STEP;
    private final NamespacedKey K_DONE;

    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    // Baselines snapshotted the moment a player enters INFUSION/UPGRADE,
    // so the completion poller only fires on a genuine NEW result for
    // THIS player's tutorial run, not on marbles/stats they already had.
    private final Map<UUID, Integer> infusionBaselineCount = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> upgradeBaselineTotal = new ConcurrentHashMap<>();

    public TutorialManager(Plugin plugin, DustManager dustManager, TutorialLocationStore locationStore) {
        this.plugin = plugin;
        this.dustManager = dustManager;
        this.locationStore = locationStore;
        this.K_ACTIVE = new NamespacedKey(plugin, "tutorial_active");
        this.K_STEP = new NamespacedKey(plugin, "tutorial_step");
        this.K_DONE = new NamespacedKey(plugin, "tutorial_done");
    }

    public TutorialLocationStore locations() {
        return locationStore;
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
     * being completed before doing anything -- this is what makes it
     * safe against duplicate/late/out-of-order events, and against two
     * different players' completions ever being able to cross-affect
     * each other (everything here is keyed off the individual player).
     */
    public void completeStep(Player player, TutorialStep justCompleted) {
        if (!isActive(player)) return;
        if (getStep(player) != justCompleted) return; // already moved on, or event fired late/twice -- ignore

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
            // couple seconds of each other -- too much text at once.
            case RECYCLER -> 100; // ~5s
            case TASKS -> 40;     // ~2s
            default -> 30;
        };
    }

    private void finish(Player player) {
        setActive(player, false);
        player.getPersistentDataContainer().set(K_DONE, PersistentDataType.BYTE, (byte) 1);

        infusionBaselineCount.remove(player.getUniqueId());
        upgradeBaselineTotal.remove(player.getUniqueId());
        removeBossBar(player);

        dustManager.addDust(player, TutorialStep.COMPLETE.rewardDust());

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

    public void reset(Player player) {
        removeBossBar(player);
        infusionBaselineCount.remove(player.getUniqueId());
        upgradeBaselineTotal.remove(player.getUniqueId());
        player.getPersistentDataContainer().remove(K_DONE);
        setActive(player, true);
        setStep(player, TutorialStep.TASKS);
        enterStep(player, TutorialStep.TASKS);
    }

    public void skip(Player player) {
        removeBossBar(player);
        infusionBaselineCount.remove(player.getUniqueId());
        upgradeBaselineTotal.remove(player.getUniqueId());
        setActive(player, false);
        player.getPersistentDataContainer().set(K_DONE, PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(ChatColor.GRAY + "Tutorial skipped by an admin.");
    }

    // ---------------- Presentation ----------------

    /** Enters a step: teleport (if a checkpoint is set), boss bar, chat instructions, baseline snapshot. */
    private void enterStep(Player player, TutorialStep step) {
        int stepNumber = step.ordinal() + 1;
        int total = TutorialStep.totalSteps();

        // Safety net: never leave a station GUI open across a room change.
        // The upgrade GUI is normally closed the moment a real upgrade is
        // detected (see TutorialUpgradeHook), but this covers any other
        // GUI that might still be open when a step transition teleports
        // the player away.
        player.closeInventory();

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

        if (step == TutorialStep.INFUSION) {
            infusionBaselineCount.put(player.getUniqueId(), TutorialMarbleUtil.countMarbles(player));
        } else if (step == TutorialStep.UPGRADE) {
            upgradeBaselineTotal.put(player.getUniqueId(), TutorialMarbleUtil.highestStatTotal(player));
        }
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
        // NOTE: baselines are intentionally left in memory keyed by UUID --
        // they're harmless to keep and get overwritten if the player
        // re-enters that step later (e.g. after a relog mid-step).
    }
}
