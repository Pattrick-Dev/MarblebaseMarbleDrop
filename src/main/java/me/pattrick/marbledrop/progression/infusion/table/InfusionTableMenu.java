package me.pattrick.marbledrop.progression.infusion.table;

import me.pattrick.marbledrop.progression.DustManager;
import me.pattrick.marbledrop.progression.infusion.InfusionService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class InfusionTableMenu {

    public static final String TITLE = ChatColor.DARK_PURPLE + "Infusion Table";

    // Slots
    public static final int SLOT_CATALYST = 13;
    public static final int SLOT_CONFIRM = 22;
    public static final int SLOT_MINUS = 19;
    public static final int SLOT_PLUS = 25;
    public static final int SLOT_INFO = 4;

    private final DustManager dust;
    private final InfusionService infusion;
    private final Block cauldron;

    private int dustAmount;

    public InfusionTableMenu(DustManager dust, InfusionService infusion, Block cauldron) {
        this.dust = dust;
        this.infusion = infusion;
        this.cauldron = cauldron;
        this.dustAmount = 50;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, TITLE);
        draw(player, inv);
        player.openInventory(inv);
    }

    public void draw(Player player, Inventory inv) {
        // Preserve whatever is currently in the catalyst slot
        ItemStack catalyst = inv.getItem(SLOT_CATALYST);

        // Fill background BUT do not overwrite interactive slots
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(" ");
            filler.setItemMeta(fm);
        }

        for (int i = 0; i < inv.getSize(); i++) {
            if (i == SLOT_CATALYST || i == SLOT_CONFIRM || i == SLOT_MINUS || i == SLOT_PLUS || i == SLOT_INFO) {
                continue;
            }
            inv.setItem(i, filler);
        }

        // Put catalyst back (or leave empty)
        inv.setItem(SLOT_CATALYST, catalyst == null ? new ItemStack(Material.AIR) : catalyst);

        // Buttons
        inv.setItem(SLOT_MINUS, button(Material.REDSTONE, ChatColor.RED + "-50 Dust",
                List.of(ChatColor.GRAY + "Decrease infusion dust by 50")));

        inv.setItem(SLOT_PLUS, button(Material.GLOWSTONE_DUST, ChatColor.GREEN + "+50 Dust",
                List.of(ChatColor.GRAY + "Increase infusion dust by 50")));

        // Info (this is the "confirmation" that catalyst is being counted)
        int balance = dust.getDust(player);
        ItemStack cat = inv.getItem(SLOT_CATALYST);
        int catalystValue = CatalystValue.valueOf(cat);

        String catName = (cat == null || cat.getType().isAir())
                ? (ChatColor.DARK_GRAY + "None")
                : (ChatColor.WHITE + prettyName(cat) + ChatColor.GRAY + " x" + cat.getAmount());

        String willConsume = (cat == null || cat.getType().isAir())
                ? (ChatColor.DARK_GRAY + "Nothing")
                : (ChatColor.YELLOW + "" + cat.getAmount() + " " + prettyName(cat));

        inv.setItem(SLOT_INFO, button(Material.BOOK, ChatColor.LIGHT_PURPLE + "Infusion Details",
                List.of(
                        ChatColor.GRAY + "Dust Balance: " + ChatColor.YELLOW + balance,
                        ChatColor.GRAY + "Dust Amount: " + ChatColor.YELLOW + dustAmount,
                        "",
                        ChatColor.GRAY + "Catalyst: " + catName,
                        ChatColor.GRAY + "Catalyst Value: " + ChatColor.YELLOW + catalystValue,
                        ChatColor.GRAY + "Will Consume: " + willConsume,
                        "",
                        ChatColor.GRAY + "Total Value: " + ChatColor.GOLD + (dustAmount + catalystValue),
                        "",
                        ChatColor.DARK_GRAY + "Put any item in the center slot",
                        ChatColor.DARK_GRAY + "to boost the infusion outcome.",
                        ChatColor.DARK_GRAY + "Close menu to get items back."
                )));

        // Confirm
        inv.setItem(SLOT_CONFIRM, button(Material.AMETHYST_SHARD, ChatColor.GOLD + "Infuse",
                List.of(
                        ChatColor.GRAY + "Consume dust + 1 catalyst item",
                        ChatColor.GRAY + "and form a marble.",
                        "",
                        ChatColor.YELLOW + "Click to begin."
                )));
    }

    private ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            m.setLore(lore);
            it.setItemMeta(m);
        }
        return it;
    }

    public void adjust(Player player, Inventory inv, int delta) {
        dustAmount = Math.max(50, dustAmount + delta);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        draw(player, inv);
    }

    public void confirm(Player player, Inventory inv) {
        ItemStack catalyst = inv.getItem(SLOT_CATALYST);
        int catalystValue = CatalystValue.valueOf(catalyst);

        // Try infusion first (dust is deducted inside infuseToItem)
        ItemStack marble = infusion.infuseToItem(player, dustAmount, catalystValue);
        if (marble == null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            draw(player, inv);
            return;
        }

        // Only consume 1 catalyst item AFTER infusion succeeds
        if (catalyst != null && !catalyst.getType().isAir()) {
            int amt = catalyst.getAmount();
            if (amt <= 1) {
                inv.setItem(SLOT_CATALYST, new ItemStack(Material.AIR));
            } else {
                catalyst.setAmount(amt - 1);
                inv.setItem(SLOT_CATALYST, catalyst);
            }
        }

        player.closeInventory();

        // Run animation + give item at the end
        InfusionTableProcess.run(player, cauldron, marble);
    }

    public Block getCauldron() {
        return cauldron;
    }

    private String prettyName(ItemStack item) {
        ItemMeta m = item.getItemMeta();
        if (m != null && m.hasDisplayName()) {
            return ChatColor.stripColor(m.getDisplayName());
        }
        String s = item.getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
