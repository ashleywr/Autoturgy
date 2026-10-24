/* An Inventarium snapshot contains counts and loaded physical source references.
 * Container budgets apply before inclusion; this class never extracts or owns items. */
package com.sanhiruzu.autoturgy.inventarium;

import com.sanhiruzu.autoturgy.common.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public final class InventoryIndex {
  public record Source(LoadedTargets.Target target, int slot, ItemStack template, int count) {
    public Source {
      template = template.copyWithCount(1);
    }

    @Override
    public ItemStack template() {
      return template.copy();
    }
  }

  public record Entry(ItemStack template, long count) {
    public Entry {
      template = template.copyWithCount(1);
    }

    @Override
    public ItemStack template() {
      return template.copy();
    }
  }

  public record Snapshot(List<Source> sources, List<Entry> entries, int slots, int rejected) {
    public Snapshot {
      sources = List.copyOf(sources);
      entries = List.copyOf(entries);
    }

    public long count(StackFilters.Filter filter) {
      return entries.stream()
          .filter(e -> StackFilters.accepts(filter, e.template()))
          .mapToLong(Entry::count)
          .sum();
    }

    public long count(ItemStack template) {
      return entries.stream()
          .filter(e -> ItemStack.isSameItemSameComponents(template, e.template()))
          .mapToLong(Entry::count)
          .sum();
    }

    public static Snapshot empty() {
      return new Snapshot(List.of(), List.of(), 0, 0);
    }
  }

  public static Snapshot scan(
      ServerLevel level, Collection<LoadedTargets.Target> candidates, int capacity) {
    List<Source> sources = new ArrayList<>();
    List<Entry> entries = new ArrayList<>();
    Set<BlockPos> positions = new HashSet<>();
    Set<IItemHandler> handlers = Collections.newSetFromMap(new IdentityHashMap<>());
    int used = 0, rejected = 0;
    for (LoadedTargets.Target target : candidates.stream().limit(16384).toList()) {
      if (!level.dimension().equals(target.dimension())
          || !level.hasChunkAt(target.pos())
          || positions.contains(target.pos())) continue;
      IItemHandler handler =
          level.getCapability(Capabilities.ItemHandler.BLOCK, target.pos(), target.side());
      if (handler == null || handlers.contains(handler)) continue;
      Set<BlockPos> physical = new HashSet<>();
      physical.add(target.pos());
      // Both halves of a vanilla double chest expose the same physical container.
      if (level.getBlockEntity(target.pos()) instanceof ChestBlockEntity
          && level.getBlockState(target.pos()).getBlock() instanceof ChestBlock) {
        var state = level.getBlockState(target.pos());
        if (state.getValue(ChestBlock.TYPE)
            != net.minecraft.world.level.block.state.properties.ChestType.SINGLE)
          physical.add(target.pos().relative(ChestBlock.getConnectedDirection(state)));
      }
      if (physical.stream().anyMatch(positions::contains)) continue;
      int slots = handler.getSlots();
      if (slots <= 0 || slots > 4096 || used + slots > Math.clamp(capacity, 0, 16384)) {
        rejected++;
        continue;
      }
      positions.addAll(physical);
      handlers.add(handler);
      used += slots;
      for (int slot = 0; slot < slots; slot++) {
        ItemStack visible = handler.getStackInSlot(slot);
        if (visible.isEmpty()) continue;
        ItemStack available = handler.extractItem(slot, visible.getCount(), true);
        if (available.isEmpty() || !ItemStack.isSameItemSameComponents(visible, available))
          continue;
        int count = Math.min(visible.getCount(), available.getCount());
        sources.add(new Source(target, slot, visible, count));
        int match = -1;
        for (int i = 0; i < entries.size(); i++)
          if (ItemStack.isSameItemSameComponents(entries.get(i).template(), visible)) {
            match = i;
            break;
          }
        if (match < 0) entries.add(new Entry(visible, count));
        else {
          Entry old = entries.get(match);
          entries.set(match, new Entry(old.template(), old.count() + count));
        }
      }
    }
    return new Snapshot(sources, entries, used, rejected);
  }

  public static List<Entry> search(Snapshot snapshot, String query, boolean byCount) {
    String needle = query.toLowerCase(Locale.ROOT);
    Comparator<Entry> order =
        byCount
            ? Comparator.comparingLong(Entry::count).reversed()
            : Comparator.comparing(e -> e.template().getHoverName().getString());
    return snapshot.entries().stream()
        .filter(
            e ->
                (e.template().getHoverName().getString()
                        + " "
                        + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                            e.template().getItem()))
                    .toLowerCase(Locale.ROOT)
                    .contains(needle))
        .sorted(order)
        .toList();
  }

  private InventoryIndex() {}
}
