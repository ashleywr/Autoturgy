/* Read-only adapters use the installed host's public contracts.
 * No reader extracts resources or changes host state to obtain a measurement. */
package com.sanhiruzu.autoturgy.host;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.essentia.*;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneWorkbench;
import com.leclowndu93150.thaumaturge.api.wands.WandAccess;
import com.leclowndu93150.thaumaturge.content.aura.BlockEntityRechargePedestal;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.crucible.BlockEntityCrucible;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityInfusionMatrix;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ThaumaturgeIntegration {
  public record AspectReading(String id, int amount, int capacity) {}

  public record Snapshot(
      List<AspectReading> aspects,
      boolean node,
      boolean valid,
      boolean automaticCapacity,
      boolean perAspectCapacity) {
    public Snapshot(
        List<AspectReading> aspects, boolean node, boolean valid, boolean automaticCapacity) {
      this(aspects, node, valid, automaticCapacity, node);
    }

    public Snapshot {
      aspects = List.copyOf(aspects);
    }

    public static Snapshot invalid() {
      return new Snapshot(List.of(), false, false, false);
    }
  }

  public static Snapshot read(Level level, BlockPos pos, Direction side) {
    if (!level.hasChunkAt(pos)) return Snapshot.invalid();
    Snapshot locus = com.sanhiruzu.autoturgy.essentia.EssentiaModule.reading(level, pos);
    if (locus != null) return locus;
    BlockEntity be = level.getBlockEntity(pos);
    if (be instanceof BlockEntityNode node) {
      List<AspectReading> result = new ArrayList<>();
      for (AspectInstance base : node.getAspectsBase().sortedByTag()) {
        result.add(
            new AspectReading(
                key(base.aspect()),
                node.getAspects().amountOf(base.aspect()),
                Math.max(1, base.amount())));
      }
      return new Snapshot(result, true, true, true);
    }
    IEssentiaStorage storage = level.getCapability(EssentiaCapabilities.STORAGE, pos, side);
    if (storage != null) {
      int capacity =
          level.getBlockState(pos).getBlock() instanceof IEssentiaJar jar ? jar.jarCapacity() : 0;
      return of(storage.contents(), capacity, capacity > 0);
    }
    if (be instanceof BlockEntityCrucible crucible)
      return of(crucible.getAspects(), BlockEntityCrucible.MAX_ASPECT, true);
    if (be instanceof BlockEntityInfusionMatrix matrix)
      return of(matrix.remainingEssentia(), 0, false);
    if (be instanceof IArcaneWorkbench workbench) return wand(workbench.wandStack());
    if (be instanceof BlockEntityRechargePedestal pedestal) return wand(pedestal.getItem());
    if (be instanceof IAspectContainer container) return of(container.getAspects(), 0, false);
    return Snapshot.invalid();
  }

  private static Snapshot wand(ItemStack stack) {
    if (!(stack.getItem() instanceof ItemWand wand)) return Snapshot.invalid();
    int capacity = wand.getParts(stack).maxCentivis();
    var contents = WandAccess.getAllVis(stack);
    List<AspectReading> aspects =
        TTAspects.PRIMALS.stream()
            .map(
                aspect ->
                    new AspectReading(
                        aspect.location().toString(), contents.amount(aspect), capacity))
            .sorted(Comparator.comparing(AspectReading::id))
            .toList();
    return new Snapshot(aspects, false, true, capacity > 0, true);
  }

  private static Snapshot of(AspectList list, int capacity, boolean automatic) {
    return new Snapshot(
        list.sortedByTag().stream()
            .map(a -> new AspectReading(key(a.aspect()), a.amount(), capacity))
            .toList(),
        false,
        true,
        automatic);
  }

  private static String key(Holder<IAspect> aspect) {
    return aspect.unwrapKey().map(k -> k.location().toString()).orElse("unknown");
  }

  public static int signal(
      Snapshot reading, int metric, int selection, boolean inverse, int manualCapacity) {
    if (!reading.valid()) return 0;
    if (metric == 3) {
      int count = reading.aspects().size();
      return inverse ? Math.clamp(15 - count, 0, 15) : Math.min(255, count);
    }
    List<AspectReading> aspects = reading.aspects();
    if (selection > 0)
      aspects = selection <= aspects.size() ? List.of(aspects.get(selection - 1)) : List.of();
    double ratio = 0;
    if (!aspects.isEmpty()) {
      if (metric == 1 || metric == 2) {
        if (reading.node()) {
          var ratios =
              aspects.stream()
                  .mapToDouble(
                      a ->
                          (double) a.amount()
                              / Math.max(1, manualCapacity > 0 ? manualCapacity : a.capacity()));
          ratio = metric == 1 ? ratios.max().orElse(0) : ratios.min().orElse(0);
        } else {
          var amounts = aspects.stream().mapToInt(AspectReading::amount);
          int value = metric == 1 ? amounts.max().orElse(0) : amounts.min().orElse(0);
          int capacity = manualCapacity > 0 ? manualCapacity : aspects.getFirst().capacity();
          ratio = capacity > 0 ? (double) value / capacity : 0;
        }
      } else {
        long amount = aspects.stream().mapToLong(AspectReading::amount).sum();
        long capacity =
            manualCapacity > 0
                ? manualCapacity
                : reading.perAspectCapacity()
                    ? aspects.stream().mapToLong(AspectReading::capacity).sum()
                    : aspects.getFirst().capacity();
        ratio = capacity > 0 ? (double) amount / capacity : 0;
      }
    }
    int signal = (int) Math.ceil(Math.clamp(ratio, 0, 1) * 15);
    return inverse ? 15 - signal : signal;
  }

  public static int signal(
      Snapshot reading, int metric, String selectedAspect, boolean inverse, int manualCapacity) {
    if (metric == 3 || selectedAspect.isEmpty())
      return signal(reading, metric, 0, inverse, manualCapacity);
    for (int i = 0; i < reading.aspects().size(); i++) {
      if (reading.aspects().get(i).id().equals(selectedAspect))
        return signal(reading, metric, i + 1, inverse, manualCapacity);
    }
    return reading.valid() && inverse ? 15 : 0;
  }

  private ThaumaturgeIntegration() {}
}
