/* Counts real sided storage with traversal guards and no extraction.
 * Future index/request devices can implement CountProvider without duplicating stock. */
package com.sanhiruzu.autoturgy.sensing;

import com.sanhiruzu.autoturgy.common.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public final class TallyService {
  public interface CountProvider {
    long count(StackFilters.Filter filter, Set<BlockPos> visited, int depth);
  }

  public static long count(
      ServerLevel level,
      BlockPos origin,
      LoadedTargets.Target target,
      StackFilters.Filter filter,
      int lens,
      Set<BlockPos> visited,
      int depth) {
    if (target == null
        || depth >= 16
        || !LoadedTargets.local(level, origin, target, 20)
        || !visited.add(target.pos())) return 0;
    BlockPos pos = target.pos();
    if (lens == 1)
      return level
          .getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), e -> e.isAlive())
          .stream()
          .filter(e -> StackFilters.accepts(filter, e.getItem()))
          .mapToLong(e -> e.getItem().getCount())
          .sum();
    if (lens == 2) {
      return predictDrops(level, pos).stream()
          .filter(stack -> StackFilters.accepts(filter, stack))
          .mapToLong(ItemStack::getCount)
          .sum();
    }
    BlockEntity be = level.getBlockEntity(pos);
    if (be instanceof CountProvider provider) return provider.count(filter, visited, depth + 1);
    var block = level.getBlockState(pos).getBlock();
    if (block == net.minecraft.world.level.block.Blocks.BOOKSHELF
        || block == com.sanhiruzu.autoturgy.inventarium.InventariumModule.ENCHANTING_SHELF.get()
        || block == com.sanhiruzu.autoturgy.inventarium.InventariumModule.ENSORCELLED_SHELF.get()) {
      for (BlockPos corePos : BlockPos.betweenClosed(pos.offset(-3, -3, -3), pos.offset(3, 3, 3))) {
        if (!level.hasChunkAt(corePos) || !visited.add(corePos.immutable())) continue;
        if (level.getBlockEntity(corePos)
                instanceof com.sanhiruzu.autoturgy.inventarium.InventariumModule.CoreEntity core
            && com.sanhiruzu.autoturgy.inventarium.InventariumModule.structure(level, corePos)
                .valid()) return core.count(filter, visited, depth + 1);
      }
    }
    IItemHandler inventory =
        level.getCapability(Capabilities.ItemHandler.BLOCK, pos, target.side());
    if (inventory == null) return 0;
    long total = 0;
    for (int slot = 0; slot < Math.min(inventory.getSlots(), 4096); slot++) {
      ItemStack visible = inventory.getStackInSlot(slot);
      if (StackFilters.accepts(filter, visible)) {
        ItemStack extractable = inventory.extractItem(slot, visible.getCount(), true);
        if (ItemStack.isSameItemSameComponents(visible, extractable))
          total += Math.min(visible.getCount(), extractable.getCount());
      }
    }
    return total;
  }

  /**
   * Two deterministic tool contexts predict ordinary and silk-touch alternatives. Dynamic loot is
   * sampled rather than exhaustively enumerated.
   */
  public static List<ItemStack> predictDrops(ServerLevel level, BlockPos pos) {
    if (!level.hasChunkAt(pos)) return List.of();
    BlockState state = level.getBlockState(pos);
    List<ItemStack> tools =
        List.of(
            new ItemStack(Items.NETHERITE_PICKAXE),
            new ItemStack(Items.NETHERITE_AXE),
            new ItemStack(Items.NETHERITE_SHOVEL),
            new ItemStack(Items.NETHERITE_HOE),
            new ItemStack(Items.SHEARS));
    ItemStack tool =
        tools.stream()
            .filter(t -> t.isCorrectToolForDrops(state))
            .max(Comparator.comparingDouble(t -> t.getDestroySpeed(state)))
            .orElse(tools.getFirst())
            .copy();
    ItemStack silk = tool.copy();
    silk.enchant(
        level
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.SILK_TOUCH),
        1);
    List<ItemStack> result = new ArrayList<>();
    long seed =
        0x5A17D03EL
            ^ pos.asLong()
            ^ net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock())
                .hashCode();
    for (ItemStack selected : List.of(tool, silk)) {
      LootParams params =
          new LootParams.Builder(level)
              .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
              .withParameter(LootContextParams.BLOCK_STATE, state)
              .withParameter(LootContextParams.TOOL, selected)
              .withOptionalParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(pos))
              .create(LootContextParamSets.BLOCK);
      var table =
          level.getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable());
      for (ItemStack drop :
          table.getRandomItems(params, RandomSource.create(seed)).stream().limit(256).toList()) {
        if (drop.isEmpty()) continue;
        ItemStack existing =
            result.stream()
                .filter(s -> ItemStack.isSameItemSameComponents(s, drop))
                .findFirst()
                .orElse(null);
        if (existing == null) result.add(drop.copyWithCount(Math.min(4096, drop.getCount())));
        else existing.setCount(Math.max(existing.getCount(), Math.min(4096, drop.getCount())));
      }
    }
    return result;
  }

  private TallyService() {}
}
