package me.pattrick.marbledrop;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class CooldownManager {
  static File dataFolder = Bukkit.getPluginManager().getPlugin("MarbleBaseMD").getDataFolder();
  
  static File filePath = new File(dataFolder, "cooldowns.yml");
  
  static FileConfiguration config = YamlConfiguration.loadConfiguration(filePath);
  
  static String cooldown = config.getString("cooldown");
  
  public static void CooldownCheckDisable() throws IOException {
    for (Map.Entry<UUID, Long> entry : ListenEvents.cooldown.entrySet())
      config.set("cooldown." + entry.getKey(), entry.getValue()); 
    config.save(filePath);
  }
  
  public static void CooldownCheckEnable() {
	if (config.getConfigurationSection("cooldown") == null) {
		
		return;
	}else { 
    config.getConfigurationSection("cooldown").getKeys(false).forEach(key -> {
          Long content = (Long) config.get("cooldown." + key);
          ListenEvents.cooldown.put(UUID.fromString(key), content);
        });
  }
  }
}
