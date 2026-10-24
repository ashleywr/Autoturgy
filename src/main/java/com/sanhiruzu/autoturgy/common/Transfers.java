/* Sided capability transfers preserve every committed remainder.
 * Callers own returned leftovers when a source refuses rollback after simulation changed. */
package com.sanhiruzu.autoturgy.common;

import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.*;

public final class Transfers {
  public record ItemResult(int moved, List<ItemStack> leftovers) {
    public ItemResult {
      leftovers = leftovers.stream().map(ItemStack::copy).toList();
    }

    @Override
    public List<ItemStack> leftovers() {
      return leftovers.stream().map(ItemStack::copy).toList();
    }
  }

  public record FluidResult(int moved, FluidStack leftover) {
    public FluidResult {
      leftover = leftover.copy();
    }

    @Override
    public FluidStack leftover() {
      return leftover.copy();
    }
  }

  public static ItemResult moveItems(
      IItemHandler source, IItemHandler destination, StackFilters.Filter filter, int maximum) {
    if (source == destination || maximum <= 0) return new ItemResult(0, List.of());
    int moved = 0;
    List<ItemStack> custody = new ArrayList<>();
    for (int slot = 0; slot < Math.min(4096, source.getSlots()) && moved < maximum; slot++) {
      ItemStack available = source.extractItem(slot, maximum - moved, true);
      if (!StackFilters.accepts(filter, available)) continue;
      ItemStack simulatedRemainder =
          ItemHandlerHelper.insertItemStacked(destination, available.copy(), true);
      int accepted = available.getCount() - simulatedRemainder.getCount();
      if (accepted <= 0) continue;
      ItemStack extracted = source.extractItem(slot, accepted, false);
      if (extracted.isEmpty()) continue;
      if (!ItemStack.isSameItemSameComponents(available, extracted)
          || !StackFilters.accepts(filter, extracted)) {
        ItemStack remainder = source.insertItem(slot, extracted, false);
        if (!remainder.isEmpty())
          remainder = ItemHandlerHelper.insertItemStacked(source, remainder, false);
        if (!remainder.isEmpty()) custody.add(remainder);
        break;
      }
      ItemStack remainder =
          ItemHandlerHelper.insertItemStacked(destination, extracted.copy(), false);
      moved += extracted.getCount() - remainder.getCount();
      if (!remainder.isEmpty()) {
        remainder = source.insertItem(slot, remainder, false);
        if (!remainder.isEmpty())
          remainder = ItemHandlerHelper.insertItemStacked(source, remainder, false);
        if (!remainder.isEmpty()) {
          custody.add(remainder);
          break;
        }
      }
    }
    return new ItemResult(moved, custody);
  }

  public static FluidResult moveFluid(
      IFluidHandler source, IFluidHandler destination, int maximum) {
    if (source == destination || maximum <= 0) return new FluidResult(0, FluidStack.EMPTY);
    FluidStack available = source.drain(maximum, IFluidHandler.FluidAction.SIMULATE);
    if (available.isEmpty()) return new FluidResult(0, FluidStack.EMPTY);
    int accepted = destination.fill(available.copy(), IFluidHandler.FluidAction.SIMULATE);
    if (accepted <= 0) return new FluidResult(0, FluidStack.EMPTY);
    FluidStack extracted =
        source.drain(
            available.copyWithAmount(Math.min(accepted, available.getAmount())),
            IFluidHandler.FluidAction.EXECUTE);
    if (extracted.isEmpty()) return new FluidResult(0, FluidStack.EMPTY);
    int moved = destination.fill(extracted.copy(), IFluidHandler.FluidAction.EXECUTE);
    FluidStack remainder = extracted.copyWithAmount(extracted.getAmount() - moved);
    if (!remainder.isEmpty())
      remainder.shrink(source.fill(remainder.copy(), IFluidHandler.FluidAction.EXECUTE));
    return new FluidResult(moved, remainder);
  }

  private Transfers() {}
}
