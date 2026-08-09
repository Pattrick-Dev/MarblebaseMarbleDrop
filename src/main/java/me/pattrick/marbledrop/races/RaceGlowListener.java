package me.pattrick.marbledrop.races;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Toggles a glowing outline on the player's own marble for the race
 * they're currently in (see RaceGlowItem). Right-click, on air or a
 * block.
 * <p>
 * Primary path (when ProtocolLib is installed): reads the raw client
 * packet (USE_ITEM for air, USE_ITEM_ON for a block) directly, instead of
 * going through Bukkit's PlayerInteractEvent. Bukkit's own translation of
 * those packets into that event turned out to be unreliable for this
 * item specifically -- block right-clicks worked, air right-clicks
 * often silently didn't fire anything at all. Reading the packet
 * ourselves sidesteps whatever's dropping it in that translation layer,
 * since we're looking at exactly what the client actually sent rather
 * than trusting Bukkit's interpretation of it.
 * <p>
 * Fallback path (ProtocolLib absent): the original PlayerInteractEvent
 * handling. Cancelling the packet at the primary path also prevents the
 * corresponding PlayerInteractEvent from ever firing, so the two paths
 * never both fire for the same click -- safe to leave both registered
 * rather than needing to choose one.
 */
public final class RaceGlowListener implements Listener {

    private final Plugin plugin;
    private final RaceManager raceManager;
    private final RaceGlowPrivacy glowPrivacy;
    private final RaceInventoryOverlay overlay;

    public RaceGlowListener(Plugin plugin, RaceManager raceManager, RaceGlowPrivacy glowPrivacy, RaceInventoryOverlay overlay) {
        this.plugin = plugin;
        this.raceManager = raceManager;
        this.glowPrivacy = glowPrivacy;
        this.overlay = overlay;

        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            registerPacketListener();
        }
    }

    private void registerPacketListener() {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.LOW,
                PacketType.Play.Client.USE_ITEM, PacketType.Play.Client.USE_ITEM_ON) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                // Our ability items only ever live in a hotbar slot -- never
                // the off-hand -- so an off-hand interaction isn't ours,
                // whatever else might be going on with it.
                EnumWrappers.Hand hand = event.getPacket().getHands().readSafely(0);
                if (hand != null && hand != EnumWrappers.Hand.MAIN_HAND) return;

                Player player = event.getPlayer();
                if (!isHoldingGlowItem(player)) return;

                event.setCancelled(true);
                // Packet listeners run off the main thread in some cases --
                // hop back to it before touching any Bukkit state.
                Bukkit.getScheduler().runTask(plugin, () -> toggle(player));
            }
        });
    }

    /** Fallback only -- see the class javadoc. Never fires for a glow-item click when the packet listener above already handled (and cancelled) it. */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        if (!isHoldingGlowItem(e.getPlayer())) return;
        e.setCancelled(true);
        toggle(e.getPlayer());
    }

    private boolean isHoldingGlowItem(Player player) {
        int heldSlot = player.getInventory().getHeldItemSlot();
        return (overlay != null)
                ? overlay.isFakeSlot(player, heldSlot, RaceInventoryOverlay.TAG_GLOW)
                : RaceGlowItem.isGlowItem(plugin, player.getInventory().getItemInMainHand());
    }

    private void toggle(Player player) {
        MarbleRunner runner = raceManager.getMyRunner(player.getUniqueId());
        if (runner == null) {
            player.sendMessage(ChatColor.RED + "Your race has already finished -- no marble to highlight.");
            return;
        }

        boolean nowGlowing = !runner.isGlowing();
        runner.setGlowing(nowGlowing);

        if (glowPrivacy != null) {
            if (nowGlowing) {
                glowPrivacy.track(runner.getEntity(), player.getUniqueId());
            } else {
                glowPrivacy.untrack(runner.getEntity());
            }
        }

        if (overlay != null) {
            int heldSlot = player.getInventory().getHeldItemSlot();
            overlay.show(player, heldSlot, RaceInventoryOverlay.TAG_GLOW, RaceGlowItem.create(plugin, nowGlowing));
        } else {
            ItemStack item = player.getInventory().getItemInMainHand();
            RaceGlowItem.updateState(plugin, item, nowGlowing);
        }

        String visibility = (glowPrivacy != null)
                ? ""
                : ChatColor.DARK_GRAY + " (visible to everyone -- ProtocolLib not installed)";
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, nowGlowing ? 1.4f : 0.9f);
        player.sendMessage(ChatColor.AQUA + "Marble glow: " + (nowGlowing ? ChatColor.GREEN + "ON" : ChatColor.GRAY + "OFF") + visibility);
    }
}
