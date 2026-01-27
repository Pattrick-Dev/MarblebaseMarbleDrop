package me.pattrick.marbledrop;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandKitTabCompletion implements TabCompleter {

  private static boolean isAdmin(CommandSender sender) {
    return sender.isOp() || sender.hasPermission("marbledrop.admin");
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

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

    // Only for /md
    if (!command.getName().equalsIgnoreCase("md")) return Collections.emptyList();

    // ---- /md <sub> ----
    if (args.length == 1) {
      List<String> base = new ArrayList<>();

      // everyone
      base.add("cd");
      base.add("cooldown");
      base.add("dust");
      base.add("tasks");

      // admin-only router targets
      if (isAdmin(sender)) {
        base.add("table");
        base.add("infusiontable");
        base.add("recycler");
        base.add("recycle");

        base.add("debug");
        base.add("chance");
        base.add("rcd");
        base.add("pdc");
      }

      return filterStartsWith(base, args[0]);
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
      dustAdminSubs.add("take");
      dustAdminSubs.add("set");
      dustAdminSubs.add("reset");

      return filterStartsWith(dustAdminSubs, args[2]);
    }

    // ---- /md dust admin <sub> <player> ----
    if (args.length == 4 && args[0].equalsIgnoreCase("dust") && args[1].equalsIgnoreCase("admin")) {
      if (!isAdmin(sender)) return Collections.emptyList();

      String action = args[2].toLowerCase();
      if (action.equals("give") || action.equals("take") || action.equals("set")) {
        return filterStartsWith(onlinePlayerNames(), args[3]);
      }

      // reset might still want a player depending on your command
      if (action.equals("reset")) {
        return filterStartsWith(onlinePlayerNames(), args[3]);
      }

      return Collections.emptyList();
    }

    // ---- /md dust admin <sub> <player> <amount> ----
    if (args.length == 5 && args[0].equalsIgnoreCase("dust") && args[1].equalsIgnoreCase("admin")) {
      if (!isAdmin(sender)) return Collections.emptyList();

      String action = args[2].toLowerCase();
      if (action.equals("give") || action.equals("take") || action.equals("set")) {
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

    // ---- /md tasks <sub> ----
    if (args.length == 2 && args[0].equalsIgnoreCase("tasks")) {
      List<String> taskSubs = new ArrayList<>();

      // If TasksCommand supports subcommands, add them here.
      // taskSubs.add("list");
      // taskSubs.add("claim");

      if (isAdmin(sender)) {
        taskSubs.add("admin");
      }

      return filterStartsWith(taskSubs, args[1]);
    }

    // ---- /md tasks admin <sub> ----
    if (args.length == 3 && args[0].equalsIgnoreCase("tasks") && args[1].equalsIgnoreCase("admin")) {
      if (!isAdmin(sender)) return Collections.emptyList();

      List<String> tasksAdminSubs = new ArrayList<>();
      // Add your TasksAdminCommand subcommands here if you want.
      // tasksAdminSubs.add("add");
      // tasksAdminSubs.add("remove");
      // tasksAdminSubs.add("reload");

      return filterStartsWith(tasksAdminSubs, args[2]);
    }

    // ---- /md table|infusiontable <sub> ----
    if (args.length == 2 && (args[0].equalsIgnoreCase("table") || args[0].equalsIgnoreCase("infusiontable"))) {
      if (!isAdmin(sender)) return Collections.emptyList();

      List<String> tableSubs = new ArrayList<>();
      tableSubs.add("add");
      tableSubs.add("remove");
      tableSubs.add("count");
      return filterStartsWith(tableSubs, args[1]);
    }

    return Collections.emptyList();
  }
}
