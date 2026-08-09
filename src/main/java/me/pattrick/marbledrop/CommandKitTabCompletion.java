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

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

    if (command.getName().equalsIgnoreCase("tasks")) {
      return tasksTabComplete(sender, args);
    }

    // Only for /md
    if (!command.getName().equalsIgnoreCase("md")) return Collections.emptyList();

    // ---- /md <sub> ----
    if (args.length == 1) {
      List<String> base = new ArrayList<>();

      // everyone
      base.add("help");
      base.add("dust");
      base.add("race");
      base.add("races");
      base.add("join");
      base.add("leave");
      base.add("team");
      base.add("tutorial");

      // admin-only router targets (TrackCommand and UpgradeStationCommand
      // both gate everything on marbledrop.admin, same as the station
      // give/remove/count commands below)
      if (isAdmin(sender)) {
        base.add("upgrade");
        base.add("upgrades");
        base.add("track");
        base.add("table");
        base.add("infusiontable");
        base.add("recycler");
        base.add("recycle");

        base.add("reload");
        base.add("debug");
        base.add("pdc");
      }

      return filterStartsWith(base, args[0]);
    }

    // ---- /md upgrade|upgrades <sub> ---- (admin-only: UpgradeStationCommand)
    if (args.length == 2 && (args[0].equalsIgnoreCase("upgrade") || args[0].equalsIgnoreCase("upgrades"))) {
      if (!isAdmin(sender)) return Collections.emptyList();

      List<String> upSubs = new ArrayList<>();
      upSubs.add("give");
      upSubs.add("remove");
      upSubs.add("count");
      return filterStartsWith(upSubs, args[1]);
    }

    // ---- /md dust <sub> ----
    if (args.length == 2 && args[0].equalsIgnoreCase("dust")) {
      List<String> dustSubs = new ArrayList<>();

      // If your DustCommand supports subcommands, add them here.
      // dustSubs.add("balance");
      // dustSubs.add("pay");

      if (isAdmin(sender)) {
        dustSubs.add("admin");
      }

      return filterStartsWith(dustSubs, args[1]);
    }

    // ---- /md dust admin <sub> ----
    if (args.length == 3 && args[0].equalsIgnoreCase("dust") && args[1].equalsIgnoreCase("admin")) {
      if (!isAdmin(sender)) return Collections.emptyList();

      List<String> dustAdminSubs = new ArrayList<>();
      dustAdminSubs.add("give");
      dustAdminSubs.add("remove");
      dustAdminSubs.add("take"); // alias for remove -- see DustAdminCommand
      dustAdminSubs.add("set");

      return filterStartsWith(dustAdminSubs, args[2]);
    }

    // ---- /md dust admin <sub> <player> ----
    if (args.length == 4 && args[0].equalsIgnoreCase("dust") && args[1].equalsIgnoreCase("admin")) {
      if (!isAdmin(sender)) return Collections.emptyList();

      String action = args[2].toLowerCase();
      if (action.equals("give") || action.equals("remove") || action.equals("take") || action.equals("set")) {
        return filterStartsWith(onlinePlayerNames(), args[3]);
      }

      return Collections.emptyList();
    }

    // ---- /md dust admin <sub> <player> <amount> ----
    if (args.length == 5 && args[0].equalsIgnoreCase("dust") && args[1].equalsIgnoreCase("admin")) {
      if (!isAdmin(sender)) return Collections.emptyList();

      String action = args[2].toLowerCase();
      if (action.equals("give") || action.equals("remove") || action.equals("take") || action.equals("set")) {
        List<String> amounts = new ArrayList<>();
        amounts.add("50");
        amounts.add("100");
        amounts.add("250");
        amounts.add("500");
        amounts.add("1000");
        amounts.add("2500");
        return filterStartsWith(amounts, args[4]);
      }

      return Collections.emptyList();
    }

    // ---- /md table|infusiontable <sub> ----
    if (args.length == 2 && (args[0].equalsIgnoreCase("table") || args[0].equalsIgnoreCase("infusiontable"))) {
      if (!isAdmin(sender)) return Collections.emptyList();

      List<String> tableSubs = new ArrayList<>();
      tableSubs.add("give");
      tableSubs.add("remove");
      tableSubs.add("count");
      tableSubs.add("private");
      return filterStartsWith(tableSubs, args[1]);
    }

    // ---- /md recycler|recycle <sub> ----
    if (args.length == 2 && (args[0].equalsIgnoreCase("recycler") || args[0].equalsIgnoreCase("recycle"))) {
      if (!isAdmin(sender)) return Collections.emptyList();

      List<String> recyclerSubs = new ArrayList<>();
      recyclerSubs.add("give");
      recyclerSubs.add("remove");
      recyclerSubs.add("count");
      return filterStartsWith(recyclerSubs, args[1]);
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

    // ---- /md race|races <sub> ----
    if (args.length == 2 && (args[0].equalsIgnoreCase("race") || args[0].equalsIgnoreCase("races"))) {
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
      }

      return filterStartsWith(raceSubs, args[1]);
    }

    // ---- /md race|races <sub> <trackId> ----
    if (args.length == 3 && (args[0].equalsIgnoreCase("race") || args[0].equalsIgnoreCase("races"))) {
      String sub = args[1].toLowerCase();

      if (sub.equals("open") || sub.equals("close") || sub.equals("start") || sub.equals("clear") || sub.equals("test")) {
        if (!isAdmin(sender)) return Collections.emptyList();
        return filterStartsWith(trackIds(), args[2]);
      }
      if (sub.equals("watch")) {
        return filterStartsWith(trackIds(), args[2]);
      }

      return Collections.emptyList();
    }

    // ---- /md race|races test <trackId> <aiCount> ----
    if (args.length == 4 && (args[0].equalsIgnoreCase("race") || args[0].equalsIgnoreCase("races"))
            && args[1].equalsIgnoreCase("test")) {
      if (!isAdmin(sender)) return Collections.emptyList();
      return filterStartsWith(List.of("1", "2", "3", "4", "5", "noself"), args[3]);
    }

    // ---- /md race|races test <trackId> <aiCount> noself ----
    if (args.length == 5 && (args[0].equalsIgnoreCase("race") || args[0].equalsIgnoreCase("races"))
            && args[1].equalsIgnoreCase("test")) {
      if (!isAdmin(sender)) return Collections.emptyList();
      return filterStartsWith(List.of("noself"), args[4]);
    }

    // ---- /md tutorial <sub> ----
    if (args.length == 2 && args[0].equalsIgnoreCase("tutorial")) {
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

      return filterStartsWith(tutorialSubs, args[1]);
    }

    // ---- /md tutorial status|reset|skip <player> ----
    if (args.length == 3 && args[0].equalsIgnoreCase("tutorial")) {
      String sub = args[1].toLowerCase();

      if (sub.equals("status")) {
        return filterStartsWith(onlinePlayerNames(), args[2]);
      }
      if (sub.equals("reset") || sub.equals("skip")) {
        if (!isAdmin(sender)) return Collections.emptyList();
        return filterStartsWith(onlinePlayerNames(), args[2]);
      }
      if (sub.equals("setlocation") || sub.equals("clearlocation")) {
        if (!isAdmin(sender)) return Collections.emptyList();
        return filterStartsWith(tutorialStepNames(), args[2]);
      }
      if (sub.equals("setrace")) {
        if (!isAdmin(sender)) return Collections.emptyList();
        return filterStartsWith(trackIds(), args[2]);
      }

      return Collections.emptyList();
    }

    return Collections.emptyList();
  }
}
