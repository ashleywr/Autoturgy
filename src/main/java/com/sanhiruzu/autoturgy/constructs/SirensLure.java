/* Pays short attraction leases and installs a reversible ordinary movement goal.
 * Loaded totem arbitration, combat refusal and per-lure resistance bound interference. */
package com.sanhiruzu.autoturgy.constructs;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import com.sanhiruzu.autoturgy.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.*;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.registries.*;

public final class SirensLure {
  public static DeferredBlock<SirenBlock> BLOCK;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SirenEntity>> TYPE;
  private static final Map<Level, Map<Long, Set<BlockPos>>> LOADED = new WeakHashMap<>();
  private static final String RESIST = "autoturgy_siren_resistance",
      COMBAT = "autoturgy_siren_combat";

  public record Column(boolean valid, int bottom, int top, int strength) {}

  public static void register(IEventBus bus) {
    BLOCK = Content.block("sirens_lure", SirenBlock::new);
    TYPE =
        Content.ENTITIES.register(
            "sirens_lure",
            () -> BlockEntityType.Builder.of(SirenEntity::new, BLOCK.get()).build(null));
    bus.addListener((RegisterCapabilitiesEvent e) -> SpireModule.ports(e, TYPE.get()));
    NeoForge.EVENT_BUS.addListener(
        (EntityJoinLevelEvent e) -> {
          if (!e.getLevel().isClientSide
              && e.getEntity() instanceof Mob mob
              && susceptibleType(mob)) install(mob);
        });
    NeoForge.EVENT_BUS.addListener(
        (LivingDamageEvent.Post e) -> {
          if (!e.getEntity().level().isClientSide
              && e.getSource().getEntity() instanceof LivingEntity attacker) {
            long until = e.getEntity().level().getGameTime() + 100;
            e.getEntity().getPersistentData().putLong(COMBAT, until);
            attacker.getPersistentData().putLong(COMBAT, until);
          }
        });
  }

  public static void install(Mob mob) {
    if (mob.goalSelector.getAvailableGoals().stream()
        .noneMatch(g -> g.getGoal() instanceof Attraction))
      mob.goalSelector.addGoal(2, new Attraction(mob));
  }

  public static boolean susceptibleType(Mob mob) {
    return !(mob instanceof AbstractGolem
        || mob instanceof EntityThaumaturgeGolem
        || mob instanceof Slime
        || mob instanceof EnderDragon
        || mob instanceof WitherBoss
        || mob.getType().is(Tags.EntityTypes.BOSSES));
  }

  public static boolean idle(Mob mob) {
    return susceptibleType(mob)
        && mob.isAlive()
        && !mob.isNoAi()
        && mob.getTarget() == null
        && !mob.isPassenger()
        && !mob.isLeashed()
        && !(mob instanceof TamableAnimal tame && tame.isOrderedToSit())
        && mob.getPersistentData().getLong(COMBAT) <= mob.level().getGameTime()
        && (mob.getLastHurtByMob() == null
            || mob.tickCount - mob.getLastHurtByMobTimestamp() > 100);
  }

  public static Column column(Level l, BlockPos p) {
    int down = 0, up = 0;
    while (down < 16 && DimensionalLure.totem(l, p.below(down + 1))) down++;
    while (up < 16 && DimensionalLure.totem(l, p.above(up + 1))) up++;
    return new Column(down + up >= 2, p.getY() - down, p.getY() + up, down + up);
  }

  public static boolean resistant(Mob mob, BlockPos p) {
    ListTag rows = mob.getPersistentData().getList(RESIST, Tag.TAG_COMPOUND);
    long now = mob.level().getGameTime();
    for (int i = 0; i < rows.size(); i++)
      if (rows.getCompound(i).getLong("pos") == p.asLong()
          && rows.getCompound(i).getLong("until") > now) return true;
    return false;
  }

  public static void resist(Mob mob, BlockPos p) {
    ListTag old = mob.getPersistentData().getList(RESIST, Tag.TAG_COMPOUND), rows = new ListTag();
    long now = mob.level().getGameTime();
    for (int i = 0; i < old.size() && rows.size() < 7; i++) {
      CompoundTag row = old.getCompound(i);
      if (row.getLong("pos") != p.asLong() && row.getLong("until") > now) rows.add(row.copy());
    }
    CompoundTag row = new CompoundTag();
    row.putLong("pos", p.asLong());
    row.putLong("until", now + 600);
    rows.add(row);
    mob.getPersistentData().put(RESIST, rows);
  }

  public static SirenEntity winner(Mob mob) {
    if (!idle(mob)) return null;
    SirenEntity best = null;
    int seen = 0;
    var buckets = LOADED.getOrDefault(mob.level(), Map.of());
    int cx = mob.blockPosition().getX() >> 4, cz = mob.blockPosition().getZ() >> 4;
    for (int x = cx - 2; x <= cx + 2; x++)
      for (int z = cz - 2; z <= cz + 2; z++)
        for (BlockPos p : buckets.getOrDefault(ChunkPos.asLong(x, z), Set.of())) {
          if (++seen > 4096) return best;
          if (!mob.level().hasChunkAt(p)
              || !(mob.level().getBlockEntity(p) instanceof SirenEntity s)
              || !s.available()
              || !s.covers(mob)
              || resistant(mob, p)) continue;
          if (best == null || stronger(s, best)) best = s;
        }
    return best;
  }

  private static boolean stronger(SirenEntity a, SirenEntity b) {
    int ac = column(a.getLevel(), a.getBlockPos()).strength(),
        bc = column(b.getLevel(), b.getBlockPos()).strength();
    if (ac != bc) return ac > bc;
    if (a.radius() != b.radius()) return a.radius() > b.radius();
    return a.getBlockPos().compareTo(b.getBlockPos()) < 0;
  }

  public static final class SirenBlock extends ConstructEntity.ConstructBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new SirenEntity(p, s);
    }
  }

  public static final class SirenEntity extends ConstructEntity {
    private long paidUntil;
    private int paidRadius = 12;

    public SirenEntity(BlockPos p, BlockState s) {
      super(TYPE.get(), p, s);
    }

    public String kind() {
      return "sirens_lure";
    }

    public String research() {
      return "sirens";
    }

    public List<ResourceKey<IAspect>> fuels() {
      return List.of(TTAspects.POTENTIA, TTAspects.DESIDERIUM);
    }

    public ResourceKey<IAspect> activationAspect() {
      return TTAspects.ORDO;
    }

    public int activationCost() {
      return 100;
    }

    public boolean validStart() {
      boolean ok = column(level, worldPosition).valid();
      failure = ok ? "ready" : "structure";
      return ok;
    }

    public boolean paid() {
      return active && paidUntil > level.getGameTime();
    }

    public int radius() {
      return paid() ? paidRadius : amount(TTAspects.DESIDERIUM) > 0 ? 24 : 12;
    }

    public boolean available() {
      return active
          && !isRemoved()
          && column(level, worldPosition).valid()
          && (paid() || amount(TTAspects.POTENTIA) > 0);
    }

    public boolean covers(Mob mob) {
      Column c = column(level, worldPosition);
      double dx = mob.getX() - worldPosition.getX() - .5,
          dz = mob.getZ() - worldPosition.getZ() - .5;
      return c.valid()
          && mob.getY() >= c.bottom() - 4
          && mob.getY() <= c.top() + 5
          && dx * dx + dz * dz <= radius() * radius();
    }

    public void cancel() {
      paidUntil = 0;
      super.cancel();
    }

    public void serverTick() {
      intake();
      if (active && !column(level, worldPosition).valid()) {
        cancel();
        failure = "structure";
      }
      if (level.getGameTime() % 20 != 0) return;
      if (active && paidUntil <= level.getGameTime() + 1 && amount(TTAspects.POTENTIA) > 0) {
        Column c = column(level, worldPosition);
        AABB area =
            new AABB(
                worldPosition.getX() - radius(),
                c.bottom() - 4,
                worldPosition.getZ() - radius(),
                worldPosition.getX() + radius() + 1,
                c.top() + 5,
                worldPosition.getZ() + radius() + 1);
        var candidates = level.getEntitiesOfClass(Mob.class, area, SirensLure::idle);
        int count = 0;
        for (Mob mob : candidates) {
          if (++count > 256) break;
          if (winner(mob) == this) {
            paidRadius = amount(TTAspects.DESIDERIUM) > 0 ? 24 : 12;
            debit(
                paidRadius > 12
                    ? Map.of(TTAspects.POTENTIA, 1, TTAspects.DESIDERIUM, 1)
                    : Map.of(TTAspects.POTENTIA, 1));
            paidUntil = level.getGameTime() + 21;
            operations++;
            failure = "ready";
            break;
          }
        }
      }
      settings[9] = radius();
      settings[10] = column(level, worldPosition).strength();
      status();
    }

    public void onLoad() {
      super.onLoad();
      if (!level.isClientSide)
        LOADED
            .computeIfAbsent(level, k -> new HashMap<>())
            .computeIfAbsent(new ChunkPos(worldPosition).toLong(), k -> new LinkedHashSet<>())
            .add(worldPosition);
    }

    private void unregister() {
      paidUntil = 0;
      var buckets = LOADED.get(level);
      if (buckets != null) {
        long key = new ChunkPos(worldPosition).toLong();
        var rows = buckets.get(key);
        if (rows != null) {
          rows.remove(worldPosition);
          if (rows.isEmpty()) buckets.remove(key);
        }
      }
    }

    public void setRemoved() {
      unregister();
      super.setRemoved();
    }

    public void onChunkUnloaded() {
      unregister();
      super.onChunkUnloaded();
    }
  }

  public static final class Attraction extends Goal {
    private final Mob mob;
    private BlockPos selected;
    private int ticks, stalled;
    private double bestDistance;

    Attraction(Mob mob) {
      this.mob = mob;
      setFlags(EnumSet.of(Flag.MOVE));
    }

    public boolean canUse() {
      SirenEntity s = winner(mob);
      if (s == null || !s.paid()) return false;
      selected = s.getBlockPos();
      return true;
    }

    public boolean canContinueToUse() {
      SirenEntity s = winner(mob);
      return selected != null && s != null && s.paid() && selected.equals(s.getBlockPos());
    }

    public boolean requiresUpdateEveryTick() {
      return true;
    }

    public void start() {
      ticks = 0;
      stalled = 0;
      bestDistance = mob.distanceToSqr(selected.getCenter());
    }

    public void tick() {
      if (selected == null) return;
      ticks++;
      double distance = mob.distanceToSqr(selected.getCenter());
      if (distance < bestDistance - .25) {
        bestDistance = distance;
        stalled = 0;
      } else stalled++;
      if (ticks % 10 == 1)
        mob.getNavigation()
            .moveTo(selected.getX() + .5, selected.getY(), selected.getZ() + .5, 1.0);
      if (mob instanceof EnderMan && stalled >= 40 && ticks % 40 == 0 && distance > 16)
        nearerTeleport();
      if (stalled >= 100 || ticks >= 200 || distance <= 4 && ticks >= 40) {
        resist(mob, selected);
        mob.getNavigation().stop();
      }
    }

    private void nearerTeleport() {
      double distance = mob.distanceToSqr(selected.getCenter());
      if (distance <= 16) return;
      Vec3 step =
          selected
              .getCenter()
              .subtract(mob.position())
              .normalize()
              .scale(Math.min(4, Math.sqrt(distance) - 2));
      Vec3 destination = mob.position().add(step.x, 0, step.z);
      AABB moved = mob.getBoundingBox().move(destination.subtract(mob.position()));
      if (!DimensionalLure.space(mob.level(), moved)
          || !mob.level().getBlockState(BlockPos.containing(destination).below()).isSolid()) return;
      var event = EventHooks.onEnderTeleport(mob, destination.x, destination.y, destination.z);
      if (!event.isCanceled()
          && DimensionalLure.space(
              mob.level(),
              mob.getBoundingBox()
                  .move(
                      event.getTargetX() - mob.getX(),
                      event.getTargetY() - mob.getY(),
                      event.getTargetZ() - mob.getZ())))
        mob.randomTeleport(event.getTargetX(), event.getTargetY(), event.getTargetZ(), true);
    }

    public void stop() {
      mob.getNavigation().stop();
      selected = null;
    }
  }

  private SirensLure() {}
}
