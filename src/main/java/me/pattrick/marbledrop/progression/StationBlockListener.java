package me.pattrick.marbledrop.progression;

import me.pattrick.marbledrop.progression.infusion.table.InfusionTableAmbient;
import me.pattrick.marbledrop.progression.infusion.table.InfusionTableManager;
import me.pattrick.marbledrop.progression.upgrades.UpgradeStationAmbient;
import me.pattrick.marbledrop.progression.upgrades.UpgradeStationManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

/**
 * Registers/unregisters the three station managers as their tagged
 * crafted items are placed and broken, replacing the old admin
 * add/remove-by-looking-at-an-existing-block flow.
 */
public final class StationBlockListener implements Listener {

    private final Plugin plugin;
    private final InfusionTableManager tables;
    private final InfusionTableAmbient tableAmbient;
    private final UpgradeStationManager stations;
    private final UpgradeStationAmbient stationAmbient;
    private final MarbleRecyclerManager recyclers;
    private final RecyclerAmbient recyclerAmbient;

    public StationBlockListener(
            Plugin plugin,
            InfusionTableManager tables,
            InfusionTableAmbient tableAmbient,
            UpgradeStationManager stations,
            UpgradeStationAmbient stationAmbient,
            MarbleRecyclerManager recyclers,
            RecyclerAmbient recyclerAmbient
    ) {
        this.plugin = plugin;
        this.tables = tables;
        this.tableAmbient = tableAmbient;
        this.stations = stations;
        this.stationAmbient = stationAmbient;
        this.recyclers = recyclers;
        this.recyclerAmbient = recyclerAmbient;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        StationType type = StationItems.readType(plugin, e.getItemInHand());
        if (type == null) return;

        Location loc = e.getBlockPlaced().getLocation();
        Player player = e.getPlayer();
        boolean registered = switch (type) {
            case INFUSION_TABLE -> tables.addTable(loc);
            case UPGRADE_STATION -> stations.addStation(loc);
            case RECYCLER -> recyclers.addRecycler(loc);
        };

        if (registered) {
            player.sendMessage(ChatColor.GREEN + "Placed a new " + type.displayName() + ChatColor.GREEN + "!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        Location loc = block.getLocation();
        Material t = block.getType();

        StationType type;
        if (t == Material.CAULDRON && tables.isTable(loc)) {
            type = StationType.INFUSION_TABLE;
        } else if (t == Material.SMITHING_TABLE && stations.isStation(block)) {
            type = StationType.UPGRADE_STATION;
        } else if (t == Material.GRINDSTONE && recyclers.isRecycler(loc)) {
            type = StationType.RECYCLER;
        } else {
            return;
        }

        switch (type) {
            case INFUSION_TABLE -> {
                tables.removeTable(loc);
                if (tableAmbient != null) tableAmbient.removeTable(loc);
            }
            case UPGRADE_STATION -> {
                stations.removeStation(loc);
                if (stationAmbient != null) stationAmbient.removeStation(loc);
            }
            case RECYCLER -> {
                recyclers.removeRecycler(loc);
                if (recyclerAmbient != null) recyclerAmbient.removeRecycler(loc);
            }
        }

        e.setDropItems(false);
        block.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), StationItems.create(plugin, type));
    }
}
