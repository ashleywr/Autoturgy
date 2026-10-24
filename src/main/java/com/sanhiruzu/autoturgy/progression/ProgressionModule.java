/* Registers original ingredients used by host research and magical recipes.
 * Recipe and research JSON remain authoritative for unlocking and paid crafting. */
package com.sanhiruzu.autoturgy.progression;

import com.sanhiruzu.autoturgy.Content;
import com.sanhiruzu.autoturgy.sensing.GuideItem;
import java.util.*;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ProgressionModule {
  public static final Map<String, DeferredItem<Item>> INGREDIENTS = new LinkedHashMap<>();
  public static final net.neoforged.neoforge.registries.DeferredRegister<
          net.minecraft.world.item.crafting.RecipeSerializer<?>>
      SERIALIZERS =
          net.neoforged.neoforge.registries.DeferredRegister.create(
              net.minecraft.core.registries.Registries.RECIPE_SERIALIZER, "autoturgy");
  public static final net.neoforged.neoforge.registries.DeferredHolder<
          net.minecraft.world.item.crafting.RecipeSerializer<?>,
          net.minecraft.world.item.crafting.RecipeSerializer<MilkBucketConversionRecipe>>
      MILK_CONVERSION =
          SERIALIZERS.register(
              "milk_bucket_conversion",
              () ->
                  new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(
                      MilkBucketConversionRecipe::new));

  public static void register(net.neoforged.bus.api.IEventBus bus) {
    SERIALIZERS.register(bus);
    for (String primal : List.of("aer", "terra", "ignis", "aqua", "ordo", "perditio")) {
      ingredient(primal + "_sliver");
      ingredient(primal + "_crystalline_brain");
    }
    for (String name :
        List.of(
            "enchanted_paper",
            "bound_paper",
            "aqueous_admixture",
            "conflux_disc",
            "mercatus_amalgam",
            "ember_heart")) ingredient(name);
    for (int i = 17; i <= 22; i++) GuideItem.PAGES.add("guide.autoturgy." + i);
  }

  private static void ingredient(String name) {
    INGREDIENTS.put(name, Content.item(name, () -> new Item(new Item.Properties())));
  }

  private ProgressionModule() {}
}
