package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.sanhiruzu.autoturgy.constructs.*;
import com.sanhiruzu.autoturgy.essentia.EssentiaModule;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class SirenGameTests {
  static final BlockPos P = new BlockPos(8, 4, 8);

  static SirensLure.SirenEntity siren(GameTestHelper h, BlockPos p, int height) {
    for (int x = 1; x < 17; x++)
      for (int z = 1; z < 17; z++)
        if (h.getBlockState(new BlockPos(x, 3, z)).isAir())
          h.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
    h.setBlock(p, SirensLure.BLOCK.get());
    h.setBlock(p.below(), EssentiaModule.host("obsidian_totem"));
    h.setBlock(p.below(2), EssentiaModule.host("obsidian_totem"));
    if (height > 2) h.setBlock(p.above(), EssentiaModule.host("obsidian_totem"));
    return h.getBlockEntity(p);
  }

  static Cow cow(GameTestHelper h, BlockPos p) {
    Cow cow = EntityType.COW.create(h.getLevel());
    cow.moveTo(h.absolutePos(p).getBottomCenter());
    cow.setPersistenceRequired();
    h.getLevel().addFreshEntity(cow);
    return cow;
  }

  @GameTest(template = "constructs", timeoutTicks = 160)
  public static void realPaidIdleMobNavigationAndSourceConservation(GameTestHelper h) {
    var s = siren(h, P, 2);
    ConstructGameTests.feed(h, s, TTAspects.POTENTIA, 8);
    ConstructGameTests.start(h, s);
    var mob = cow(h, P.north(7));
    double start = mob.distanceToSqr(s.getBlockPos().getCenter());
    h.succeedWhen(
        () -> {
          h.assertTrue(
              mob.distanceToSqr(s.getBlockPos().getCenter()) < start - 12,
              "actual ordinary navigation moves cow toward paid totem");
          h.assertTrue(s.operations > 0, "real paid attraction lease acquired");
          h.assertValueEqual(
              s.amount(TTAspects.POTENTIA) + s.operations,
              8,
              "actual source item jar to controller to movement conservation");
          h.assertTrue(
              mob.getTarget() == null && !mob.isNoAi(), "target and external AI untouched");
        });
  }

  @GameTest(template = "constructs", timeoutTicks = 65)
  public static void strongestArbitrationCombatImmuneAndUnloadCallback(GameTestHelper h) {
    var weak = siren(h, new BlockPos(5, 4, 8), 2);
    var strong = siren(h, new BlockPos(12, 4, 8), 3);
    ConstructGameTests.feed(h, weak, TTAspects.POTENTIA, 8);
    ConstructGameTests.feed(h, strong, TTAspects.POTENTIA, 8);
    ConstructGameTests.start(h, weak);
    ConstructGameTests.start(h, strong);
    var mob = cow(h, new BlockPos(9, 4, 4));
    h.runAfterDelay(
        2,
        () ->
            h.assertTrue(
                SirensLure.winner(mob) == strong,
                "strongest real totem column wins deterministically"));
    var slime = EntityType.SLIME.create(h.getLevel());
    slime.moveTo(mob.position());
    h.assertFalse(SirensLure.susceptibleType(slime), "actual slime immune");
    var golem = EntityType.IRON_GOLEM.create(h.getLevel());
    h.assertFalse(SirensLure.susceptibleType(golem), "actual golem immune");
    var wither = EntityType.WITHER.create(h.getLevel());
    h.assertFalse(SirensLure.susceptibleType(wither), "actual boss immune");
    var host =
        com.leclowndu93150.thaumaturge.registry.TTEntities.THAUMATURGE_GOLEM
            .get()
            .create(h.getLevel());
    h.assertFalse(SirensLure.susceptibleType(host), "actual host golem immune");
    var zombie = EntityType.ZOMBIE.create(h.getLevel());
    zombie.setTarget(mob);
    h.assertTrue(SirensLure.winner(zombie) == null, "actual combat target wins over attraction");
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(strong.operations > 0, "strongest pays movement");
          h.assertValueEqual(weak.operations, 0, "losing totem consumes no source");
          strong.onChunkUnloaded();
          h.assertTrue(SirensLure.winner(mob) == weak, "unload callback unregisters former winner");
          weak.cancel();
          h.assertTrue(SirensLure.winner(mob) == null, "disable releases last lure");
          h.assertTrue(
              mob.getTarget() == null && !mob.isNoAi(), "no temporary AI/target mutation remains");
          h.succeed();
        });
  }

  @GameTest(template = "constructs", timeoutTicks = 165)
  public static void blockedMobGetsPerLureTemporaryResistance(GameTestHelper h) {
    var s = siren(h, P, 2);
    ConstructGameTests.feed(h, s, TTAspects.POTENTIA, 16);
    ConstructGameTests.start(h, s);
    BlockPos cage = P.north(5);
    var cow = cow(h, cage);
    for (Direction d : Direction.Plane.HORIZONTAL)
      for (int y = 0; y < 3; y++) h.setBlock(cage.relative(d).above(y), Blocks.STONE);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              SirensLure.resistant(cow, s.getBlockPos()),
              "real stalled navigation acquires bounded per-lure resistance");
          h.assertTrue(SirensLure.winner(cow) == null, "resistance excludes same lure");
          h.assertTrue(
              !cow.isNoAi() && cow.getTarget() == null, "resistance leaves ordinary AI intact");
        });
  }

  @GameTest(template = "constructs", timeoutTicks = 50)
  public static void emptyPeriodsFreeAndPaidDesideriumExtendsRadius(GameTestHelper h) {
    var s = siren(h, P, 2);
    ConstructGameTests.feed(h, s, TTAspects.POTENTIA, 8);
    ConstructGameTests.feed(h, s, TTAspects.DESIDERIUM, 4);
    ConstructGameTests.start(h, s);
    h.runAfterDelay(
        22,
        () -> {
          h.assertValueEqual(s.amount(TTAspects.POTENTIA), 8, "no eligible target means no upkeep");
          h.assertValueEqual(s.amount(TTAspects.DESIDERIUM), 4, "no idle-period extension charge");
          var mob = cow(h, P.north(5));
          h.assertValueEqual(
              s.radius(), 24, "actual host Desiderium enables explicit modern Fames mapping");
          h.assertTrue(SirensLure.winner(mob) == s, "eligible paid candidate");
        });
    h.runAfterDelay(
        43,
        () -> {
          h.assertTrue(s.operations > 0, "successful paid attraction occurred");
          h.assertValueEqual(
              s.amount(TTAspects.POTENTIA) + s.operations,
              8,
              "one actual Potentia per working second");
          h.assertValueEqual(
              s.amount(TTAspects.DESIDERIUM) + s.operations,
              4,
              "one actual Desiderium per extended working second");
          h.succeed();
        });
  }

  @GameTest(template = "constructs", timeoutTicks = 60)
  public static void equalStrengthWinnerStableAndRecentActualCombatRefuses(GameTestHelper h) {
    var a = siren(h, new BlockPos(5, 4, 8), 2);
    var b = siren(h, new BlockPos(12, 4, 8), 2);
    ConstructGameTests.feed(h, a, TTAspects.POTENTIA, 8);
    ConstructGameTests.feed(h, b, TTAspects.POTENTIA, 8);
    ConstructGameTests.start(h, a);
    ConstructGameTests.start(h, b);
    var mob = cow(h, new BlockPos(9, 4, 5));
    var expected = a.getBlockPos().compareTo(b.getBlockPos()) < 0 ? a : b;
    h.runAfterDelay(
        2,
        () -> h.assertTrue(SirensLure.winner(mob) == expected, "coordinate tie-break is stable"));
    h.runAfterDelay(
        3,
        () -> {
          var attacker = EntityType.ZOMBIE.create(h.getLevel());
          attacker.moveTo(mob.position());
          h.getLevel().addFreshEntity(attacker);
          mob.hurt(h.getLevel().damageSources().mobAttack(attacker), 1);
          h.assertTrue(SirensLure.winner(mob) == null, "actual recent living damage refuses lure");
          a.cancel();
          b.cancel();
          h.succeed();
        });
  }

  @GameTest(template = "constructs", timeoutTicks = 95)
  public static void singlePaidLeaseExpiresOnDepletionAndMovementGoalReleases(GameTestHelper h) {
    var s = siren(h, P, 2);
    ConstructGameTests.feed(h, s, TTAspects.POTENTIA, 1);
    ConstructGameTests.start(h, s);
    var mob = cow(h, P.north(7));
    h.runAfterDelay(
        65,
        () -> {
          h.assertValueEqual(
              s.amount(TTAspects.POTENTIA), 0, "only one physically paid source consumed");
          h.assertValueEqual(s.operations, 1, "depletion cannot create a second lease");
          h.assertFalse(s.paid(), "paid window expires while unfunded");
          h.assertTrue(SirensLure.winner(mob) == null, "unfunded lure loses authority promptly");
          h.assertTrue(
              mob.goalSelector.getAvailableGoals().stream()
                  .noneMatch(g -> g.getGoal() instanceof SirensLure.Attraction && g.isRunning()),
              "ordinary movement goal released");
          h.assertFalse(mob.isNoAi(), "ordinary AI still enabled");
          h.succeed();
        });
  }

  @GameTest(template = "constructs", timeoutTicks = 100)
  public static void paidEndermanMakesOneSafeNearerStepThroughOrdinaryTeleportEvent(
      GameTestHelper h) {
    var s = siren(h, P, 2);
    ConstructGameTests.feed(h, s, TTAspects.POTENTIA, 8);
    ConstructGameTests.start(h, s);
    BlockPos start = P.north(7);
    EnderMan mob = EntityType.ENDERMAN.create(h.getLevel());
    mob.moveTo(h.absolutePos(start).getBottomCenter());
    mob.setPersistenceRequired();
    h.getLevel().addFreshEntity(mob);
    for (Direction d : Direction.Plane.HORIZONTAL)
      for (int y = 0; y < 4; y++) h.setBlock(start.relative(d).above(y), Blocks.STONE);
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++) h.setBlock(start.offset(x, 4, z), Blocks.STONE);
    int[] events = {0};
    double[] old = {0}, next = {0};
    java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityTeleportEvent.EnderEntity>
        listener =
            e -> {
              if (e.getEntity() == mob) {
                events[0]++;
                old[0] = mob.distanceToSqr(s.getBlockPos().getCenter());
                next[0] =
                    new net.minecraft.world.phys.Vec3(
                            e.getTargetX(), e.getTargetY(), e.getTargetZ())
                        .distanceToSqr(s.getBlockPos().getCenter());
              }
            };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
    h.runAfterDelay(
        80,
        () -> {
          net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
          h.assertTrue(events[0] > 0, "paid stalled enderman uses ordinary ender teleport event");
          h.assertTrue(next[0] < old[0], "safe teleport target is actually nearer");
          h.assertTrue(
              mob.distanceToSqr(s.getBlockPos().getCenter()) < 40,
              "actual successful step moved nearer across obstruction");
          h.assertTrue(
              s.operations > 0 && s.amount(TTAspects.POTENTIA) + s.operations == 8,
              "all attraction including teleport paid");
          h.succeed();
        });
  }
}
