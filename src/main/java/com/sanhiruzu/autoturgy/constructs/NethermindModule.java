/* Registers runed obsidian and validates the paid Nethermind platform.
 * Weighted block plans commit only into loaded legal space; creature work is delegated. */
package com.sanhiruzu.autoturgy.constructs;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.api.transaction.Transaction;
import com.sanhiruzu.autoturgy.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;

public final class NethermindModule {
  public static DeferredBlock<NethermindBlock> CONTROLLER;
  public static DeferredBlock<Block> OBSIDIAN;
  public static final Map<Rune, DeferredBlock<Block>> RUNES = new EnumMap<>(Rune.class);
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<NethermindEntity>> TYPE;

  public enum Rune {
    ROCK,
    SEA,
    CRYSTAL,
    LIGHT,
    SOUL,
    PEBBLE,
    CELERITY,
    PIGLIN,
    BLAZE,
    GHAST,
    WITHER_SKELETON,
    MAGMA_CUBE,
    FIREBAT,
    WISP;

    public boolean creature() {
      return ordinal() >= PIGLIN.ordinal();
    }

    public boolean block() {
      return ordinal() < CELERITY.ordinal();
    }
  }

  public record Structure(boolean valid, Map<Rune, Integer> runes) {
    public int count(Rune rune) {
      return runes.getOrDefault(rune, 0);
    }

    public int blocks() {
      return runes.entrySet().stream()
          .filter(e -> e.getKey().block())
          .mapToInt(Map.Entry::getValue)
          .sum();
    }

    public int creatures() {
      return runes.entrySet().stream()
          .filter(e -> e.getKey().creature())
          .mapToInt(Map.Entry::getValue)
          .sum();
    }

    public int celerity() {
      return Math.min(12, count(Rune.CELERITY));
    }

    public int interval() {
      return Math.max(20, 200 - 15 * celerity());
    }

    public int multiplier() {
      return 1 + Math.min(4, celerity());
    }
  }

  public record Material(Rune rune, Block block, ResourceKey<IAspect> aspect, int baseCost) {}

  public static List<Material> materials() {
    return List.of(
        new Material(Rune.ROCK, Blocks.NETHERRACK, TTAspects.TERRA, 2),
        new Material(Rune.SEA, Blocks.LAVA, TTAspects.IGNIS, 4),
        new Material(Rune.CRYSTAL, Blocks.NETHER_QUARTZ_ORE, TTAspects.VITREUS, 4),
        new Material(Rune.LIGHT, Blocks.GLOWSTONE, TTAspects.LUX, 4),
        new Material(Rune.SOUL, Blocks.SOUL_SAND, TTAspects.SPIRITUS, 4),
        new Material(Rune.PEBBLE, Blocks.GRAVEL, TTAspects.TERRA, 2));
  }

  public static void register(IEventBus bus) {
    CONTROLLER = Content.block("nethermind", NethermindBlock::new);
    OBSIDIAN =
        Content.block(
            "runed_obsidian",
            () ->
                new Block(Block.Properties.of().strength(50, 1200).requiresCorrectToolForDrops()));
    for (Rune rune : Rune.values())
      RUNES.put(
          rune,
          Content.block(
              "rune_" + rune.name().toLowerCase(Locale.ROOT),
              () ->
                  new Block(
                      Block.Properties.of().strength(50, 1200).requiresCorrectToolForDrops())));
    TYPE =
        Content.ENTITIES.register(
            "nethermind",
            () -> BlockEntityType.Builder.of(NethermindEntity::new, CONTROLLER.get()).build(null));
    bus.addListener((RegisterCapabilitiesEvent e) -> SpireModule.ports(e, TYPE.get()));
    DimensionalLure.register(bus);
  }

  public static Rune rune(BlockState s) {
    for (var entry : RUNES.entrySet()) if (s.is(entry.getValue().get())) return entry.getKey();
    return null;
  }

  public static Structure structure(Level l, BlockPos p) {
    Map<Rune, Integer> runes = new EnumMap<>(Rune.class);
    boolean valid = l.hasChunkAt(p) && l.getBlockState(p).is(CONTROLLER.get());
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++) {
        if (x == 0 && z == 0) continue;
        BlockPos q = p.offset(x, 0, z);
        if (!l.hasChunkAt(q)) return new Structure(false, Map.of());
        var s = l.getBlockState(q);
        Rune rune = rune(s);
        if (rune != null) runes.merge(rune, 1, Integer::sum);
        else if (!s.is(OBSIDIAN.get())) valid = false;
      }
    Structure result = new Structure(valid, Map.copyOf(runes));
    return new Structure(valid && !(result.blocks() > 0 && result.creatures() > 0), result.runes());
  }

  public static List<Material> weighted(Structure s) {
    List<Material> choices = new ArrayList<>();
    for (Material m : materials()) {
      int n = s.count(m.rune());
      if (s.blocks() >= 4 && n == 0) continue;
      int weight = (m.rune() == Rune.ROCK ? 4 : m.rune() == Rune.SEA ? 1 : 0) + n * 4;
      for (int i = 0; i < weight; i++) choices.add(m);
    }
    return choices;
  }

  public static Map<ResourceKey<IAspect>, Integer> cost(Structure s, Material m) {
    int n = Math.max(1, m.baseCost() - (s.count(m.rune()) > 0 ? 1 : 0)) * s.multiplier();
    Map<ResourceKey<IAspect>, Integer> cost = new LinkedHashMap<>();
    cost.put(TTAspects.IGNIS, s.multiplier());
    cost.merge(m.aspect(), n, Integer::sum);
    return cost;
  }

  public static boolean legal(Level l, BlockPos p) {
    return !l.isOutsideBuildHeight(p)
        && l.hasChunkAt(p)
        && l.getBlockEntity(p) == null
        && l.getBlockState(p).canBeReplaced()
        && l.getFluidState(p).isEmpty()
        && l.getEntities(
                (net.minecraft.world.entity.Entity) null,
                new AABB(p),
                e -> e.isAlive() && !e.isSpectator())
            .isEmpty();
  }

  public static BlockPos target(Level l, BlockPos origin) {
    int start = l.random.nextInt(729);
    for (int i = 0; i < 729; i++) {
      int n = (start + i) % 729;
      BlockPos p = origin.offset(n % 9 - 4, -2 - n / 81, n / 9 % 9 - 4);
      if (legal(l, p)) return p;
    }
    return null;
  }

  public static final class NethermindBlock extends ConstructEntity.ConstructBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new NethermindEntity(p, s);
    }
  }

  public static final class NethermindEntity extends ConstructEntity {
    private int timer;

    public NethermindEntity(BlockPos p, BlockState s) {
      super(TYPE.get(), p, s);
    }

    public String kind() {
      return "nethermind";
    }

    public String research() {
      return "nethermind";
    }

    public List<ResourceKey<IAspect>> fuels() {
      return List.of(
          TTAspects.IGNIS,
          TTAspects.TERRA,
          TTAspects.VITREUS,
          TTAspects.LUX,
          TTAspects.SPIRITUS,
          TTAspects.BESTIA,
          TTAspects.EXANIMIS,
          TTAspects.VOLATUS,
          TTAspects.AURAM);
    }

    public ResourceKey<IAspect> activationAspect() {
      return TTAspects.IGNIS;
    }

    public int activationCost() {
      return 500;
    }

    public boolean validStart() {
      Structure s = structure(level, worldPosition);
      if (!s.valid()) {
        failure = "structure";
        return false;
      }
      if (s.creatures() > 0) return DimensionalLure.canOperate(this, s);
      if (target(level, worldPosition) == null) {
        failure = "space";
        return false;
      }
      if (weighted(s).stream().noneMatch(m -> has(cost(s, m)))) {
        failure = "essentia";
        return false;
      }
      failure = "ready";
      return true;
    }

    public boolean operate() {
      if (!active || !(level instanceof ServerLevel server)) return false;
      Structure s = structure(level, worldPosition);
      if (!s.valid()) {
        cancel();
        failure = "structure";
        return false;
      }
      if (s.creatures() > 0) return DimensionalLure.operate(this, s);
      BlockPos target = target(level, worldPosition);
      if (target == null) {
        failure = "space";
        return false;
      }
      List<Material> choices = weighted(s);
      if (choices.isEmpty()) return false;
      Material m = choices.get(level.random.nextInt(choices.size()));
      var cost = cost(s, m);
      if (!has(cost)) {
        failure = "essentia";
        return false;
      }
      try (Transaction tx = Transaction.openRoot()) {
        if (VisRelayHelper.drainCentivis(
                server, worldPosition, TTAspects.IGNIS, 5 * s.multiplier(), tx)
            != 5 * s.multiplier()) {
          failure = "relay_vis";
          return false;
        }
        if (!legal(level, target)
            || !server.setBlockAndUpdate(target, m.block().defaultBlockState())) return false;
        debit(cost);
        tx.commit();
        operations++;
        failure = "ready";
        return true;
      }
    }

    public void serverTick() {
      intake();
      Structure s = structure(level, worldPosition);
      if (active && !s.valid()) {
        cancel();
        failure = "structure";
      }
      if (active && ++timer >= s.interval()) {
        timer = 0;
        operate();
      }
      if (level.getGameTime() % 20 == 0) {
        settings[9] = s.celerity();
        settings[10] = s.interval();
        status();
      }
    }
  }

  private NethermindModule() {}
}
