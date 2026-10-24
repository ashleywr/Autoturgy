/* Paid focus infusions preserve existing upgrades and physical stored contents.
 * Delegation keeps the installed host's matching, research and essentia contracts. */
package com.sanhiruzu.autoturgy.equipment;

import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import com.leclowndu93150.thaumaturge.content.infusion.*;
import com.mojang.serialization.MapCodec;
import com.sanhiruzu.autoturgy.progression.ProgressionModule;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;

public record FocusUpgradeRecipe(InfusionRecipe delegate) implements InfusionJobRecipe {
  public static final MapCodec<FocusUpgradeRecipe> CODEC =
      InfusionRecipe.MAP_CODEC.xmap(FocusUpgradeRecipe::new, FocusUpgradeRecipe::delegate);
  public static final StreamCodec<RegistryFriendlyByteBuf, FocusUpgradeRecipe> STREAM_CODEC =
      InfusionRecipe.STREAM_CODEC.map(FocusUpgradeRecipe::new, FocusUpgradeRecipe::delegate);
  public static DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FocusUpgradeRecipe>>
      SERIALIZER;

  public static void register() {
    SERIALIZER =
        ProgressionModule.SERIALIZERS.register(
            "focus_upgrade",
            () ->
                new RecipeSerializer<FocusUpgradeRecipe>() {
                  public MapCodec<FocusUpgradeRecipe> codec() {
                    return CODEC;
                  }

                  public StreamCodec<RegistryFriendlyByteBuf, FocusUpgradeRecipe> streamCodec() {
                    return STREAM_CODEC;
                  }
                });
  }

  public boolean matches(InfusionInput input, Level level) {
    if (!delegate.matches(input, level)) return false;
    ItemStack changed = delegate.assemble(input, level.registryAccess());
    for (String key : List.of("Gratis", "Storage", "Transposing"))
      if (CraftingFocus.upgrade(changed, key) && !CraftingFocus.upgrade(input.catalyst(), key))
        return true;
    return false;
  }

  public ItemStack assemble(InfusionInput input, HolderLookup.Provider registry) {
    ItemStack result = delegate.assemble(input, registry);
    var flags = CraftingFocus.flags(input.catalyst());
    for (String key : List.of("Gratis", "Storage", "Transposing"))
      if (CraftingFocus.upgrade(result, key)) flags.putBoolean(key, true);
    result.set(DataComponents.CUSTOM_DATA, CustomData.of(flags));
    return result;
  }

  public Ingredient catalyst() {
    return delegate.catalyst();
  }

  public List<Ingredient> components() {
    return delegate.components();
  }

  public AspectList aspects() {
    return delegate.aspects();
  }

  public int instability() {
    return delegate.instability();
  }

  public ItemStack resultItem() {
    return delegate.resultItem();
  }

  public Optional<ResearchGate> researchGate() {
    return delegate.researchGate();
  }

  public boolean canCraftInDimensions(int width, int height) {
    return delegate.canCraftInDimensions(width, height);
  }

  public ItemStack getResultItem(HolderLookup.Provider registry) {
    return delegate.getResultItem(registry);
  }

  public RecipeSerializer<?> getSerializer() {
    return SERIALIZER.get();
  }

  public RecipeType<?> getType() {
    return delegate.getType();
  }
}
