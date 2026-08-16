package me.pattrick.marbledrop.tutorial;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Restores tutorial state on relog if a player is already mid-tutorial
 * (they start it themselves with /md tutorial start - see TutorialCommand),
 * and gates /md subcommands (plus the standalone /tasks command) so only
 * the current step's command can be used once they have.
 * <p>
 * TASKS and RACE are fully custom mini-experiences (a spawned sheep to
 * kill, an isolated AI practice race) rather than the real tasks/race
 * systems, so when the gate sees those two commands used at the right
 * step it cancels the real command and hands off to the matching
 * tutorial handler instead. INFUSION/UPGRADE/RECYCLER let the real
 * command through unmodified - those steps are completed by observing
 * the real systems (see TutorialProgressPoller / TutorialRecyclerHook),
 * not by intercepting them.
 */
public final class TutorialListener implements Listener {

    private static final java.util.Set<String> ALWAYS_ALLOWED = java.util.Set.of(
            "help", "reload", "debug", "pdc", "tutorial"
    );

    private final Plugin plugin;
    private final TutorialManager tutorialManager;
    private final TutorialTasksHandler tasksHandler;
    private final TutorialRaceService raceService;

    public TutorialListener(Plugin plugin, TutorialManager tutorialManager, TutorialTasksHandler tasksHandler,
                             TutorialRaceService raceService) {
        this.plugin = plugin;
        this.tutorialManager = tutorialManager;
        this.tasksHandler = tasksHandler;
        this.raceService = raceService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Independent of hasCompleted/isActive below - a completed or
        // never-started player still needs to be hidden from/to anyone
        // ELSE who's currently active, and a fresh connection doesn't
        // inherit any prior hidden/shown state either way.
        TutorialVisibility.syncOnJoin(plugin, player, tutorialManager, tutorialManager.tabPrivacy());

        if (tutorialManager.hasCompleted(player)) return;

        if (tutorialManager.isActive(player)) {
            tutorialManager.resume(player);
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

        String sub;
        if (label.equals("tasks")) {
            sub = "tasks"; // standalone top-level command, no "md" prefix
        } else if (label.equals("md") || label.equals("marbledrop")) {
            if (parts.length < 2) return; // bare /md opens help, fine
            sub = parts[1].toLowerCase();
        } else {
            return; // not our command, leave chat/other plugins alone
        }

        if (ALWAYS_ALLOWED.contains(sub)) return;

        // Admins need unrestricted access to *real* administrative subcommands
        // (managing or purging an actual race, real task admin actions) even
        // while their own account happens to be mid-tutorial - this gate
        // exists to onboard regular players, not to sandbox admins away from
        // their own admin tools. It deliberately does NOT cover the bare
        // "try this step" form (/md race, /md race gui|menu, /tasks) --
        // that's the one case an admin testing the tutorial itself still
        // needs routed into the tutorial's own mini-experience below, same
        // as any other player.
        boolean isAdmin = player.isOp() || player.hasPermission("marbledrop.admin");
        if (isAdmin && isRealManagementSubcommand(sub, actionArg(label, parts))) return;

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

    /** The arg right after the subcommand keyword - parts[2] for "/md race open", parts[1] for the standalone "/tasks admin". */
    private String actionArg(String label, String[] parts) {
        if (label.equals("tasks")) {
            return parts.length >= 2 ? parts[1] : null;
        }
        return parts.length >= 3 ? parts[2] : null;
    }

    /** True for subcommands past the bare/GUI form of "race" or "tasks" - real admin actions, not "try this step". */
    private boolean isRealManagementSubcommand(String sub, String action) {
        if (sub.equals("race") || sub.equals("races")) {
            if (action == null) return false; // bare "/md race"
            String a = action.toLowerCase();
            return !(a.equals("gui") || a.equals("menu"));
        }
        if (sub.equals("tasks")) {
            return action != null && action.equalsIgnoreCase("admin");
        }
        return false;
    }
}
