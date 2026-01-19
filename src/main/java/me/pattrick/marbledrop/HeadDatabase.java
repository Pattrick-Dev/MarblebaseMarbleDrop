package me.pattrick.marbledrop;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.Arrays;
import java.time.LocalDate;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class HeadDatabase {
    public static ItemStack getMarbleHead(String user) throws IOException {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        final ItemMeta meta = head.getItemMeta();
        final ArrayList<String> sample = Sampler.main();
        meta.setLore(Arrays.asList("§7Team: " + sample.get(2), " ", "§7Found by: " + user, "§7On: " + LocalDate.now(), " ", "§8Marblebase Marble"));
        final GameProfile profile = new GameProfile(UUID.randomUUID(), "");
        meta.setDisplayName((String)sample.get(1));
        profile.getProperties().put("textures", new Property("textures", sample.get(0)));
        Field profileField = null;
        try {
            profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        }
        catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {

            e.printStackTrace();
        }
        head.setItemMeta(meta);
        return head;
    }
}