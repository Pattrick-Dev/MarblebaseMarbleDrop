package me.pattrick.marbledrop.marble;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.pattrick.marbledrop.command.Commands;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Lets a marble be placed as a real player-head block (floor or wall) so
 * it can be displayed, instead of the earlier blanket "marbles can never
 * be placed" rule (see ListenEvents, which used to cancel this
 * unconditionally).
 * <p>
 * Placing an item as a block does NOT carry over its custom
 * PersistentDataContainer data on its own -- only vanilla's own skin/
 * texture transfers automatically. On place, this copies the marble's
 * full PDC (stats, rarity, level, XP, ...) onto the new block's own PDC
 * (a Skull is a TileState, which is a PersistentDataHolder) via
 * MarbleItem#writeToContainer, plus who placed it. On break, it reads
 * that data back, reconstructs an exact marble ItemStack (PDC + the
 * skull's own texture profile, since a manually-dropped item needs both
 * re-applied), and drops that instead of vanilla's blank head drop.
 * <p>
 * Only the placer (or a marbledrop.admin) can break a placed marble back
 * -- otherwise it's cancelled. Without this, anyone could walk up and
 * break-and-pocket someone else's displayed marble.
 * <p>
 * Also protected from anything that would destroy or move it without
 * going through the owner-checked onBreak path above: explosions
 * (TNT/creepers via EntityExplodeEvent, beds/respawn anchors via
 * BlockExplodeEvent -- filtered out of the event's block list so the rest
 * of the explosion still happens normally) and piston push/pull
 * (cancelled outright, since a piston can't selectively skip one block
 * out of the row it's moving).
 */
public final class MarblePlacementListener implements Listener {

    private final PlacedMarbleManager marbles;
    private final MarbleDisplayAmbient ambient;

    public MarblePlacementListener(PlacedMarbleManager marbles, MarbleDisplayAmbient ambient) {
        this.marbles = marbles;
        this.ambient = ambient;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material placedType = event.getBlock().getType();
        if (placedType != Material.PLAYER_HEAD && placedType != Material.PLAYER_WALL_HEAD) return;

        ItemStack used = event.getItemInHand();
        MarbleData data = MarbleItem.read(used);
        if (data == null) return; // a normal head, not a marble -- leave it alone

        BlockState state = event.getBlock().getState();
        if (!(state instanceof Skull skull)) return;

        MarbleItem.writeToContainer(skull.getPersistentDataContainer(), data);
        skull.getPersistentDataContainer().set(
                MarbleKeys.PLACED_BY, PersistentDataType.STRING, event.getPlayer().getUniqueId().toString());

        // Display name rides along on the ItemStack forever normally --
        // MarbleItem#write() only ever touches PDC + lore -- but placing
        // as a block drops it like every other un-PDC'd ItemMeta field,
        // so it has to be snapshotted here to survive the round trip.
        ItemMeta usedMeta = used.getItemMeta();
        if (usedMeta != null && usedMeta.hasDisplayName()) {
            skull.getPersistentDataContainer().set(
                    MarbleKeys.PLACED_NAME, PersistentDataType.STRING, usedMeta.getDisplayName());
        }

        skull.update();

        marbles.addMarble(event.getBlock().getLocation());

        // Creative mode never consumes the item being placed (vanilla's
        // own behavior, since creative inventories are meant to be
        // infinite) -- for a one-of-a-kind marble that would leave a
        // duplicate sitting in hand right next to the one now on
        // display, so it's removed by hand here regardless of gamemode.
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            consumeOne(event.getPlayer(), event.getHand());
        }

        String name = (usedMeta != null && usedMeta.hasDisplayName())
                ? usedMeta.getDisplayName()
                : MarbleItem.rarityColor(data.getRarity()) + "Marble";
        event.getPlayer().sendMessage(name + ChatColor.GREEN + " placed.");
    }

    private void consumeOne(Player player, EquipmentSlot hand) {
        boolean offHand = hand == EquipmentSlot.OFF_HAND;
        ItemStack held = offHand ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) return;

        int remaining = held.getAmount() - 1;
        ItemStack updated = remaining > 0 ? held : null;
        if (updated != null) updated.setAmount(remaining);

        if (offHand) {
            player.getInventory().setItemInOffHand(updated);
        } else {
            player.getInventory().setItemInMainHand(updated);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) return;

        BlockState state = block.getState();
        if (!(state instanceof Skull skull)) return;

        MarbleData data = MarbleItem.readFromContainer(skull.getPersistentDataContainer());
        if (data == null) return; // a normal head, not a marble display -- leave it alone

        Player breaker = event.getPlayer();
        String placedByStr = skull.getPersistentDataContainer().get(MarbleKeys.PLACED_BY, PersistentDataType.STRING);
        UUID placedBy = placedByStr == null ? null : UUID.fromString(placedByStr);

        boolean isOwner = placedBy != null && placedBy.equals(breaker.getUniqueId());
        if (!isOwner && !breaker.hasPermission(Commands.ADMIN)) {
            event.setCancelled(true);
            String ownerName = placedBy == null ? null : Bukkit.getOfflinePlayer(placedBy).getName();
            breaker.sendMessage(ChatColor.RED + "Only "
                    + (ownerName != null ? ownerName : "its owner") + " can break this marble display.");
            return;
        }

        event.setDropItems(false);
        ItemStack marble = rebuildMarbleItem(skull, data);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), marble);

        marbles.removeMarble(block.getLocation());
        ambient.removeMarker(block.getLocation());
    }

    /** TNT, creepers, etc. -- pulls placed marbles out of the destroyed-block list so the rest of the explosion still happens, they just survive. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isPlacedMarble);
    }

    /** Beds/respawn anchors exploding out of season, etc. -- same treatment as onEntityExplode. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isPlacedMarble);
    }

    /** A piston can't selectively skip one block out of the row it's pushing, so if any block in the move is a placed marble, the whole extension is cancelled. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (isPlacedMarble(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Same as onPistonExtend, for sticky pistons pulling a block back. getBlocks() is empty for non-sticky retracts, so this is a no-op for those. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (isPlacedMarble(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isPlacedMarble(Block block) {
        Material t = block.getType();
        if (t != Material.PLAYER_HEAD && t != Material.PLAYER_WALL_HEAD) return false;
        if (!(block.getState() instanceof Skull skull)) return false;
        return MarbleItem.readFromContainer(skull.getPersistentDataContainer()) != null;
    }

    /** Reconstructs the exact marble item a placed skull represents: the skull's own texture, its marble PDC/lore reapplied via MarbleItem, and its snapshotted display name. */
    private ItemStack rebuildMarbleItem(Skull skull, MarbleData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);

        PlayerProfile profile = skull.getPlayerProfile();
        if (profile != null && item.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setPlayerProfile(profile);
            item.setItemMeta(skullMeta);
        } else {
            OfflinePlayer owningPlayer = skull.getOwningPlayer();
            if (owningPlayer != null && item.getItemMeta() instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(owningPlayer);
                item.setItemMeta(skullMeta);
            }
        }

        MarbleItem.write(item, data);

        String placedName = skull.getPersistentDataContainer().get(MarbleKeys.PLACED_NAME, PersistentDataType.STRING);
        if (placedName != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(placedName);
                item.setItemMeta(meta);
            }
        }

        return item;
    }
}
