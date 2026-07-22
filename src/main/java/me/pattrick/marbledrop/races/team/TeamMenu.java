package me.pattrick.marbledrop.races.team;

import me.pattrick.marbledrop.progression.infusion.heads.SkullUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public final class TeamMenu {

    public static final String TITLE = ChatColor.DARK_PURPLE + "My Marble Team";

    static final int SLOT_TEAM_NAME  = 2;
    static final int SLOT_TEAM_COLOR = 6;
    static final int[] MEMBER_SLOTS  = {10, 11, 12, 13, 14};

    // Colors available for cycling
    private static final DyeColor[] COLORS = {
        DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
        DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.CYAN,
        DyeColor.PURPLE, DyeColor.BLUE, DyeColor.RED
    };

    private TeamMenu() {}

    public static void open(Player player, CustomTeamManager mgr) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        ItemStack glass = named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        inv.setItem(SLOT_TEAM_NAME,  buildTeamNameItem(mgr.getTeamName(player)));
        inv.setItem(SLOT_TEAM_COLOR, buildColorItem(mgr.getTeamColor(player)));

        for (int i = 0; i < CustomTeamManager.TEAM_SIZE; i++) {
            inv.setItem(MEMBER_SLOTS[i], buildMemberItem(player, mgr, i));
        }

        player.openInventory(inv);
    }

    // ---- Slot builders ----

    private static ItemStack buildTeamNameItem(String teamName) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ChatColor.YELLOW + "Team Name: " +
                (teamName.isEmpty() ? ChatColor.RED + "Not set" : ChatColor.WHITE + teamName));
        meta.setLore(List.of(ChatColor.GRAY + "Click to set team name"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildColorItem(String colorName) {
        DyeColor dye  = parseDye(colorName);
        ItemStack item = new ItemStack(dyeToWool(dye));
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ChatColor.YELLOW + "Color: " + ChatColor.WHITE + colorName);
        meta.setLore(List.of(ChatColor.GRAY + "Click to change"));
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack buildMemberItem(Player player, CustomTeamManager mgr, int slot) {
        String name     = mgr.getMemberName(player, slot);
        boolean hasText = !mgr.getMemberTexture(player, slot).isEmpty();
        boolean issued  = mgr.isMemberIssued(player, slot);
        boolean noTeam  = mgr.getTeamName(player).isEmpty();

        if (name.isEmpty()) {
            return withLore(
                new ItemStack(Material.GRAY_WOOL),
                ChatColor.GRAY + "Slot " + (slot + 1),
                List.of(ChatColor.GRAY + "Click to name this athlete")
            );
        }

        // Build skull with or without texture
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        String tex = mgr.getMemberTexture(player, slot);
        if (!tex.isEmpty()) {
            ItemMeta m = item.getItemMeta();
            if (m instanceof SkullMeta sm) {
                SkullUtil.applyBase64(sm, tex);
                item.setItemMeta(sm);
            }
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Texture: " + (hasText ? ChatColor.GREEN + "Set" : ChatColor.RED + "Not set"));
        if (noTeam) {
            lore.add(ChatColor.RED + "Set a team name first!");
        } else if (issued) {
            lore.add(ChatColor.GRAY + "Marble: " + ChatColor.YELLOW + "Already issued");
            lore.add("");
            lore.add(ChatColor.GRAY + "Left-click: " + ChatColor.DARK_GRAY + "Already given");
            lore.add(ChatColor.RED + "Shift+left: Re-issue (resets stats!)");
        } else {
            lore.add(ChatColor.GRAY + "Marble: " + ChatColor.GREEN + "Ready");
            lore.add("");
            lore.add(ChatColor.GREEN + "Left-click: Get marble");
        }
        lore.add(ChatColor.YELLOW + "Right-click: Set texture URL");
        lore.add(ChatColor.YELLOW + "Shift+right: Rename");

        return withLore(item, ChatColor.AQUA + name, lore);
    }

    // ---- Color cycling ----

    static DyeColor nextColor(DyeColor current) {
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i] == current) return COLORS[(i + 1) % COLORS.length];
        }
        return COLORS[0];
    }

    static DyeColor parseDye(String name) {
        try { return DyeColor.valueOf(name.toUpperCase()); }
        catch (Exception e) { return DyeColor.WHITE; }
    }

    // ---- Helpers ----

    private static ItemStack named(ItemStack item, String name) {
        ItemMeta m = item.getItemMeta();
        if (m != null) { m.setDisplayName(name); item.setItemMeta(m); }
        return item;
    }

    private static ItemStack withLore(ItemStack item, String displayName, List<String> lore) {
        ItemMeta m = item.getItemMeta();
        if (m == null) return item;
        m.setDisplayName(displayName);
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    private static Material dyeToWool(DyeColor dye) {
        return switch (dye) {
            case ORANGE     -> Material.ORANGE_WOOL;
            case MAGENTA    -> Material.MAGENTA_WOOL;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_WOOL;
            case YELLOW     -> Material.YELLOW_WOOL;
            case LIME       -> Material.LIME_WOOL;
            case PINK       -> Material.PINK_WOOL;
            case CYAN       -> Material.CYAN_WOOL;
            case PURPLE     -> Material.PURPLE_WOOL;
            case BLUE       -> Material.BLUE_WOOL;
            case RED        -> Material.RED_WOOL;
            default         -> Material.WHITE_WOOL;
        };
    }
}
