package me.pattrick.marbledrop.tutorial;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Auto-starts the tutorial on a player's first join, restores state on
 * relog, and gates /md subcommands so only the current step's command
 * can be used.
 * <p>
 * TASKS and RACE are fully custom mini-experiences (a spawned sheep to
 * kill, an isolated AI practice race) rather than the real tasks/race
 * systems, so when the gate sees those two commands used at the right
 * step it cancels the real command and hands off to the matching
 * tutorial handler instead. INFUSION/UPGRADE/RECYCLER let the real
 * command through unmodified -- those steps are completed by observing
 * the real systems (see TutorialProgressPoller / TutorialRecyclerHook),
 * not by intercepting them.
 */
public final class TutorialListener implements Listener {

    private static final java.util.Set<String> ALWAYS_ALLOWED = java.util.Set.of(
            "help", "reload", "debug", "pdc", "tutorial"
    );

    private final TutorialManager tutorialManager;
    private final TutorialTasksHandler tasksHandler;
    private final TutorialRaceService raceService;

    public TutorialListener(TutorialManager tutorialManager, TutorialTasksHandler tasksHandler,
                             TutorialRaceService raceService) {
        this.tutorialManager = tutorialManager;
        this.tasksHandler = tasksHandler;
        this.raceService = raceService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (tutorialManager.hasCompleted(player)) return;

        if (tutorialManager.isActive(player)) {
            tutorialManager.resume(player);
        } else {
            tutorialManager.start(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tutorialManager.handleQuit(event.getPlayer());
        tasksHandler.handleQuit(event.getPlayer());
        raceService.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!tutorialManager.isActive(player)) return;

        String message = event.getMessage().trim();
        String[] parts = message.substring(1).split("\\s+"); // drop leading '/'
        if (parts.length == 0) return;

        String label = parts[0].toLowerCase();
        if (!label.equals("md") && !label.equals("marbledrop")) {
            return; // not our command, leave chat/other plugins alone
        }

        if (parts.length < 2) return; // bare /md opens help, fine

        String sub = parts[1].toLowerCase();
        if (ALWAYS_ALLOWED.contains(sub)) return;

        TutorialStep current = tutorialManager.getStep(player);

        if (!current.matchesCommand(sub)) {
            event.setCancelled(true);
            player.sendMessage(org.bukkit.ChatColor.RED + "Finish the current tutorial step first: " +
                    org.bukkit.ChatColor.YELLOW + current.title());
            player.sendMessage(org.bukkit.ChatColor.GRAY + current.hint());
            return;
        }

        // Matches the current step. TASKS and RACE are fully custom --
        // cancel the real command and run our own mini-experience instead.
        if (current == TutorialStep.TASKS) {
            event.setCancelled(true);
            tasksHandler.beginTask(player);
            return;
        }

        if (current == TutorialStep.RACE) {
            event.setCancelled(true);
            raceService.startRace(player);
            return;
        }

        // INFUSION / UPGRADE / RECYCLER: let the real command through untouched.
    }
}
