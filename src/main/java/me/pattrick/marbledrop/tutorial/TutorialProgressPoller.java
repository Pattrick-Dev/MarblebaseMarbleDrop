package me.pattrick.marbledrop.tutorial;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The INFUSION and UPGRADE steps use the real, unmodified game systems
 * (the actual Infusion Table, the actual Upgrade Station -- including
 * the infusion table's own ~6-second reveal animation), so there's no
 * single clean "completion event" to hook. Instead, this polls each
 * active tutorial player currently on one of those two steps and
 * compares against a baseline snapshotted the moment they entered the
 * step (see TutorialManager.enterStep), so it only fires on a genuine
 * NEW result for that specific player's tutorial run.
 */
public final class TutorialProgressPoller {

    private final Plugin plugin;
    private final TutorialManager tutorialManager;
    private BukkitTask task;

    public TutorialProgressPoller(Plugin plugin, TutorialManager tutorialManager) {
        this.plugin = plugin;
        this.tutorialManager = tutorialManager;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!tutorialManager.isActive(player)) continue;

            TutorialStep step = tutorialManager.getStep(player);

            if (step == TutorialStep.INFUSION) {
                int baseline = tutorialManager.infusionBaseline(player);
                int current = TutorialMarbleUtil.countMarbles(player);
                if (current > baseline) {
                    player.sendMessage(ChatColor.GREEN + "You got a marble!");
                    tutorialManager.completeStep(player, TutorialStep.INFUSION);
                }
            } else if (step == TutorialStep.UPGRADE) {
                int baseline = tutorialManager.upgradeBaseline(player);
                int current = TutorialMarbleUtil.highestStatTotal(player);
                if (baseline >= 0 && current > baseline) {
                    player.sendMessage(ChatColor.GREEN + "Upgrade applied!");
                    tutorialManager.completeStep(player, TutorialStep.UPGRADE);
                }
            }
        }
    }
}
