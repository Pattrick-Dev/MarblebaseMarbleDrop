package me.pattrick.marbledrop.progression.infusion.heads;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class HeadPool {

    private final Plugin plugin;
    private final Random rng = new Random();
    private final List<HeadEntry> entries = new ArrayList<>();

    public HeadPool(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            File data = plugin.getDataFolder();
            if (!data.exists()) data.mkdirs();

            File file = new File(data, "heads.yml");
            if (!file.exists()) {
                plugin.saveResource("heads.yml", false);
            }

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            entries.clear();

            ConfigurationSection root = cfg.getConfigurationSection("heads");
            if (root == null) {
                plugin.getLogger().severe("[MarbleDrop] heads.yml missing 'heads:' root!");
                return;
            }

            for (String key : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) continue;

                String base64 = sec.getString("base64");
                String name = sec.getString("name", key);
                String team = sec.getString("team", "Unknown");

                if (base64 == null || base64.isEmpty()) {
                    plugin.getLogger().warning("[MarbleDrop] Head " + key + " missing base64, skipped.");
                    continue;
                }

                String displayName =
                        ChatColor.translateAlternateColorCodes('&', name);

                entries.add(new HeadEntry(base64, displayName, team));
            }

            plugin.getLogger().info("[MarbleDrop] Loaded " + entries.size() + " marble heads.");
        } catch (Exception ex) {
            plugin.getLogger().severe("[MarbleDrop] Failed to load heads.yml: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public boolean isLoaded() {
        return !entries.isEmpty();
    }

    public HeadEntry random() {
        if (entries.isEmpty()) return null;
        return entries.get(rng.nextInt(entries.size()));
    }

    public List<HeadEntry> all() {
        return List.copyOf(entries);
    }
}
