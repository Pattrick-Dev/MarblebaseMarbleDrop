package me.pattrick.marbledrop.progression;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * Crafting recipes for the three station items. All ingredients are
 * plain vanilla materials (no dust/marbles) so a fresh server always has
 * a path to the first infusion table without depending on progression
 * systems that themselves need one.
 */
public final class StationRecipes {

    private StationRecipes() {}

    /** Single source of truth for each station's recipe key -- also used by TutorialCraftGui to look the recipe back up. */
    public static NamespacedKey keyFor(Plugin plugin, StationType type) {
        String name = switch (type) {
            case INFUSION_TABLE -> "infusion_table";
            case UPGRADE_STATION -> "upgrade_station";
            case RECYCLER -> "recycler";
        };
        return new NamespacedKey(plugin, name);
    }

    public static void registerAll(Plugin plugin) {
        register(plugin, StationType.INFUSION_TABLE,
                new String[]{"AGA", "GCG", "AGA"},
                'A', Material.AMETHYST_SHARD,
                'G', Material.GLOWSTONE,
                'C', Material.CAULDRON);

        register(plugin, StationType.UPGRADE_STATION,
                new String[]{"IDI", "DSD", "IDI"},
                'I', Material.IRON_INGOT,
                'D', Material.DIAMOND,
                'S', Material.SMITHING_TABLE);

        register(plugin, StationType.RECYCLER,
                new String[]{"IRI", "RGR", "IRI"},
                'I', Material.IRON_INGOT,
                'R', Material.REDSTONE,
                'G', Material.GRINDSTONE);
    }

    /**
     * The registered recipe's 3x3 grid as icons (row-major, index = row*3+col,
     * null where the shape has no ingredient in that cell). Reads back the
     * actual live ShapedRecipe rather than duplicating the layout, so any
     * consumer (the tutorial's preview GUI, chat text, or item frames) can
     * never drift out of sync with what registerAll() above actually set up.
     */
    public static ItemStack[] shapeIcons(Plugin plugin, StationType type) {
        ItemStack[] icons = new ItemStack[9];

        Recipe recipe = Bukkit.getRecipe(keyFor(plugin, type));
        if (!(recipe instanceof ShapedRecipe shaped)) return icons;

        String[] shape = shaped.getShape();
        Map<Character, RecipeChoice> choices = shaped.getChoiceMap();

        for (int row = 0; row < shape.length; row++) {
            String line = shape[row];
            for (int col = 0; col < line.length(); col++) {
                RecipeChoice choice = choices.get(line.charAt(col));
                if (choice == null) continue;

                ItemStack icon = choice.getItemStack().clone();
                icon.setAmount(1);
                icons[row * 3 + col] = icon;
            }
        }
        return icons;
    }

    private static void register(Plugin plugin, StationType type, String[] shape, Object... ingredients) {
        NamespacedKey key = keyFor(plugin, type);

        // Guard against "duplicate recipe key" on a plugin re-enable without a full server restart.
        plugin.getServer().removeRecipe(key);

        ShapedRecipe recipe = new ShapedRecipe(key, StationItems.create(plugin, type));
        recipe.shape(shape);
        for (int i = 0; i < ingredients.length; i += 2) {
            recipe.setIngredient((Character) ingredients[i], (Material) ingredients[i + 1]);
        }

        plugin.getServer().addRecipe(recipe);
    }
}
