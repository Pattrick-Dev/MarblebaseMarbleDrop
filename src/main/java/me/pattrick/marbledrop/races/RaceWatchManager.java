package me.pattrick.marbledrop.races;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class RaceWatchManager implements Listener {

    private final Plugin plugin;
    private final TrackManager tracks;
    private final MarbleRaceEngine engine;

    // How far a spectator can stray from the marble pack before being snapped back.
    // Large enough for comfortable aerial views and side angles.
    private static final double WATCH_RADIUS = 40.0;

    // How often (in ticks) the leash anchor chases the marble pack centroid.
    private static final long ANCHOR_UPDATE_INTERVAL = 10L;

    private BukkitTask anchorTask;

    private final File file;
    private YamlConfiguration cfg;

    private record WatchState(
            Location returnLoc,
            GameMode returnMode,
            boolean returnAllowFlight,
            boolean returnFlying,
            ItemStack[] invContents,
            ItemStack[] armorContents,
            ItemStack offhand,
            int level,
            float exp,
            int totalExp,
            int food,
            float saturation,
            double health
    ) {}

    // in-memory cache for online watchers
    private final Map<UUID, WatchState> watching = new HashMap<>();
    private final Map<UUID, Location> anchor = new HashMap<>();

    public RaceWatchManager(Plugin plugin, TrackManager tracks, MarbleRaceEngine engine) {
        this.plugin = plugin;
        this.tracks = tracks;
        this.engine = engine;

        this.file = new File(plugin.getDataFolder(), "race-watch.yml");
        reloadFile();
        startAnchorTask();
    }

    private void startAnchorTask() {
        anchorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (watching.isEmpty()) return;

            Location centroid = engine.getCentroid();
            if (centroid == null) return;

            for (UUID id : watching.keySet()) {
                anchor.put(id, centroid.clone());
            }
        }, ANCHOR_UPDATE_INTERVAL, ANCHOR_UPDATE_INTERVAL);
    }

    public void shutdown() {
        if (anchorTask != null) {
            anchorTask.cancel();
            anchorTask = null;
        }
    }

    public void reloadFile() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            if (!file.exists()) file.createNewFile();
        } catch (IOException e) {
            plugin.getLogger().severe("[MarbleDrop] Failed creating race-watch.yml: " + e.getMessage());
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    private void saveFile() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[MarbleDrop] Failed saving race-watch.yml: " + e.getMessage());
        }
    }

    public boolean isWatching(Player p) {
        return p != null && watching.containsKey(p.getUniqueId());
    }

    public void start(Player p, String trackId) {
        if (p == null) return;

        if (trackId == null || trackId.isBlank()) {
            p.sendMessage(ChatColor.RED + "No track specified.");
            return;
        }

        trackId = trackId.toLowerCase();

        MarbleTrack track = tracks.getTrack(trackId);
        if (track == null || track.size() < 2) {
            p.sendMessage(ChatColor.RED + "Track not found or not enough points.");
            return;
        }

        // already watching: just snap them to this track's anchor
        if (isWatching(p)) {
            Location view = resolveWatchLocation(track);
            if (view != null) {
                anchor.put(p.getUniqueId(), view.clone());
                p.teleport(view);
                // Reassert rather than trust it's still set -- a relog,
                // death/respawn, or another plugin can quietly reset flight
                // between watch sessions, and this is the only place that
                // would ever notice if it's already marked "watching".
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.sendMessage(ChatColor.YELLOW + "Switched watch view to '" + trackId + "'.");
            }
            return;
        }

        Location view = resolveWatchLocation(track);
        if (view == null) {
            p.sendMessage(ChatColor.RED + "This track has no valid watch location yet.");
            return;
        }

        // Save to disk BEFORE clearing inventory (so logout mid-watch is safe)
        WatchState state = captureState(p);
        writeStateToDisk(p.getUniqueId(), state);
        watching.put(p.getUniqueId(), state);

        // Anchor used to leash movement
        anchor.put(p.getUniqueId(), view.clone());

        // Clear inventory for safety
        clearPlayerInventory(p);

        // Adventure + flight allowed (no noclip like spectator)
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);

        // load chunk and teleport
        if (view.getWorld() != null) {
            int cx = view.getBlockX() >> 4;
            int cz = view.getBlockZ() >> 4;
            if (!view.getWorld().isChunkLoaded(cx, cz)) view.getWorld().loadChunk(cx, cz);
        }

        p.teleport(view);

        p.sendMessage(ChatColor.GREEN + "Now watching track '" + trackId + "'.");
        p.sendMessage(ChatColor.GRAY + "Type " + ChatColor.AQUA + "/spawn" + ChatColor.GRAY + " (or rejoin later) to leave; your inventory is safe.");
    }

    public void stop(Player p, boolean teleportBack) {
        if (p == null) return;

        UUID id = p.getUniqueId();

        WatchState state = watching.remove(id);
        anchor.remove(id);

        // If not in memory (maybe they relogged), try disk
        if (state == null) {
            state = readStateFromDisk(id);
        }

        if (state == null) {
            p.sendMessage(ChatColor.RED + "You are not watching a race.");
            return;
        }

        restoreState(p, state, teleportBack);
        removeStateFromDisk(id);

        p.sendMessage(ChatColor.YELLOW + "Stopped watching.");
    }

    private Location resolveWatchLocation(MarbleTrack track) {
        Location w = track.getWatchLocation();
        if (w != null) return w;

        // fallback: 3 blocks above start point
        if (track.size() <= 0) return null;
        Location start = track.getPoint(0);
        if (start == null) return null;

        Location view = start.clone().add(0.0, 3.0, 0.0);
        view.setYaw(start.getYaw());
        view.setPitch(start.getPitch());
        return view;
    }

    private WatchState captureState(Player p) {
        ItemStack[] inv = p.getInventory().getContents();
        ItemStack[] armor = p.getInventory().getArmorContents();
        ItemStack offhand = p.getInventory().getItemInOffHand();

        int level = p.getLevel();
        float exp = p.getExp();
        int totalExp = p.getTotalExperience();

        int food = p.getFoodLevel();
        float sat = p.getSaturation();

        double health = p.getHealth();

        return new WatchState(
                p.getLocation().clone(),
                p.getGameMode(),
                p.getAllowFlight(),
                p.isFlying(),
                safeClone(inv),
                safeClone(armor),
                offhand == null ? null : offhand.clone(),
                level,
                exp,
                totalExp,
                food,
                sat,
                health
        );
    }

    private void restoreState(Player p, WatchState s, boolean teleportBack) {
        p.setGameMode(s.returnMode() == null ? GameMode.SURVIVAL : s.returnMode());
        p.setAllowFlight(s.returnAllowFlight());
        p.setFlying(s.returnFlying());

        p.getInventory().setContents(s.invContents() == null ? new ItemStack[0] : safeClone(s.invContents()));
        p.getInventory().setArmorContents(s.armorContents() == null ? new ItemStack[0] : safeClone(s.armorContents()));
        p.getInventory().setItemInOffHand(s.offhand() == null ? null : s.offhand().clone());

        p.setLevel(s.level());
        p.setExp(s.exp());
        p.setTotalExperience(s.totalExp());

        p.setFoodLevel(s.food());
        p.setSaturation(s.saturation());

        double maxHealth = 20.0;
        if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
            maxHealth = Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue();
        }
        double clampedHealth = Math.max(0.0, Math.min(maxHealth, s.health()));
        p.setHealth(clampedHealth);

        if (teleportBack && s.returnLoc() != null) {
            p.teleport(s.returnLoc());
        }
    }

    private void clearPlayerInventory(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getInventory().setItemInOffHand(null);
        p.updateInventory();
    }

    private static ItemStack[] safeClone(ItemStack[] arr) {
        if (arr == null) return null;
        ItemStack[] out = new ItemStack[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = (arr[i] == null ? null : arr[i].clone());
        }
        return out;
    }

    // ------------------------
    // Disk persistence helpers
    // ------------------------
    private void writeStateToDisk(UUID id, WatchState s) {
        if (id == null || s == null) return;

        String base = "watchers." + id;

        cfg.set(base + ".returnLoc", s.returnLoc());
        cfg.set(base + ".returnMode", s.returnMode() == null ? "SURVIVAL" : s.returnMode().name());
        cfg.set(base + ".returnAllowFlight", s.returnAllowFlight());
        cfg.set(base + ".returnFlying", s.returnFlying());

        cfg.set(base + ".inv", toList(s.invContents()));
        cfg.set(base + ".armor", toList(s.armorContents()));
        cfg.set(base + ".offhand", s.offhand());

        cfg.set(base + ".level", s.level());
        cfg.set(base + ".exp", s.exp());
        cfg.set(base + ".totalExp", s.totalExp());

        cfg.set(base + ".food", s.food());
        cfg.set(base + ".saturation", s.saturation());
        cfg.set(base + ".health", s.health());

        saveFile();
    }

    private WatchState readStateFromDisk(UUID id) {
        if (id == null) return null;

        String base = "watchers." + id;
        ConfigurationSection sec = cfg.getConfigurationSection(base);
        if (sec == null) return null;

        Location returnLoc = (Location) sec.get("returnLoc");

        String gm = sec.getString("returnMode", "SURVIVAL");
        GameMode returnMode;
        try {
            returnMode = GameMode.valueOf(gm);
        } catch (Exception ignored) {
            returnMode = GameMode.SURVIVAL;
        }

        boolean allowFlight = sec.getBoolean("returnAllowFlight", false);
        boolean flying = sec.getBoolean("returnFlying", false);

        ItemStack[] inv = fromList(sec.getList("inv"));
        ItemStack[] armor = fromList(sec.getList("armor"));
        ItemStack offhand = (ItemStack) sec.get("offhand");

        int level = sec.getInt("level", 0);
        float exp = (float) sec.getDouble("exp", 0.0);
        int totalExp = sec.getInt("totalExp", 0);

        int food = sec.getInt("food", 20);
        float sat = (float) sec.getDouble("saturation", 5.0);
        double health = sec.getDouble("health", 20.0);

        return new WatchState(
                returnLoc,
                returnMode,
                allowFlight,
                flying,
                inv,
                armor,
                offhand,
                level,
                exp,
                totalExp,
                food,
                sat,
                health
        );
    }

    private void removeStateFromDisk(UUID id) {
        if (id == null) return;
        cfg.set("watchers." + id, null);
        saveFile();
    }

    private static List<ItemStack> toList(ItemStack[] arr) {
        if (arr == null) return Collections.emptyList();
        List<ItemStack> out = new ArrayList<>(arr.length);
        for (ItemStack it : arr) out.add(it);
        return out;
    }

    private static ItemStack[] fromList(List<?> list) {
        if (list == null) return new ItemStack[0];
        ItemStack[] out = new ItemStack[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            out[i] = (o instanceof ItemStack is) ? is : null;
        }
        return out;
    }

    // ------------------------------
    // Movement leash (prevents wandering)
    // ------------------------------
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!isWatching(p)) return;

        Location a = anchor.get(p.getUniqueId());
        if (a == null) return;

        Location to = e.getTo();
        if (to == null) return;

        if (to.getWorld() == null || a.getWorld() == null || !to.getWorld().equals(a.getWorld())) {
            p.teleport(a);
            return;
        }

        if (to.distance(a) > WATCH_RADIUS) {
            Location snap = a.clone();
            snap.setYaw(to.getYaw());
            snap.setPitch(to.getPitch());
            p.teleport(snap);
            p.setFallDistance(0f);
        }
    }

    // ------------------------------
    // Cancel “SMP interference”
    // ------------------------------
    @EventHandler(ignoreCancelled = true) public void onBreak(BlockBreakEvent e) { if (isWatching(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void onPlace(BlockPlaceEvent e) { if (isWatching(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void onInteract(PlayerInteractEvent e) { if (isWatching(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void onInteractEntity(PlayerInteractEntityEvent e) { if (isWatching(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void onDrop(PlayerDropItemEvent e) { if (isWatching(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void onPickup(PlayerAttemptPickupItemEvent e) { if (isWatching(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void onSwap(PlayerSwapHandItemsEvent e) { if (isWatching(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && isWatching(p)) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void onFood(FoodLevelChangeEvent e) { if (e.getEntity() instanceof Player p && isWatching(p)) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void onInvClick(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p && isWatching(p)) e.setCancelled(true); }

    // If they log out mid-watch: DO NOT delete their state; it stays on disk for restoration.
    @EventHandler(ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        watching.remove(id);
        anchor.remove(id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onKick(PlayerKickEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        watching.remove(id);
        anchor.remove(id);
    }

    // If they join and a watch-state exists on disk, restore it immediately.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        WatchState disk = readStateFromDisk(id);
        if (disk == null) return;

        restoreState(p, disk, true);
        removeStateFromDisk(id);

        p.sendMessage(ChatColor.YELLOW + "Your inventory was restored (you logged out while watching a race).");
    }
}
