package me.pattrick.marbledrop.progression.upgrades;

public final class UpgradeMenuListener implements org.bukkit.event.Listener {

    private final me.pattrick.marbledrop.progression.DustManager dust;

    public UpgradeMenuListener(me.pattrick.marbledrop.progression.DustManager dust) {
        this.dust = dust;
    }

    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player)) return;
        if (!UpgradeMenu.isUpgradeMenu(e)) return;

        UpgradeMenu.handleClick(e, dust);
    }

    @org.bukkit.event.EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof org.bukkit.entity.Player p)) return;
        if (!UpgradeMenu.TITLE.equals(e.getView().getTitle())) return;

        UpgradeMenu.onClose(p);
    }
}
