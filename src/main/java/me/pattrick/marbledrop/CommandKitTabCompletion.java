package me.pattrick.marbledrop;

import me.pattrick.marbledrop.races.TrackManager;
import me.pattrick.marbledrop.tutorial.TutorialStep;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tab completion for every top-level MarbleDrop command (md, race, tutorial,
 * team, dust, station, tasks, recipes - see CommandKit's javadoc for why
 * there are this many). Each gets its own dedicated helper method here,
 * dispatched by command name in onTabComplete - the per-command arg
 * positions below are one shallower than they were back when everything
 * lived under "/md <sub> ...", since the top-level command name itself no
 * longer eats an args slot.
 */
public class CommandKitTabCompletion implements TabCompleter {

  private final TrackManager tracks;

  public CommandKitTabCompletion(TrackManager tracks) {
    this.tracks = tracks;
  }

  private static boolean isAdmin(CommandSender sender) {
    return sender.hasPermission("marbledrop.admin");
  }

  private static List<String> filterStartsWith(List<String> options, String token) {
    if (options == null || options.isEmpty()) return Collections.emptyList();
    if (token == null || token.isEmpty()) return options;

    String lower = token.toLowerCase();
    List<String> out = new ArrayList<>();
    for (String s : options) {
      if (s != null && s.toLowerCase().startsWith(lower)) out.add(s);
    }
    return out;
  }

  private static List<String> onlinePlayerNames() {
    List<String> names = new ArrayList<>();
    for (Player p : Bukkit.getOnlinePlayers()) {
      names.add(p.getName());
    }
    return names;
  }

  private List<String> trackIds() {
    return tracks == null ? Collections.emptyList() : new ArrayList<>(tracks.sortedIds());
  }

  private static List<String> tutorialStepNames() {
    List<String> names = new ArrayList<>();
    for (TutorialStep step : TutorialStep.values()) {
      if (step == TutorialStep.COMPLETE) continue;
      names.add(step.name());
    }
    return names;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    String name = command.getName().toLowerCase();

    return switch (name) {
      case "tasks" -> tasksTabComplete(sender, args);
      case "race" -> raceTabComplete(sender, args);
      case "tutorial" -> tutorialTabComplete(sender, args);
      case "dust" -> dustTabComplete(sender, args);
      case "station" -> stationTabComplete(sender, args);
      case "md" -> mdTabComplete(sender, args);
      default -> Collections.emptyList(); // "team" takes no args, nothing else routes here
    };
  }

  // ---- /tasks <sub> ----
  private List<String> tasksTabComplete(CommandSender sender, String[] args) {
    if (args.length == 1) {
      List<String> taskSubs = new ArrayList<>();

      // If TasksCommand supports subcommands, add them here.
      // taskSubs.add("list");
      // taskSubs.add("claim");

      if (isAdmin(sender)) {
        taskSubs.add("admin");
      }

      return filterStartsWith(taskSubs, args[0]);
    }

    // ---- /tasks admin <sub> ---- (TasksAdminCommand only implements "reset")
    if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
      if (!isAdmin(sender)) return Collections.emptyList();
      return filterStartsWith(List.of("reset"), args[1]);
    }

    // ---- /tasks admin reset <daily|weekly|all> ----
    if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("reset")) {
      if (!isAdmin(sender)) return Collections.emptyList();
      return filterStartsWith(List.of("daily", "weekly", "all"), args[2]);
    }

    // ---- /tasks admin reset <daily|weekly|all> <player|all> ----
    if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("reset")) {
      if (!isAdmin(sender)) return Collections.emptyList();
      List<String> targets = new ArrayList<>(onlinePlayerNames());
      targets.add("all");
      return filterStartsWith(targets, args[3]);
    }

    return Collections.emptyList();
  }

  // ---- /race <sub> ----
  private List<String> raceTabComplete(CommandSender sender, String[] args) {
    if (args.length == 1) {
      List<String> raceSubs = new ArrayList<>();
      raceSubs.add("gui");
      raceSubs.add("watch");
      raceSubs.add("unwatch");
      raceSubs.add("leavewatch");
      raceSubs.add("join");
      raceSubs.add("leave");
      raceSubs.add("next");

      if (isAdmin(sender)) {
        raceSubs.add("open");
        raceSubs.add("close");
        raceSubs.add("start");
        raceSubs.add("clear");
        raceSubs.add("purge");
        raceSubs.add("forcecycle");
        raceSubs.add("test");
        raceSubs.add("schedule");
      }

      return filterStartsWith(raceSubs, args[0]);
    }

    // ---- /race <sub> <trackId> ----
    if (args.length == 2) {
      String sub = args[0].toLowerCase();

      if (sub.equals("open") || sub.equals("close") || sub.equals("start") || sub.equals("clear") || sub.equals("test")) {
        if (!isAdmin(sender)) return Collections.emptyList();
        return filterStartsWith(trackIds(), args[1]);
      }
      if (sub.equals("watch")) {
        return filterStartsWith(trackIds(), args[1]);
      }

      return Collections.emptyList();
    }

    // ---- /race test <trackId> <aiCount> ----
    if (args.length == 3 && args[0].equalsIgnoreCase("test")) {
      if (!isAdmin(sender)) return Collections.emptyList();
      return filterStartsWith(List.of("1", "2", "3", "4", "5", "noself"), args[2]);
    }

    // ---- /race test <trackId> <aiCount> noself ----
    if (args.length == 4 && args[0].equalsIgnoreCase("test")) {
      if (!isAdmin(sender)) return Collections.emptyList();
      return filterStartsWith(List.of("noself"), args[3]);
    }

    return Collections.emptyList();
  }

  // ---- /tutorial <sub> ----
  private List<String> tutorialTabComplete(CommandSender sender, String[] args) {
    if (args.length == 1) {
      List<String> tutorialSubs = new ArrayList<>();
      tutorialSubs.add("start");
      tutorialSubs.add("status");

      if (isAdmin(sender)) {
        tutorialSubs.add("reset");
        tutorialSubs.add("skip");
        tutorialSubs.add("setlocation");
        tutorialSubs.add("clearlocation");
        tutorialSubs.add("setrace");
        tutorialSubs.add("setpost");
        tutorialSubs.add("setcraftframes");
      }

      return filterStartsWith(tutorialSubs, args[0]);
    }

    // ---- /tutorial status|reset|skip|setlocation|clearlocation|setrace <arg> ----
    if (args.length == 2) {
      String sub = args[0].toLowerCase();

      if (sub.equals("status")) {
        return filterStartsWith(onlinePlayerNames(), args[1]);
      }
      if (sub.equals("reset") || sub.equals("skip")) {
        if (!isAdmin(sender)) return Collections.emptyList();
        return filterStartsWith(onlinePlayerNames(), args[1]);
      }
      if (sub.equals("setlocation") || sub.equals("clearlocation")) {
        if (!isAdmin(sender)) return Collections.emptyList();
        return filterStartsWith(tutorialStepNames(), args[1]);
      }
      if (sub.equals("setrace")) {
        if (!isAdmin(sender)) return Collections.emptyList();
        return filterStartsWith(trackIds(), args[1]);
      }

      return Collections.emptyList();
    }

    return Collections.emptyList();
  }

  // ---- /dust <sub> ----
  private List<String> dustTabComplete(CommandSender sender, String[] args) {
    if (args.length == 1) {
      List<String> dustSubs = new ArrayList<>();
      if (isAdmin(sender)) {
        dustSubs.add("admin");
      }
      return filterStartsWith(dustSubs, args[0]);
    }

    // ---- /dust admin <sub> ----
    if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
      if (!isAdmin(sender)) return Collections.emptyList();

      List<String> dustAdminSubs = new ArrayList<>();
      dustAdminSubs.add("give");
      dustAdminSubs.add("remove");
      dustAdminSubs.add("take"); // alias for remove - see DustAdminCommand
      dustAdminSubs.add("set");

      return filterStartsWith(dustAdminSubs, args[1]);
    }

    // ---- /dust admin <sub> <player> ----
    if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
      if (!isAdmin(sender)) return Collections.emptyList();

      String action = args[1].toLowerCase();
      if (action.equals("give") || action.equals("remove") || action.equals("take") || action.equals("set")) {
        return filterStartsWith(onlinePlayerNames(), args[2]);
      }

      return Collections.emptyList();
    }

    // ---- /dust admin <sub> <player> <amount> ----
    if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
      if (!isAdmin(sender)) return Collections.emptyList();

      String action = args[1].toLowerCase();
      if (action.equals("give") || action.equals("remove") || action.equals("take") || action.equals("set")) {
        List<String> amounts = new ArrayList<>();
        amounts.add("50");
        amounts.add("100");
        amounts.add("250");
        amounts.add("500");
        amounts.add("1000");
        amounts.add("2500");
        return filterStartsWith(amounts, args[3]);
      }

      return Collections.emptyList();
    }

    return Collections.emptyList();
  }

  // ---- /station <table|recycler|upgrade> <sub> ---- (whole command is admin-only)
  private List<String> stationTabComplete(CommandSender sender, String[] args) {
    if (!isAdmin(sender)) return Collections.emptyList();

    if (args.length == 1) {
      return filterStartsWith(List.of("table", "recycler", "upgrade"), args[0]);
    }

    if (args.length == 2) {
      String type = args[0].toLowerCase();
      List<String> subs = new ArrayList<>(List.of("give", "remove", "count"));
      if (type.equals("table")) subs.add("private"); // InfusionTableCommand-only

      if (!type.equals("table") && !type.equals("recycler") && !type.equals("upgrade")) {
        return Collections.emptyList();
      }
      return filterStartsWith(subs, args[1]);
    }

    return Collections.emptyList();
  }

  // ---- /md <sub> ----
  private List<String> mdTabComplete(CommandSender sender, String[] args) {
    if (args.length == 1) {
      List<String> base = new ArrayList<>();
      base.add("help");
      base.add("version");

      if (isAdmin(sender)) {
        base.add("track");
        base.add("reload");
        base.add("update");
        base.add("debug");
        base.add("pdc");
      }

      return filterStartsWith(base, args[0]);
    }

    // ---- /md track <sub> ----
    // TrackCommand requires marbledrop.admin for everything under it.
    if (args.length == 2 && args[0].equalsIgnoreCase("track")) {
      if (!isAdmin(sender)) return Collections.emptyList();

      List<String> trackSubs = List.of(
              "gui", "create", "addpoint", "info", "delete",
              "show", "hide", "run", "setwatch", "clearwatch", "autorace", "laps"
      );
      return filterStartsWith(trackSubs, args[1]);
    }

    // ---- /md track <sub> <id> ---- (subcommands that take an existing track id)
    if (args.length == 3 && args[0].equalsIgnoreCase("track")) {
      if (!isAdmin(sender)) return Collections.emptyList();

      String sub = args[1].toLowerCase();
      if (sub.equals("addpoint") || sub.equals("info") || sub.equals("delete")
              || sub.equals("show") || sub.equals("run") || sub.equals("setwatch") || sub.equals("clearwatch")
              || sub.equals("autorace") || sub.equals("laps")) {
        return filterStartsWith(trackIds(), args[2]);
      }

      return Collections.emptyList();
    }

    // ---- /md track autorace <id> <on|off> ----
    if (args.length == 4 && args[0].equalsIgnoreCase("track") && args[1].equalsIgnoreCase("autorace")) {
      if (!isAdmin(sender)) return Collections.emptyList();
      return filterStartsWith(List.of("on", "off"), args[3]);
    }

    // ---- /md track laps <id> <count> ----
    if (args.length == 4 && args[0].equalsIgnoreCase("track") && args[1].equalsIgnoreCase("laps")) {
      if (!isAdmin(sender)) return Collections.emptyList();
      return filterStartsWith(List.of("1", "2", "3", "4", "5"), args[3]);
    }

    return Collections.emptyList();
  }
}
