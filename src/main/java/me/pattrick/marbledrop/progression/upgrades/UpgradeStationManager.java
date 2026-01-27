package me.pattrick.marbledrop.progression.upgrades;

public final class UpgradeStationManager {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final java.io.File file;
    private org.bukkit.configuration.file.FileConfiguration cfg;

    // store as "world,x,y,z"
    private final java.util.Set<String> stations = new java.util.HashSet<>();

    public UpgradeStationManager(org.bukkit.plugin.java.JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new java.io.File(plugin.getDataFolder(), "upgrade_stations.yml");
        ensureFile();
        reload();
    }

    private void ensureFile() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            if (!file.exists()) file.createNewFile();
        } catch (Exception e) {
            plugin.getLogger().warning("[Upgrades] Could not create upgrade_stations.yml: " + e.getMessage());
        }
    }

    public void reload() {
        this.cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        stations.clear();

        java.util.List<String> list = cfg.getStringList("stations");
        if (list != null) stations.addAll(list);
    }

    public void save() {
        try {
            cfg.set("stations", new java.util.ArrayList<>(stations));
            cfg.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[Upgrades] Could not save upgrade_stations.yml: " + e.getMessage());
        }
    }

    public java.util.Set<String> getStations() {
        return java.util.Collections.unmodifiableSet(stations);
    }

    public boolean isStation(org.bukkit.block.Block b) {
        if (b == null || b.getWorld() == null) return false;
        return stations.contains(keyOf(b.getLocation()));
    }

    public boolean addStation(org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        boolean added = stations.add(keyOf(loc));
        if (added) save();
        return added;
    }

    public boolean removeStation(org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        boolean removed = stations.remove(keyOf(loc));
        if (removed) save();
        return removed;
    }

    private String keyOf(org.bukkit.Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
