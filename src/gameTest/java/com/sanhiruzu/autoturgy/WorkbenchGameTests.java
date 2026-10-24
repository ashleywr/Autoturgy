package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar;
import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import com.leclowndu93150.thaumaturge.registry.TTGolemParts;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.golems.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class WorkbenchGameTests {
  record Fixture(
      GolemWorkbench.BenchEntity bench,
      EntityThaumaturgeGolem golem,
      Player owner,
      BarrelBlockEntity destination) {}

  static Fixture bench(GameTestHelper h, boolean smart) {
    GolemGameTests.floor(h);
    BlockPos p = GolemGameTests.BENCH;
    h.setBlock(p, GolemWorkbench.BLOCK.get());
    GolemWorkbench.BenchEntity bench = h.getBlockEntity(p);
    h.setBlock(p.north(), Blocks.BARREL);
    BarrelBlockEntity out = h.getBlockEntity(p.north());
    Player owner = GolemGameTests.owner(h, p);
    var golem = GolemGameTests.golem(h, owner, p.south(), p.south(), smart);
    h.assertTrue(
        bench.link(owner, golem).equals("linked"), "real host Empty-compatible worker linked");
    return new Fixture(bench, golem, owner, out);
  }

  static void pattern(GolemWorkbench.BenchEntity b, int page, Item... cells) {
    for (int i = 0; i < cells.length; i++)
      b.pattern(page, i, cells[i] == Items.AIR ? ItemStack.EMPTY : new ItemStack(cells[i]));
  }

  static void torch(GolemWorkbench.BenchEntity b, int count) {
    pattern(b, 0, Items.COAL, Items.AIR, Items.AIR, Items.STICK);
    b.ingredients.setStackInSlot(0, new ItemStack(Items.COAL, count));
    b.ingredients.setStackInSlot(1, new ItemStack(Items.STICK, count));
  }

  static void cake(GolemWorkbench.BenchEntity b) {
    pattern(
        b,
        0,
        Items.MILK_BUCKET,
        Items.MILK_BUCKET,
        Items.MILK_BUCKET,
        Items.SUGAR,
        Items.EGG,
        Items.SUGAR,
        Items.WHEAT,
        Items.WHEAT,
        Items.WHEAT);
    for (int i = 0; i < 3; i++) b.ingredients.setStackInSlot(i, new ItemStack(Items.MILK_BUCKET));
    b.ingredients.setStackInSlot(3, new ItemStack(Items.SUGAR, 2));
    b.ingredients.setStackInSlot(4, new ItemStack(Items.EGG));
    b.ingredients.setStackInSlot(5, new ItemStack(Items.WHEAT, 3));
  }

  static int loose(GameTestHelper h, Item item) {
    return h
        .getLevel()
        .getEntitiesOfClass(
            ItemEntity.class,
            new AABB(h.absolutePos(GolemGameTests.BENCH)).inflate(5),
            e -> e.isAlive() && e.getItem().is(item))
        .stream()
        .mapToInt(e -> e.getItem().getCount())
        .sum();
  }

  static int stored(GolemWorkbench.BenchEntity b, Item item) {
    int count = 0;
    for (int i = 0; i < b.ingredients.getSlots(); i++)
      if (b.ingredients.getStackInSlot(i).is(item))
        count += b.ingredients.getStackInSlot(i).getCount();
    return count;
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void realWorkerConsumesOnlyRegisteredRecipeAndRoutesOutput(GameTestHelper h) {
    var f = bench(h, false);
    pattern(f.bench, 0, Items.COAL, Items.AIR, Items.AIR, Items.STICK);
    h.runAfterDelay(
        45,
        () -> {
          h.assertValueEqual(f.bench.crafts, 0, "ghosts without ingredients create nothing");
          h.assertTrue(f.destination.isEmpty(), "no virtual outputs");
          torch(f.bench, 1);
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              f.destination.getItem(0).is(Items.TORCH),
              "ordinary registered torch recipe physically produced");
          h.assertValueEqual(f.destination.getItem(0).getCount(), 4, "paid recipe count");
          h.assertValueEqual(
              stored(f.bench, Items.COAL) + stored(f.bench, Items.STICK),
              0,
              "real ingredients consumed once");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void distantRealWorkerWalksWithinSamePhysicalCraftingReach(GameTestHelper h) {
    GolemGameTests.floor(h);
    BlockPos p = GolemGameTests.BENCH;
    h.setBlock(p, GolemWorkbench.BLOCK.get());
    GolemWorkbench.BenchEntity bench = h.getBlockEntity(p);
    h.setBlock(p.north(), Blocks.BARREL);
    BarrelBlockEntity output = h.getBlockEntity(p.north());
    var owner = GolemGameTests.owner(h, p);
    var g = GolemGameTests.golem(h, owner, p.north(6), p.north(6), false);
    var start = g.position();
    torch(bench, 1);
    h.runAfterDelay(
        2,
        () -> {
          g.restrictTo(h.absolutePos(p.north(2)), 32);
          h.assertTrue(
              bench.link(owner, g).equals("linked"),
              "far worker links with valid nearby resting location");
          h.assertFalse(
              GolemModule.withinWorkReach(g, h.absolutePos(p)),
              "worker starts outside physical crafting reach");
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              output.getItem(0).is(Items.TORCH),
              "real worker approached and crafted through the shared reach boundary");
          h.assertTrue(
              g.position().distanceToSqr(start) > 4, "actual navigation covered several blocks");
          h.assertValueEqual(bench.crafts, 1, "one paid recipe only");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void basicRejectsMixedThreeByThreeAdvancedCraftsAndDropsContainers(
      GameTestHelper h) {
    var f = bench(h, false);
    cake(f.bench);
    h.runAfterDelay(
        50,
        () -> {
          h.assertValueEqual(f.bench.crafts, 0, "basic golem refuses mixed full-grid cake");
          h.assertValueEqual(stored(f.bench, Items.MILK_BUCKET), 3, "refusal conserves buckets");
          f.golem.setProperties(f.golem.properties().withHead(TTGolemParts.HEAD_SMART.get()));
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              f.destination.getItem(0).is(Items.CAKE), "actual Smart host head unlocks recipe");
          h.assertValueEqual(f.bench.crafts, 1, "cake crafted once");
          h.assertValueEqual(loose(h, Items.BUCKET), 3, "three real containers dropped safely");
          h.assertValueEqual(stored(f.bench, Items.MILK_BUCKET), 0, "milk buckets truly paid");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void orderUpgradeRetainsActualCakeBuckets(GameTestHelper h) {
    var f = bench(h, true);
    f.bench.install(Direction.EAST, GolemWorkbench.Upgrade.ORDER);
    cake(f.bench);
    h.succeedWhen(
        () -> {
          h.assertTrue(f.destination.getItem(0).is(Items.CAKE), "actual cake output");
          h.assertValueEqual(
              stored(f.bench, Items.BUCKET), 3, "Order retains all three container remainders");
          h.assertValueEqual(loose(h, Items.BUCKET), 0, "no duplicate floor buckets");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void basicAllowsFullGridOfOneIngredient(GameTestHelper h) {
    var f = bench(h, false);
    Item[] iron = new Item[9];
    Arrays.fill(iron, Items.IRON_INGOT);
    pattern(f.bench, 0, iron);
    f.bench.ingredients.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 9));
    h.succeedWhen(
        () -> {
          h.assertTrue(
              f.destination.getItem(0).is(Items.IRON_BLOCK),
              "basic golem handles nine identical ingredients");
          h.assertValueEqual(stored(f.bench, Items.IRON_INGOT), 0, "all nine real ingots consumed");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void fireHasTwoExtraPatternsSelectsPayableRecipeAndRemovalKeepsStorage(
      GameTestHelper h) {
    var f = bench(h, false);
    h.assertTrue(f.bench.install(Direction.NORTH, GolemWorkbench.Upgrade.FIRE), "first Fire");
    h.assertTrue(f.bench.install(Direction.EAST, GolemWorkbench.Upgrade.FIRE), "second Fire");
    h.assertFalse(
        f.bench.install(Direction.SOUTH, GolemWorkbench.Upgrade.FIRE), "third Fire refused");
    pattern(f.bench, 0, Items.DIAMOND);
    pattern(f.bench, 1, Items.GOLD_BLOCK);
    pattern(f.bench, 2, Items.COAL, Items.AIR, Items.AIR, Items.STICK);
    f.bench.ingredients.setStackInSlot(0, new ItemStack(Items.COAL));
    f.bench.ingredients.setStackInSlot(1, new ItemStack(Items.STICK));
    h.succeedWhen(
        () -> {
          h.assertTrue(
              f.destination.getItem(0).is(Items.TORCH),
              "third pattern selected using actual ingredients");
          f.bench.ingredients.setStackInSlot(4, new ItemStack(Items.EMERALD, 7));
          f.bench.removeUpgrade(Direction.EAST, f.owner);
          h.assertValueEqual(f.bench.patternCount(), 2, "removal hides extra page");
          h.assertValueEqual(
              stored(f.bench, Items.EMERALD), 7, "upgrade removal retains real stored contents");
          h.assertTrue(f.bench.patterns.get(18).is(Items.COAL), "dormant ghost pattern retained");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 140)
  public static void entropyUsesActualValidRecipeAndExplicitPermittedProduct(GameTestHelper h) {
    var f = bench(h, false);
    h.assertTrue(
        f.bench.install(Direction.EAST, GolemWorkbench.Upgrade.ENTROPY), "unique entropy installs");
    h.assertFalse(
        f.bench.install(Direction.WEST, GolemWorkbench.Upgrade.ENTROPY), "second entropy refused");
    pattern(f.bench, 0, Items.OAK_LOG);
    f.bench.ingredients.setStackInSlot(0, new ItemStack(Items.OAK_LOG));
    f.bench.ingredients.setStackInSlot(1, new ItemStack(Items.DIAMOND));
    // Consume the nominal one first; then the placeholder remains in slot zero with an invalid
    // pattern product filter.
    f.bench.filter =
        new StackFilters.Filter(
            List.of(new StackFilters.Sample(new ItemStack(Items.BIRCH_PLANKS), 1)),
            false,
            new StackFilters.MatchOptions(true, false));
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(
              f.destination.isEmpty(), "diamond substitution cannot fabricate permitted planks");
          h.assertValueEqual(stored(f.bench, Items.DIAMOND), 1, "invalid alternatives remain real");
          f.bench.ingredients.setStackInSlot(2, new ItemStack(Items.BIRCH_LOG));
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              f.destination.getItem(0).is(Items.BIRCH_PLANKS),
              "real birch recipe yields explicitly allowed replacement");
          h.assertValueEqual(f.destination.getItem(0).getCount(), 4, "registered result count");
          h.assertValueEqual(
              stored(f.bench, Items.OAK_LOG), 1, "placeholder is not consumed as a substitute");
          h.assertValueEqual(
              stored(f.bench, Items.BIRCH_LOG), 0, "actual replacement ingredient consumed");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 170)
  public static void poweredFetterPausesCraftingWhilePhysicalOutputStillRoutes(GameTestHelper h) {
    var f = bench(h, false);
    torch(f.bench, 1);
    GolemGameTests.fetter(h, GolemGameTests.BENCH.below());
    f.bench.output.setStackInSlot(0, new ItemStack(Items.STONE, 5));
    h.runAfterDelay(
        50,
        () -> {
          h.assertValueEqual(f.bench.crafts, 0, "real powered host fetter pauses assembly");
          h.assertValueEqual(
              f.destination.getItem(0).getCount(), 5, "existing product transport continues");
          h.setBlock(GolemGameTests.BENCH.below().north(), Blocks.AIR);
        });
    h.succeedWhen(
        () -> {
          h.assertValueEqual(f.bench.crafts, 1, "deactivation resumes actual crafting");
          h.assertValueEqual(
              stored(f.bench, Items.COAL), 0, "only resumed work consumes ingredient");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 140)
  public static void fabriCoConsumesRealUnblockedJarAndExhaustionSlowsSustainedWork(
      GameTestHelper h) {
    var f = bench(h, false);
    torch(f.bench, 10);
    f.golem.getPersistentData().putInt("autoturgy_fatigue", 110);
    BlockPos p = GolemGameTests.BENCH.east(2);
    h.setBlock(p, LogisticsGameTests.host("jar_normal"));
    BlockEntityJar jar = h.getBlockEntity(p);
    jar.setStorageContents(TTAspects.FABRICO, 1);
    h.succeedWhen(
        () -> {
          h.assertTrue(f.bench.crafts > 0, "actual Fabrico permits tired worker to continue");
          h.assertValueEqual(jar.amount(), 0, "one real essentia consumed");
          h.assertTrue(f.bench.fatigue(f.golem) < 100, "paid recovery changes actual endurance");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 130)
  public static void bracedFabricoRefusesWithdrawalAndEmptyJarLeavesExhaustion(GameTestHelper h) {
    var f = bench(h, false);
    torch(f.bench, 30);
    f.bench.install(Direction.EAST, GolemWorkbench.Upgrade.AIR);
    f.golem.getPersistentData().putInt("autoturgy_fatigue", 120);
    BlockPos p = GolemGameTests.BENCH.east(2);
    h.setBlock(p, LogisticsGameTests.host("jar_normal"));
    BlockEntityJar jar = h.getBlockEntity(p);
    jar.setStorageContents(TTAspects.FABRICO, 1);
    jar.setBraced(true);
    h.runAfterDelay(
        45,
        () -> {
          h.assertValueEqual(
              f.bench.crafts, 0, "braced real Fabrico cannot rescue exhausted worker");
          h.assertValueEqual(jar.amount(), 1, "blocked source conserved");
          jar.setBraced(false);
        });
    h.runAfterDelay(
        90,
        () -> {
          h.assertValueEqual(jar.amount(), 0, "unbraced source paid one essentia");
          h.assertTrue(f.bench.crafts > 0, "paid endurance permits real work");
          int before = f.bench.crafts;
          h.runAfterDelay(
              25,
              () -> {
                h.assertTrue(
                    f.bench.crafts - before <= 2, "exhausted empty jar limits sustained work");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void airEarthWaterUseActualSpeedStrengthAndWetRecovery(GameTestHelper h) {
    var f = bench(h, false);
    int base = f.bench.interval(f.golem), effort = f.bench.effort();
    f.bench.install(Direction.NORTH, GolemWorkbench.Upgrade.AIR);
    h.assertTrue(f.bench.interval(f.golem) < base, "Air uses real host movement speed");
    h.assertTrue(f.bench.effort() > effort, "Air raises endurance cost");
    int air = f.bench.interval(f.golem), airEffort = f.bench.effort();
    f.bench.install(Direction.EAST, GolemWorkbench.Upgrade.EARTH);
    h.assertTrue(f.bench.interval(f.golem) < air, "Earth uses actual nonzero attack strength");
    h.assertTrue(f.bench.effort() < airEffort, "Earth improves endurance");
    f.bench.install(Direction.WEST, GolemWorkbench.Upgrade.WATER);
    h.setBlock(GolemGameTests.BENCH.east(), Blocks.WATER);
    f.golem.getPersistentData().putInt("autoturgy_fatigue", 40);
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(
              f.bench.fatigue(f.golem) <= 32, "adjacent real water improves resting recovery");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void actualWorkbenchMenuKeepsGhostsNonconsumingAndChecksOwnership(
      GameTestHelper h) {
    var f = bench(h, false);
    var menu = new WorkbenchMenu(7, f.owner.getInventory(), f.bench);
    menu.setCarried(new ItemStack(Items.COAL, 11));
    menu.clicked(0, 0, ClickType.PICKUP, f.owner);
    h.assertValueEqual(menu.getCarried().getCount(), 11, "cursor remains physical ownership");
    h.assertValueEqual(f.bench.patterns.getFirst().getCount(), 1, "one ghost sample");
    h.assertTrue(
        f.bench.ingredients.getStackInSlot(0).isEmpty(), "ghosts do not fill real storage");
    h.assertFalse(menu.clickMenuButton(f.owner, 1), "uninstalled Fire page refused");
    h.assertFalse(
        menu.stillValid(GolemGameTests.owner(h, GolemGameTests.BENCH)),
        "foreign player cannot edit bench");
    var saved = f.bench.saveWithFullMetadata(h.getLevel().registryAccess());
    f.bench.loadWithComponents(saved, h.getLevel().registryAccess());
    h.assertTrue(f.bench.patterns.getFirst().is(Items.COAL), "ghost pattern persists");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void bellAndWorkbenchRejectCrossDimensionAndActualTransitDetaches(
      GameTestHelper h) {
    var overworld = h.getLevel();
    var nether = overworld.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
    BlockPos p = h.absolutePos(GolemGameTests.BENCH).atY(160);
    var chunk = new net.minecraft.world.level.ChunkPos(p);
    nether.setChunkForced(chunk.x, chunk.z, true);
    nether.getChunkAt(p);
    var owner = GolemGameTests.owner(h, GolemGameTests.BENCH);
    owner.moveTo(p.getCenter());
    EntityThaumaturgeGolem[] moved = {null};
    try {
      overworld.setBlockAndUpdate(p, GolemWorkbench.BLOCK.get().defaultBlockState());
      GolemWorkbench.BenchEntity bench = (GolemWorkbench.BenchEntity) overworld.getBlockEntity(p);
      torch(bench, 1);
      var foreign =
          com.leclowndu93150.thaumaturge.registry.TTEntities.THAUMATURGE_GOLEM.get().create(nether);
      foreign.setProperties(
          com.leclowndu93150.thaumaturge.content.golem.GolemProperties.createDefault()
              .withMaterial(TTGolemParts.IRON.get())
              .withArms(TTGolemParts.ARMS_FINE.get()));
      foreign.setOwner(owner);
      foreign.restrictTo(p.south(), 32);
      foreign.moveTo(p.getX() + .5, p.getY(), p.getZ() + 1.5, 0, 0);
      h.assertTrue(
          bench.link(owner, foreign).equals("dimension"),
          "same coordinates and owner cannot link across dimensions");
      var bell = new ItemStack(GolemModule.BELL.get());
      var data = bench.location().save();
      data.putUUID("device", bench.identity);
      com.sanhiruzu.autoturgy.sensing.SensingModule.itemData(bell, data);
      bell.interactLivingEntity(owner, foreign, net.minecraft.world.InteractionHand.MAIN_HAND);
      h.assertTrue(bench.links.isEmpty(), "cross-dimension bell use creates no remote assignment");
      bench.work(foreign);
      h.assertValueEqual(bench.crafts, 0, "actual remote host entity cannot craft here");
      var local =
          com.leclowndu93150.thaumaturge.registry.TTEntities.THAUMATURGE_GOLEM
              .get()
              .create(overworld);
      local.setProperties(foreign.properties());
      local.setOwner(owner);
      local.setValidSpawn();
      local.restrictTo(p.south(), 32);
      local.moveTo(p.getX() + .5, p.getY(), p.getZ() + 1.5, 0, 0);
      local.updateEntityAttributes();
      overworld.addFreshEntity(local);
      h.assertTrue(bench.link(owner, local).equals("linked"), "same-level real host worker links");
      moved[0] =
          (EntityThaumaturgeGolem)
              local.changeDimension(
                  new net.minecraft.world.level.portal.DimensionTransition(
                      nether,
                      local.position(),
                      net.minecraft.world.phys.Vec3.ZERO,
                      0,
                      0,
                      net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
      h.assertTrue(
          moved[0] != null && moved[0].level() == nether,
          "actual host entity transitioned dimension");
      h.assertTrue(
          bench.links.isEmpty() && GolemModule.linked(moved[0]) == null,
          "real transit clears old device and copied entity link");
      h.assertFalse(
          moved[0].getPersistentData().contains(GolemModule.LINK),
          "no stale cross-world attunement");
      bench.work(moved[0]);
      h.assertValueEqual(bench.crafts, 0, "transferred golem cannot trigger remote assembly");
      h.assertValueEqual(
          stored(bench, Items.COAL), 1, "refused cross-world work conserves ingredients");
      h.succeed();
    } finally {
      if (moved[0] != null) moved[0].discard();
      overworld.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
      nether.setChunkForced(chunk.x, chunk.z, false);
    }
  }
}
