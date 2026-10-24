/* Replaces block conjuration with bounded real host and vanilla creature spawns.
 * Both validated construct ends pay; collision and instability gate every effect. */
package com.sanhiruzu.autoturgy.constructs;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.api.transaction.Transaction;
import com.leclowndu93150.thaumaturge.content.entity.WispEntity;
import com.leclowndu93150.thaumaturge.registry.TTEntities;
import com.sanhiruzu.autoturgy.*;
import com.sanhiruzu.autoturgy.constructs.NethermindModule.*;
import com.sanhiruzu.autoturgy.essentia.EssentiaModule;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.registries.*;

public final class DimensionalLure {
  public static DeferredBlock<LureBlock> BLOCK;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<LureEntity>> TYPE;

  public record Creature(Rune rune, EntityType<? extends Mob> type, ResourceKey<IAspect> aspect) {}

  public static List<Creature> creatures() {
    return List.of(
        new Creature(Rune.PIGLIN, EntityType.ZOMBIFIED_PIGLIN, TTAspects.EXANIMIS),
        new Creature(Rune.BLAZE, EntityType.BLAZE, TTAspects.IGNIS),
        new Creature(Rune.GHAST, EntityType.GHAST, TTAspects.VOLATUS),
        new Creature(Rune.WITHER_SKELETON, EntityType.WITHER_SKELETON, TTAspects.EXANIMIS),
        new Creature(Rune.MAGMA_CUBE, EntityType.MAGMA_CUBE, TTAspects.BESTIA),
        new Creature(Rune.FIREBAT, TTEntities.FIRE_BAT.get(), TTAspects.BESTIA),
        new Creature(Rune.WISP, TTEntities.WISP.get(), TTAspects.AURAM));
  }

  public static void register(IEventBus bus) {
    BLOCK = Content.block("dimensional_lure", LureBlock::new);
    TYPE =
        Content.ENTITIES.register(
            "dimensional_lure",
            () -> BlockEntityType.Builder.of(LureEntity::new, BLOCK.get()).build(null));
    bus.addListener((RegisterCapabilitiesEvent e) -> SpireModule.ports(e, TYPE.get()));
  }

  public static boolean totem(Level l, BlockPos p) {
    return l.hasChunkAt(p)
        && (l.getBlockState(p).is(EssentiaModule.host("obsidian_totem"))
            || l.getBlockState(p).is(EssentiaModule.host("obsidian_totem_charged")));
  }

  public static boolean nitor(Level l, BlockPos p) {
    if (!l.hasChunkAt(p)) return false;
    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(l.getBlockState(p).getBlock());
    return id.getNamespace().equals("thaumaturge") && id.getPath().startsWith("nitor_");
  }

  public static boolean ring(Level l, BlockPos p) {
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++)
        if (Math.max(Math.abs(x), Math.abs(z)) == 2) {
          BlockPos q = p.offset(x, 0, z);
          if (!l.hasChunkAt(q) || !l.getBlockState(q).is(NethermindModule.OBSIDIAN.get()))
            return false;
        }
    return true;
  }

  public static LureEntity lower(NethermindEntity upper, Structure s) {
    if (s.creatures() < 4 || s.blocks() > 0) return null;
    int minimum = s.count(Rune.GHAST) > 0 ? 8 : 6;
    for (int dy = 1; dy <= 10; dy++) {
      BlockPos p = upper.getBlockPos().below(dy);
      if (!upper.getLevel().hasChunkAt(p)) return null;
      if (upper.getLevel().getBlockEntity(p) instanceof LureEntity lure)
        return dy >= minimum && ring(upper.getLevel(), p) ? lure : null;
      if (!upper.getLevel().getBlockState(p).getCollisionShape(upper.getLevel(), p).isEmpty())
        return null;
    }
    return null;
  }

  public static int stability(Level l, BlockPos low, BlockPos high) {
    int sum = 0, first = -1;
    boolean equal = true;
    for (int x : new int[] {-3, 3})
      for (int z : new int[] {-3, 3}) {
        int height = 0;
        while (height < 4 && totem(l, low.offset(x, height, z))) height++;
        sum += height;
        if (first < 0) first = height;
        else equal &= first == height;
        if (height > 0 && nitor(l, low.offset(x, height, z))) sum += 2;
      }
    if (equal && first > 0) sum += 4;
    for (Direction d : Direction.Plane.HORIZONTAL)
      if (nitor(l, high.relative(d, 2).below())) sum += 2;
    return sum;
  }

  public static Map<ResourceKey<IAspect>, Integer> upperCost(Structure s, Creature c) {
    Map<ResourceKey<IAspect>, Integer> cost = new LinkedHashMap<>();
    cost.put(TTAspects.IGNIS, 2 * s.multiplier());
    cost.merge(c.aspect(), 3 * s.multiplier(), Integer::sum);
    return cost;
  }

  public static Map<ResourceKey<IAspect>, Integer> lowerCost(Structure s) {
    return Map.of(TTAspects.ALIENIS, 2 * s.multiplier(), TTAspects.POTENTIA, s.multiplier());
  }

  public static List<Creature> weighted(Structure s) {
    List<Creature> choices = new ArrayList<>();
    for (Creature c : creatures()) for (int i = 0; i < s.count(c.rune()); i++) choices.add(c);
    return choices;
  }

  private static Mob candidate(ServerLevel level, LureEntity lure, Creature c, Vec3 at) {
    if (level.getDifficulty() == Difficulty.PEACEFUL) return null;
    Mob mob = c.type().create(level);
    if (mob == null) return null;
    mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
    if (mob instanceof MagmaCube cube) cube.setSize(2, true);
    if (mob instanceof WispEntity wisp) wisp.setAspect(TTAspects.IGNIS.location());
    if (!space(level, mob.getBoundingBox()) || !level.noCollision(mob)) return null;
    return mob;
  }

  public static boolean space(Level level, AABB box) {
    if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()) return false;
    for (BlockPos p :
        BlockPos.betweenClosed(
            BlockPos.containing(box.minX, box.minY, box.minZ),
            BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
      if (!level.hasChunkAt(p)
          || !level.getFluidState(p).isEmpty()
          || !level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
    return true;
  }

  public static boolean canOperate(NethermindEntity upper, Structure s) {
    LureEntity lower = lower(upper, s);
    if (lower == null || !lower.active) {
      upper.failure = "lower_first";
      return false;
    }
    if (lower.instability >= 80) {
      upper.failure = "unstable";
      return false;
    }
    ServerLevel level = (ServerLevel) upper.getLevel();
    if (level
            .getEntitiesOfClass(
                Mob.class, new AABB(lower.getBlockPos()).inflate(12), Entity::isAlive)
            .size()
        >= 16) {
      upper.failure = "crowded";
      return false;
    }
    if (!lower.has(lowerCost(s))) {
      upper.failure = "essentia";
      return false;
    }
    for (Creature c : weighted(s))
      if (upper.has(upperCost(s, c))
          && candidate(level, lower, c, lower.getBlockPos().above().getBottomCenter()) != null) {
        upper.failure = "ready";
        return true;
      }
    upper.failure = "space_or_essentia";
    return false;
  }

  public static boolean operate(NethermindEntity upper, Structure s) {
    if (!canOperate(upper, s)) return false;
    LureEntity lower = lower(upper, s);
    ServerLevel level = (ServerLevel) upper.getLevel();
    List<Creature> choices = weighted(s);
    Creature c = choices.get(level.random.nextInt(choices.size()));
    Mob mob = candidate(level, lower, c, lower.getBlockPos().above().getBottomCenter());
    if (mob == null) {
      upper.failure = "space";
      return false;
    }
    var topCost = upperCost(s, c);
    var bottomCost = lowerCost(s);
    if (!upper.has(topCost) || !lower.has(bottomCost)) {
      upper.failure = "essentia";
      return false;
    }
    try (Transaction tx = Transaction.openRoot()) {
      int vis = 5 * s.multiplier();
      if (VisRelayHelper.drainCentivis(level, upper.getBlockPos(), TTAspects.IGNIS, vis, tx) != vis
          || VisRelayHelper.drainCentivis(level, lower.getBlockPos(), TTAspects.PERDITIO, vis, tx)
              != vis) {
        upper.failure = "relay_vis";
        return false;
      }
      EventHooks.finalizeMobSpawn(
          mob,
          level,
          level.getCurrentDifficultyAt(mob.blockPosition()),
          MobSpawnType.MOB_SUMMONED,
          null);
      if (mob.isSpawnCancelled()) return false;
      if (mob instanceof MagmaCube cube) cube.setSize(2, true);
      if (mob instanceof WispEntity wisp) wisp.setAspect(TTAspects.IGNIS.location());
      if (!space(level, mob.getBoundingBox()) || !level.noCollision(mob)) return false;
      if (!level.addFreshEntity(mob)) return false;
      upper.debit(topCost);
      lower.debit(bottomCost);
      tx.commit();
      upper.operations++;
      lower.operations++;
      upper.failure = "ready";
      lower.instability =
          Math.min(
              100,
              lower.instability
                  + Math.max(
                      0,
                      4
                          + 2 * s.celerity()
                          - stability(level, lower.getBlockPos(), upper.getBlockPos())));
      if (c.rune() == Rune.BLAZE && level.random.nextInt(8) == 0) {
        Mob extra =
            candidate(
                level, lower, c, lower.getBlockPos().above().getBottomCenter().add(1.6, 0, 0));
        if (extra != null) {
          EventHooks.finalizeMobSpawn(
              extra,
              level,
              level.getCurrentDifficultyAt(extra.blockPosition()),
              MobSpawnType.MOB_SUMMONED,
              null);
          if (!extra.isSpawnCancelled()
              && space(level, extra.getBoundingBox())
              && level.noCollision(extra)
              && level.addFreshEntity(extra))
            lower.instability = Math.min(100, lower.instability + 8);
        }
      }
      if (mob instanceof ZombifiedPiglin pig
          && lower.instability >= 20
          && level.random.nextInt(4) == 0) {
        var player = level.getNearestPlayer(pig, 8);
        if (player != null && !player.isCreative() && !player.isSpectator()) {
          pig.setTarget(player);
          pig.setPersistentAngerTarget(player.getUUID());
          pig.setRemainingPersistentAngerTime(200);
        }
      }
      if (lower.instability >= 80) {
        upper.cancel();
        lower.cancel();
        upper.failure = "unstable";
        lower.failure = "unstable";
      }
      lower.changed();
      upper.changed();
      return true;
    }
  }

  public static final class LureBlock extends ConstructEntity.ConstructBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new LureEntity(p, s);
    }
  }

  public static final class LureEntity extends ConstructEntity {
    public int instability;

    public LureEntity(BlockPos p, BlockState s) {
      super(TYPE.get(), p, s);
    }

    public String kind() {
      return "dimensional_lure";
    }

    public String research() {
      return "dimensional_lures";
    }

    public List<ResourceKey<IAspect>> fuels() {
      return List.of(TTAspects.ALIENIS, TTAspects.POTENTIA);
    }

    public ResourceKey<IAspect> activationAspect() {
      return TTAspects.PERDITIO;
    }

    public int activationCost() {
      return 200;
    }

    public boolean validStart() {
      boolean upperValid = false;
      for (int dy = 1; dy <= 10; dy++) {
        BlockPos p = worldPosition.above(dy);
        if (!level.hasChunkAt(p)) break;
        if (level.getBlockEntity(p) instanceof NethermindEntity upper) {
          var s = NethermindModule.structure(level, p);
          upperValid = s.valid() && lower(upper, s) == this;
          break;
        }
        if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) break;
      }
      boolean ok = ring(level, worldPosition) && upperValid && instability < 80;
      failure = ok ? "ready" : instability >= 80 ? "unstable" : "structure";
      return ok;
    }

    public void serverTick() {
      intake();
      if (level.getGameTime() % 20 != 0) return;
      if (instability > 0) instability--;
      if (active && !ring(level, worldPosition)) {
        cancel();
        failure = "structure";
      }
      settings[9] = instability;
      status();
    }

    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      tag.putInt("instability", instability);
    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      instability = Math.clamp(tag.getInt("instability"), 0, 100);
    }
  }

  private DimensionalLure() {}
}
