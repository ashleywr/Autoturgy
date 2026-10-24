/* Changes only a milk bucket's pouring behavior, preserving its fluid and container.
 * Explicit empty remainders prevent the vanilla milk crafting remainder from duplicating buckets. */
package com.sanhiruzu.autoturgy.progression;

import com.sanhiruzu.autoturgy.fluids.FluidModule;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class MilkBucketConversionRecipe extends CustomRecipe {
  public MilkBucketConversionRecipe(CraftingBookCategory category) {
    super(category);
  }

  public boolean matches(CraftingInput input, Level level) {
    int found = 0;
    for (ItemStack s : input.items()) {
      if (s.isEmpty()) continue;
      if (!(s.is(Items.MILK_BUCKET) || s.is(FluidModule.MILK_BUCKET.get())) || ++found > 1)
        return false;
    }
    return found == 1;
  }

  public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
    for (ItemStack s : input.items())
      if (!s.isEmpty())
        return new ItemStack(
            s.is(Items.MILK_BUCKET) ? FluidModule.MILK_BUCKET.get() : Items.MILK_BUCKET);
    return ItemStack.EMPTY;
  }

  public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
    return NonNullList.withSize(input.size(), ItemStack.EMPTY);
  }

  public boolean canCraftInDimensions(int width, int height) {
    return width * height > 0;
  }

  public RecipeSerializer<?> getSerializer() {
    return ProgressionModule.MILK_CONVERSION.get();
  }
}
