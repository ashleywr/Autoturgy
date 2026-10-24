/* Immutable ghost samples specify matching and requested quantities.
 * Samples never own inventory items, and conversion changes only polarity. */
package com.sanhiruzu.autoturgy.common;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

public final class StackFilters {
  public record MatchOptions(boolean components, boolean damage, boolean tags, boolean namespace) {
    public MatchOptions(boolean components, boolean namespace) {
      this(components, false, false, namespace);
    }
  }

  public record Sample(ItemStack template, int quantity) {
    public Sample {
      template = template.copyWithCount(1);
      quantity = Math.clamp(quantity, 1, 4096);
    }

    @Override
    public ItemStack template() {
      return template.copy();
    }
  }

  public record Filter(List<Sample> samples, boolean blacklist, MatchOptions options) {
    public Filter {
      samples = List.copyOf(samples);
    }

    public Filter converted() {
      return new Filter(samples, !blacklist, options);
    }

    public static Filter all() {
      return new Filter(List.of(), true, new MatchOptions(false, false));
    }
  }

  public static boolean matches(MatchOptions options, ItemStack a, ItemStack b) {
    if (a.isEmpty() || b.isEmpty()) return false;
    if (options.namespace())
      return BuiltInRegistries.ITEM
          .getKey(a.getItem())
          .getNamespace()
          .equals(BuiltInRegistries.ITEM.getKey(b.getItem()).getNamespace());
    boolean identity = ItemStack.isSameItem(a, b);
    if (options.tags()) identity |= a.getTags().anyMatch(b::is);
    if (!identity || options.damage() && a.getDamageValue() != b.getDamageValue()) return false;
    if (!options.components()) return true;
    ItemStack left = a.copy(), right = b.copy();
    if (!options.damage()) {
      left.remove(net.minecraft.core.component.DataComponents.DAMAGE);
      right.remove(net.minecraft.core.component.DataComponents.DAMAGE);
    }
    return left.getComponents().equals(right.getComponents());
  }

  public static boolean accepts(Filter filter, ItemStack stack) {
    boolean any =
        filter.samples().stream()
            .anyMatch(sample -> matches(filter.options(), sample.template(), stack));
    return !stack.isEmpty() && (filter.blacklist() != any);
  }

  public static int configuredQuantity(Filter filter, ItemStack stack) {
    return filter.samples().stream()
        .filter(s -> matches(filter.options(), s.template(), stack))
        .mapToInt(Sample::quantity)
        .sum();
  }

  public static CompoundTag save(Filter filter, HolderLookup.Provider lookup) {
    CompoundTag tag = new CompoundTag();
    tag.putBoolean("blacklist", filter.blacklist());
    tag.putBoolean("components", filter.options().components());
    tag.putBoolean("namespace", filter.options().namespace());
    tag.putBoolean("damage", filter.options().damage());
    tag.putBoolean("tags", filter.options().tags());
    ListTag samples = new ListTag();
    for (Sample sample : filter.samples()) {
      CompoundTag row = new CompoundTag();
      row.put("item", sample.template().save(lookup));
      row.putInt("quantity", sample.quantity());
      samples.add(row);
    }
    tag.put("samples", samples);
    return tag;
  }

  public static Filter load(CompoundTag tag, HolderLookup.Provider lookup) {
    List<Sample> result = new ArrayList<>();
    ListTag list = tag.getList("samples", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(9, list.size()); i++) {
      CompoundTag row = list.getCompound(i);
      ItemStack stack = ItemStack.parseOptional(lookup, row.getCompound("item"));
      if (!stack.isEmpty()) result.add(new Sample(stack, row.getInt("quantity")));
    }
    return new Filter(
        result,
        tag.getBoolean("blacklist"),
        new MatchOptions(
            tag.getBoolean("components"),
            tag.getBoolean("damage"),
            tag.getBoolean("tags"),
            tag.getBoolean("namespace")));
  }

  private StackFilters() {}
}
