package me.pattrick.marbledrop.races.team;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TeamMenuListener implements Listener {

    private enum State { TEAM_NAME, MEMBER_NAME, TEXTURE_URL }

    private record Pending(State state, int slot) {}

    private final Plugin plugin;
    private final CustomTeamManager mgr;
    private final Map<UUID, Pending> waiting = new HashMap<>();

    public TeamMenuListener(Plugin plugin, CustomTeamManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!TeamMenu.TITLE.equals(e.getView().getTitle())) return;

        e.setCancelled(true);

        int raw       = e.getRawSlot();
        ClickType ct  = e.getClick();

        // --- Team name ---
        if (raw == TeamMenu.SLOT_TEAM_NAME) {
            promptChat(p, new Pending(State.TEAM_NAME, -1),
                    ChatColor.YELLOW + "Type your team name (max 32 chars), or 'cancel':");
            return;
        }

        // --- Team color ---
        if (raw == TeamMenu.SLOT_TEAM_COLOR) {
            DyeColor next = TeamMenu.nextColor(TeamMenu.parseDye(mgr.getTeamColor(p)));
            mgr.setTeamColor(p, next.name());
            TeamMenu.open(p, mgr);
            return;
        }

        // --- Member slots ---
        for (int i = 0; i < TeamMenu.MEMBER_SLOTS.length; i++) {
            if (raw != TeamMenu.MEMBER_SLOTS[i]) continue;
            handleMemberClick(p, i, ct);
            return;
        }
    }

    private void handleMemberClick(Player p, int slot, ClickType ct) {
        String name = mgr.getMemberName(p, slot);

        // Slot not yet named  -  prompt for name regardless of click type
        if (name.isEmpty()) {
            promptChat(p, new Pending(State.MEMBER_NAME, slot),
                    ChatColor.YELLOW + "Type athlete " + (slot + 1) + " name (max 24 chars), or 'cancel':");
            return;
        }

        if (ct == ClickType.SHIFT_RIGHT) {
            promptChat(p, new Pending(State.MEMBER_NAME, slot),
                    ChatColor.YELLOW + "Type a new name for " + ChatColor.AQUA + name + ChatColor.YELLOW + ", or 'cancel':");
            return;
        }

        if (ct == ClickType.RIGHT) {
            promptChat(p, new Pending(State.TEXTURE_URL, slot),
                    ChatColor.YELLOW + "Paste the marblebase.net texture URL for " + ChatColor.AQUA + name + ChatColor.YELLOW + ":",
                    ChatColor.GRAY + "(https://marblebase.net/...)",
                    ChatColor.GRAY + "Type 'cancel' to abort.");
            return;
        }

        if (ct == ClickType.SHIFT_LEFT) {
            if (!mgr.isMemberIssued(p, slot)) {
                p.sendMessage(ChatColor.RED + "Marble hasn't been issued yet.");
                return;
            }
            mgr.resetIssued(p, slot);
            giveMarble(p, slot);
            return;
        }

        // Normal left-click
        if (mgr.getTeamName(p).isEmpty()) {
            p.sendMessage(ChatColor.RED + "Set your team name first!");
            return;
        }
        if (mgr.isMemberIssued(p, slot)) {
            p.sendMessage(ChatColor.GRAY + name + "'s marble was already issued. Shift+click to re-issue (resets stats).");
            return;
        }
        giveMarble(p, slot);
    }

    private void giveMarble(Player p, int slot) {
        ItemStack marble = mgr.issueMarble(p, slot);
        if (marble == null) return;
        p.getInventory().addItem(marble);
        p.sendMessage(ChatColor.GREEN + "Added " + ChatColor.AQUA + mgr.getMemberName(p, slot)
                + ChatColor.GREEN + "'s marble to your inventory!");
        TeamMenu.open(p, mgr);
    }

    // ---- Chat input ----

    private void promptChat(Player p, Pending state, String... lines) {
        waiting.put(p.getUniqueId(), state);
        p.closeInventory();
        for (String line : lines) p.sendMessage(line);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Pending pi = waiting.remove(e.getPlayer().getUniqueId());
        if (pi == null) return;

        e.setCancelled(true);

        Player p   = e.getPlayer();
        String msg = e.getMessage().trim();

        if (msg.equalsIgnoreCase("cancel")) {
            p.sendMessage(ChatColor.GRAY + "Cancelled.");
            plugin.getServer().getScheduler().runTask(plugin, () -> TeamMenu.open(p, mgr));
            return;
        }

        switch (pi.state()) {
            case TEAM_NAME -> {
                if (msg.length() > 32) {
                    p.sendMessage(ChatColor.RED + "Team name too long (max 32 chars).");
                } else {
                    mgr.setTeamName(p, msg);
                    p.sendMessage(ChatColor.GREEN + "Team name set to: " + ChatColor.WHITE + msg);
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> TeamMenu.open(p, mgr));
            }

            case MEMBER_NAME -> {
                if (msg.length() > 24) {
                    p.sendMessage(ChatColor.RED + "Name too long (max 24 chars).");
                } else {
                    mgr.setMemberName(p, pi.slot(), msg);
                    p.sendMessage(ChatColor.GREEN + "Athlete " + (pi.slot() + 1) + " named: " + ChatColor.WHITE + msg);
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> TeamMenu.open(p, mgr));
            }

            case TEXTURE_URL -> {
                if (!msg.startsWith("https://marblebase.net")) {
                    p.sendMessage(ChatColor.RED + "URL must be from marblebase.net.");
                    plugin.getServer().getScheduler().runTask(plugin, () -> TeamMenu.open(p, mgr));
                    return;
                }
                p.sendMessage(ChatColor.GRAY + "Fetching texture...");
                int s = pi.slot();
                mgr.fetchTexture(msg,
                        () -> {
                            p.sendMessage(ChatColor.RED + "Failed to fetch texture. Check the URL and try again.");
                            TeamMenu.open(p, mgr);
                        },
                        base64 -> {
                            mgr.setMemberTexture(p, s, base64);
                            p.sendMessage(ChatColor.GREEN + "Texture applied to " + ChatColor.AQUA
                                    + mgr.getMemberName(p, s) + ChatColor.GREEN + "!");
                            TeamMenu.open(p, mgr);
                        }
                );
            }
        }
    }
}
