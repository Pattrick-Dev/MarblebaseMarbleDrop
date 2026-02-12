package me.pattrick.marbledrop.races;

import me.pattrick.marbledrop.marble.MarbleItem;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RaceCommand implements CommandExecutor {

    private final RaceManager races;

    public RaceCommand(RaceManager races) {
        this.races = races;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Please run this command as a player!");
            return true;
        }

        if (args.length == 0) {
            usage(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            case "enter" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md race enter <trackId>");
                    return true;
                }

                String trackId = args[1].toLowerCase();

                ItemStack held = player.getInventory().getItemInMainHand();
                if (held == null || held.getType().isAir() || !MarbleItem.isMarble(held)) {
                    player.sendMessage(ChatColor.RED + "Hold a marble in your main hand first.");
                    return true;
                }

                races.enter(player, trackId, held);
                return true;
            }

            case "leave" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md race leave <trackId>");
                    return true;
                }

                races.leave(player, args[1].toLowerCase());
                return true;
            }

            case "list" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md race list <trackId>");
                    return true;
                }

                races.listToPlayer(player, args[1].toLowerCase());
                return true;
            }

            case "start" -> {
                if (!player.hasPermission("marbledrop.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md race start <trackId>");
                    return true;
                }

                races.start(player, args[1].toLowerCase());
                return true;
            }

            case "clear" -> {
                if (!player.hasPermission("marbledrop.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /md race clear <trackId>");
                    return true;
                }

                races.clear(args[1].toLowerCase());
                player.sendMessage(ChatColor.YELLOW + "Cleared race entries for '" + args[1].toLowerCase() + "'.");
                return true;
            }

            default -> {
                usage(player);
                return true;
            }
        }
    }

    private void usage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Race commands:");
        player.sendMessage(ChatColor.GRAY + "/md race enter <trackId>");
        player.sendMessage(ChatColor.GRAY + "/md race leave <trackId>");
        player.sendMessage(ChatColor.GRAY + "/md race list <trackId>");
        player.sendMessage(ChatColor.GRAY + "/md race start <trackId> " + ChatColor.DARK_GRAY + "(admin)");
        player.sendMessage(ChatColor.GRAY + "/md race clear <trackId> " + ChatColor.DARK_GRAY + "(admin)");
    }
}
