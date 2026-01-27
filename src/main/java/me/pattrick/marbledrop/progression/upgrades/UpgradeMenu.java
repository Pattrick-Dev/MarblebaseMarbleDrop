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

        if (!me.pattrick.marbledrop.marble.MarbleItem.isMarble(held)) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Hold a marble in your main hand to upgrade it.");
            return;
        }

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
        org.bukkit.inventory.ItemStack preview = held.clone();
        inv.setItem(13, preview);

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
            lore.add(org.bukkit.ChatColor.GRAY + "Rarity caps prevent maxing commons.");
            lore.add("");
            lore.add(org.bukkit.ChatColor.DARK_GRAY + "Station: Upgrade Table");
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
        String title = e.getView().getTitle();
        return TITLE.equals(title);
    }

    public static void onClose(org.bukkit.entity.Player p) {
        if (p != null) OPEN_SLOT.remove(p.getUniqueId());
    }

    public static void handleClick(
            org.bukkit.event.inventory.InventoryClickEvent e,
            me.pattrick.marbledrop.progression.DustManager dustManager
    ) {
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) e.getWhoClicked();

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
        if (slot == null) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Upgrade session expired. Re-open the station.");
            player.closeInventory();
            return;
        }

        org.bukkit.inventory.ItemStack held = player.getInventory().getItem(slot);
        if (!me.pattrick.marbledrop.marble.MarbleItem.isMarble(held)) {
            player.sendMessage(org.bukkit.ChatColor.RED + "You must still be holding the marble you want to upgrade.");
            player.closeInventory();
            return;
        }

        me.pattrick.marbledrop.marble.MarbleData data = me.pattrick.marbledrop.marble.MarbleItem.read(held);
        if (data == null) {
            player.sendMessage(org.bukkit.ChatColor.RED + "That marble couldn't be read.");
            player.closeInventory();
            return;
        }

        int current = data.getStats().get(stat);
        int cap = capFor(data.getRarity());
        if (current >= cap) {
            player.sendMessage(org.bukkit.ChatColor.RED + "That stat is at the cap for " + data.getRarity().name() + " (" + cap + ").");
            return;
        }

        int cost = costFor(data.getRarity(), current);

        // Charge dust
        if (!dustManager.takeDust(player, cost)) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Not enough Marble Dust. Cost: " + cost);
            return;
        }

        // Build updated stats
        int speed = data.getStats().get(me.pattrick.marbledrop.marble.MarbleStat.SPEED);
        int accel = data.getStats().get(me.pattrick.marbledrop.marble.MarbleStat.ACCEL);
        int handling = data.getStats().get(me.pattrick.marbledrop.marble.MarbleStat.HANDLING);
        int stability = data.getStats().get(me.pattrick.marbledrop.marble.MarbleStat.STABILITY);
        int boost = data.getStats().get(me.pattrick.marbledrop.marble.MarbleStat.BOOST);

        switch (stat) {
            case SPEED -> speed++;
            case ACCEL -> accel++;
            case HANDLING -> handling++;
            case STABILITY -> stability++;
            case BOOST -> boost++;
        }

        me.pattrick.marbledrop.marble.MarbleStats newStats =
                new me.pattrick.marbledrop.marble.MarbleStats(speed, accel, handling, stability, boost);

        // Create new MarbleData preserving everything else
        me.pattrick.marbledrop.marble.MarbleData updated =
                new me.pattrick.marbledrop.marble.MarbleData(
                        data.getId(),
                        data.getMarbleKey(),
                        data.getTeamKey(),
                        data.getRarity(),
                        newStats,
                        data.getFoundBy(),
                        data.getCreatedAt(),
                        data.getXp(),
                        data.getLevel()
                );

        // Write back to the SAME item
        me.pattrick.marbledrop.marble.MarbleItem.write(held, updated);

        // Refresh lore so players see it on hover
        applyUpgradeLore(held, updated);

        // Ensure it updates in hand
        player.getInventory().setItem(slot, held);

        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.6f, 1.6f);
        player.sendMessage(org.bukkit.ChatColor.GREEN + "Upgraded " + prettyStat(stat) + " to " + (current + 1) + " (Cost: " + cost + " dust)");

        // Re-open / refresh GUI contents
        open(player, dustManager);
    }

    private static org.bukkit.inventory.ItemStack makeStatButton(me.pattrick.marbledrop.marble.MarbleData data,
                                                                 me.pattrick.marbledrop.marble.MarbleStat stat,
                                                                 org.bukkit.Material mat) {
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            int cur = data.getStats().get(stat);
            int cap = capFor(data.getRarity());
            int cost = costFor(data.getRarity(), cur);

            meta.setDisplayName(org.bukkit.ChatColor.YELLOW + prettyStat(stat));
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(org.bukkit.ChatColor.GRAY + "Current: " + org.bukkit.ChatColor.WHITE + cur);
            lore.add(org.bukkit.ChatColor.GRAY + "Cap: " + org.bukkit.ChatColor.WHITE + cap);
            lore.add("");
            lore.add(org.bukkit.ChatColor.GRAY + "Cost: " + org.bukkit.ChatColor.GOLD + cost + " dust");
            lore.add(org.bukkit.ChatColor.DARK_GRAY + "Click to upgrade +1");
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static me.pattrick.marbledrop.marble.MarbleStat statFromButton(org.bukkit.Material mat) {
        if (mat == org.bukkit.Material.FEATHER) return me.pattrick.marbledrop.marble.MarbleStat.SPEED;
        if (mat == org.bukkit.Material.SUGAR) return me.pattrick.marbledrop.marble.MarbleStat.ACCEL;
        if (mat == org.bukkit.Material.TRIPWIRE_HOOK) return me.pattrick.marbledrop.marble.MarbleStat.HANDLING;
        if (mat == org.bukkit.Material.SHIELD) return me.pattrick.marbledrop.marble.MarbleStat.STABILITY;
        if (mat == org.bukkit.Material.BLAZE_POWDER) return me.pattrick.marbledrop.marble.MarbleStat.BOOST;
        return null;
    }

    private static int capFor(me.pattrick.marbledrop.marble.MarbleRarity r) {
        return switch (r) {
            case COMMON -> 6;
            case UNCOMMON -> 8;
            case RARE -> 10;
            case EPIC -> 12;
            case LEGENDARY -> 14;
        };
    }

    private static int costFor(me.pattrick.marbledrop.marble.MarbleRarity r, int currentValue) {
        int base = switch (r) {
            case COMMON -> 25;
            case UNCOMMON -> 40;
            case RARE -> 65;
            case EPIC -> 95;
            case LEGENDARY -> 140;
        };
        return base + (currentValue * 10);
    }

    private static String prettyStat(me.pattrick.marbledrop.marble.MarbleStat s) {
        return switch (s) {
            case SPEED -> "Speed";
            case ACCEL -> "Accel";
            case HANDLING -> "Handling";
            case STABILITY -> "Stability";
            case BOOST -> "Boost";
        };
    }

    private static org.bukkit.ChatColor rarityColor(me.pattrick.marbledrop.marble.MarbleRarity r) {
        return switch (r) {
            case COMMON -> org.bukkit.ChatColor.WHITE;
            case UNCOMMON -> org.bukkit.ChatColor.GREEN;
            case RARE -> org.bukkit.ChatColor.AQUA;
            case EPIC -> org.bukkit.ChatColor.LIGHT_PURPLE;
            case LEGENDARY -> org.bukkit.ChatColor.GOLD;
        };
    }

    private static void applyUpgradeLore(org.bukkit.inventory.ItemStack item, me.pattrick.marbledrop.marble.MarbleData data) {
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        java.util.List<String> lore = new java.util.ArrayList<>();

        lore.add(org.bukkit.ChatColor.GRAY + "Team: " + org.bukkit.ChatColor.WHITE + data.getTeamKey());
        lore.add(org.bukkit.ChatColor.GRAY + "Rarity: " + rarityColor(data.getRarity()) + data.getRarity().name());
        lore.add("");

        lore.add(org.bukkit.ChatColor.GRAY + "Speed: " + org.bukkit.ChatColor.WHITE + data.getStats().get(me.pattrick.marbledrop.marble.MarbleStat.SPEED));
        lore.add(org.bukkit.ChatColor.GRAY + "Accel: " + org.bukkit.ChatColor.WHITE + data.getStats().get(me.pattrick.marbledrop.marble.MarbleStat.ACCEL));
        lore.add(org.bukkit.ChatColor.GRAY + "Handling: " + org.bukkit.ChatColor.WHITE + data.getStats().get(me.pattrick.marbledrop.marble.MarbleStat.HANDLING));
        lore.add(org.bukkit.ChatColor.GRAY + "Stability: " + org.bukkit.ChatColor.WHITE + data.getStats().get(me.pattrick.marbledrop.marble.MarbleStat.STABILITY));
        lore.add(org.bukkit.ChatColor.GRAY + "Boost: " + org.bukkit.ChatColor.WHITE + data.getStats().get(me.pattrick.marbledrop.marble.MarbleStat.BOOST));

        meta.setLore(lore);
        item.setItemMeta(meta);
    }
}
