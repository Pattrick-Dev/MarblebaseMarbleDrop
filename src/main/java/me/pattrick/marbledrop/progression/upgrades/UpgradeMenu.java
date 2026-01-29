package me.pattrick.marbledrop.progression.upgrades;

public final class UpgradeMenu {

    private UpgradeMenu() {}

    public static final String TITLE = "Marble Upgrades";

    // Track which slot they opened from (main-hand slot only)
    private static final java.util.Map<java.util.UUID, Integer> OPEN_SLOT = new java.util.HashMap<>();

    public static void open(org.bukkit.entity.Player player, me.pattrick.marbledrop.progression.DustManager dustManager) {
        if (player == null) return;

        int slot = player.getInventory().getHeldItemSlot();
        org.bukkit.inventory.ItemStack held = player.getInventory().getItem(slot);

        if (held == null || held.getType().isAir()) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Hold a marble in your main hand to upgrade it.");
            return;
        }

        // No legacy support: must be a modern PDC marble
        if (!me.pattrick.marbledrop.marble.MarbleItem.isMarble(held)) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Hold a marble in your main hand to upgrade it.");
            return;
        }

        // Re-fetch (some clients behave better this way)
        held = player.getInventory().getItem(slot);

        me.pattrick.marbledrop.marble.MarbleData data = me.pattrick.marbledrop.marble.MarbleItem.read(held);
        if (data == null) {
            player.sendMessage(org.bukkit.ChatColor.RED + "That item doesn't look like a valid marble.");
            return;
        }

        OPEN_SLOT.put(player.getUniqueId(), slot);

        org.bukkit.inventory.Inventory inv = org.bukkit.Bukkit.createInventory(player, 27, TITLE);

        // background filler
        org.bukkit.inventory.ItemStack filler = new org.bukkit.inventory.ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(" ");
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // Marble preview in center
        inv.setItem(13, held.clone());

        // Stat buttons
        inv.setItem(10, makeStatButton(data, me.pattrick.marbledrop.marble.MarbleStat.SPEED, org.bukkit.Material.FEATHER));
        inv.setItem(11, makeStatButton(data, me.pattrick.marbledrop.marble.MarbleStat.ACCEL, org.bukkit.Material.SUGAR));
        inv.setItem(12, makeStatButton(data, me.pattrick.marbledrop.marble.MarbleStat.HANDLING, org.bukkit.Material.TRIPWIRE_HOOK));
        inv.setItem(14, makeStatButton(data, me.pattrick.marbledrop.marble.MarbleStat.STABILITY, org.bukkit.Material.SHIELD));
        inv.setItem(15, makeStatButton(data, me.pattrick.marbledrop.marble.MarbleStat.BOOST, org.bukkit.Material.BLAZE_POWDER));

        // Info item
        org.bukkit.inventory.ItemStack info = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER);
        org.bukkit.inventory.meta.ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.setDisplayName(org.bukkit.ChatColor.YELLOW + "Info");
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(org.bukkit.ChatColor.GRAY + "Click a stat to upgrade.");
            lore.add(org.bukkit.ChatColor.GRAY + "Dust: " + org.bukkit.ChatColor.WHITE + dustManager.getDust(player));
            lore.add("");
            lore.add(org.bukkit.ChatColor.DARK_GRAY + "Rarity caps apply per-stat.");
            im.setLore(lore);
            info.setItemMeta(im);
        }
        inv.setItem(22, info);

        // Close
        org.bukkit.inventory.ItemStack close = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BARRIER);
        org.bukkit.inventory.meta.ItemMeta cm = close.getItemMeta();
        if (cm != null) {
            cm.setDisplayName(org.bukkit.ChatColor.RED + "Close");
            close.setItemMeta(cm);
        }
        inv.setItem(26, close);

        player.openInventory(inv);
    }

    public static boolean isUpgradeMenu(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (e == null) return false;
        if (e.getView() == null) return false;
        return TITLE.equals(e.getView().getTitle());
    }

    public static void onClose(org.bukkit.entity.Player p) {
        if (p != null) OPEN_SLOT.remove(p.getUniqueId());
    }

    public static void handleClick(
            org.bukkit.event.inventory.InventoryClickEvent e,
            me.pattrick.marbledrop.progression.DustManager dustManager
    ) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;

        e.setCancelled(true);

        org.bukkit.inventory.ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // Close button
        if (clicked.getType() == org.bukkit.Material.BARRIER) {
            player.closeInventory();
            return;
        }

        me.pattrick.marbledrop.marble.MarbleStat stat = statFromButton(clicked.getType());
        if (stat == null) return;

        Integer slot = OPEN_SLOT.get(player.getUniqueId());
        if (slot == null) return;

        org.bukkit.inventory.ItemStack inHand = player.getInventory().getItem(slot);
        if (inHand == null || inHand.getType().isAir() || !me.pattrick.marbledrop.marble.MarbleItem.isMarble(inHand)) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Your marble is missing.");
            player.closeInventory();
            return;
        }

        me.pattrick.marbledrop.marble.MarbleData data = me.pattrick.marbledrop.marble.MarbleItem.read(inHand);
        if (data == null) {
            player.sendMessage(org.bukkit.ChatColor.RED + "That item doesn't look like a valid marble.");
            player.closeInventory();
            return;
        }

        int current = data.getStats().get(stat);
        int cap = UpgradeRules.capFor(data.getRarity(), stat);
        int cost = UpgradeRules.costFor(data.getRarity(), stat, current);

        if (current >= cap) {
            player.sendMessage(org.bukkit.ChatColor.RED + "That stat is already capped for " + data.getRarity().name() + ".");
            return;
        }

        int dust = dustManager.getDust(player);
        if (dust < cost) {
            player.sendMessage(org.bukkit.ChatColor.RED + "You need " + cost + " dust to upgrade that. (You have " + dust + ")");
            return;
        }

        if (!dustManager.takeDust(player, cost)) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Not enough dust.");
            return;
        }

        // apply upgrade
        data.getStats().set(stat, current + 1);

        // write back
        me.pattrick.marbledrop.marble.MarbleItem.write(inHand, data);

        // refresh preview + buttons
        org.bukkit.inventory.Inventory inv = e.getInventory();
        inv.setItem(13, inHand.clone());
        inv.setItem(10, makeStatButton(data, me.pattrick.marbledrop.marble.MarbleStat.SPEED, org.bukkit.Material.FEATHER));
        inv.setItem(11, makeStatButton(data, me.pattrick.marbledrop.marble.MarbleStat.ACCEL, org.bukkit.Material.SUGAR));
        inv.setItem(12, makeStatButton(data, me.pattrick.marbledrop.marble.MarbleStat.HANDLING, org.bukkit.Material.TRIPWIRE_HOOK));
        inv.setItem(14, makeStatButton(data, me.pattrick.marbledrop.marble.MarbleStat.STABILITY, org.bukkit.Material.SHIELD));
        inv.setItem(15, makeStatButton(data, me.pattrick.marbledrop.marble.MarbleStat.BOOST, org.bukkit.Material.BLAZE_POWDER));

        // update info dust line
        org.bukkit.inventory.ItemStack info = inv.getItem(22);
        if (info != null && info.getType() == org.bukkit.Material.PAPER) {
            org.bukkit.inventory.meta.ItemMeta meta = info.getItemMeta();
            if (meta != null) {
                java.util.List<String> lore = meta.getLore();
                if (lore == null) lore = new java.util.ArrayList<>();
                // rebuild info lore cleanly
                lore.clear();
                lore.add(org.bukkit.ChatColor.GRAY + "Click a stat to upgrade.");
                lore.add(org.bukkit.ChatColor.GRAY + "Dust: " + org.bukkit.ChatColor.WHITE + dustManager.getDust(player));
                lore.add("");
                lore.add(org.bukkit.ChatColor.DARK_GRAY + "Rarity caps apply per-stat.");
                meta.setLore(lore);
                info.setItemMeta(meta);
                inv.setItem(22, info);
            }
        }

        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.8f, 1.4f);
    }

    private static me.pattrick.marbledrop.marble.MarbleStat statFromButton(org.bukkit.Material mat) {
        if (mat == null) return null;
        return switch (mat) {
            case FEATHER -> me.pattrick.marbledrop.marble.MarbleStat.SPEED;
            case SUGAR -> me.pattrick.marbledrop.marble.MarbleStat.ACCEL;
            case TRIPWIRE_HOOK -> me.pattrick.marbledrop.marble.MarbleStat.HANDLING;
            case SHIELD -> me.pattrick.marbledrop.marble.MarbleStat.STABILITY;
            case BLAZE_POWDER -> me.pattrick.marbledrop.marble.MarbleStat.BOOST;
            default -> null;
        };
    }

    private static String statDisplayName(me.pattrick.marbledrop.marble.MarbleStat stat) {
        if (stat == null) return "Stat";
        return switch (stat) {
            case SPEED -> "Speed";
            case ACCEL -> "Accel";
            case HANDLING -> "Handling";
            case STABILITY -> "Stability";
            case BOOST -> "Boost";
        };
    }

    private static org.bukkit.inventory.ItemStack makeStatButton(
            me.pattrick.marbledrop.marble.MarbleData data,
            me.pattrick.marbledrop.marble.MarbleStat stat,
            org.bukkit.Material mat
    ) {
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        int val = data.getStats().get(stat);
        int cap = UpgradeRules.capFor(data.getRarity(), stat);
        int cost = UpgradeRules.costFor(data.getRarity(), stat, val);

        meta.setDisplayName(org.bukkit.ChatColor.AQUA + statDisplayName(stat));

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(org.bukkit.ChatColor.GRAY + "Current: " + org.bukkit.ChatColor.WHITE + val);
        lore.add(org.bukkit.ChatColor.GRAY + "Cap: " + org.bukkit.ChatColor.WHITE + cap);
        lore.add("");
        lore.add(org.bukkit.ChatColor.YELLOW + "Cost: " + cost + " dust");
        lore.add(org.bukkit.ChatColor.GREEN + "Click to upgrade");
        meta.setLore(lore);

        it.setItemMeta(meta);
        return it;
    }
}
