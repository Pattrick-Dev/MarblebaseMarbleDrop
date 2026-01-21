package me.pattrick.marbledrop;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class CommandKitTabCompletion implements TabCompleter {
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if ((sender.isOp() && args.length == 1) || (sender.hasPermission("marbledrop.admin") && args.length == 1)) {
      List<String> completion = new ArrayList<>();
      completion.add("debug");
      completion.add("chance");
      completion.add("cooldown");
      completion.add("rcd");
      completion.add("pdc");
      return completion;
    } 
    if (args.length == 1) {
      List<String> completionNotOP = new ArrayList<>();
      completionNotOP.add("cooldown");
      return completionNotOP;
    } 
    return null;
  }
}
