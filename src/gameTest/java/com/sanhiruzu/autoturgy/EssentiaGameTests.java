package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.essentia.*;
import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar;
import com.leclowndu93150.thaumaturge.content.infusion.EssentiaSources;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.essentia.EssentiaModule;
import com.sanhiruzu.autoturgy.host.ThaumaturgeIntegration;
import com.sanhiruzu.autoturgy.sensing.SensingModule;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class EssentiaGameTests {
  static final BlockPos L = new BlockPos(5, 4, 5), J = new BlockPos(2, 3, 8);

  static EssentiaModule.LocusEntity structure(GameTestHelper h) {
    BlockPos base = L.below(2);
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++) {
        BlockPos p = base.offset(x, 0, z);
        h.setBlock(p.below(), Blocks.STONE);
        h.setBlock(
            p,
            x == 0 && z == 0
                ? Blocks.LAPIS_BLOCK
                : Math.max(Math.abs(x), Math.abs(z)) == 1
                    ? Blocks.WATER
                    : EssentiaModule.host("plank_silverwood"));
      }
    h.setBlock(L.below(), EssentiaModule.host("crystal_aqua"));
    h.setBlock(L, EssentiaModule.LOCUS.get());
    return h.getBlockEntity(L);
  }

  static BlockEntityJar jar(GameTestHelper h, BlockPos p, int n) {
    h.setBlock(p.below(), Blocks.STONE);
    h.setBlock(p, EssentiaModule.host("jar_normal"));
    BlockEntityJar jar = h.getBlockEntity(p);
    jar.setStorageContents(TTAspects.AQUA, n);
    return jar;
  }

  static LoadedTargets.Target eye(GameTestHelper h, BlockPos p) {
    return new LoadedTargets.Target(h.getLevel().dimension(), h.absolutePos(p), Direction.UP);
  }

  static EssentiaModule.AggregatorEntity aggregator(GameTestHelper h) {
    h.setBlock(L.below(), EssentiaModule.AGGREGATOR.get());
    return h.getBlockEntity(L.below());
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void locusRequiresResourcesAndCountsEmptyLabels(GameTestHelper h) {
    var locus = structure(h);
    var one = jar(h, J, 10);
    var two = jar(h, J.east(), 0);
    two.setAspectFilter(TTAspects.AQUA);
    h.assertTrue(locus.addEye(eye(h, J)), "valid connected jar group accepts real eye");
    h.assertValueEqual(locus.discover().size(), 2, "connected jars discovered once");
    var reading = locus.reading();
    h.assertValueEqual(
        reading.aspects().getFirst().amount(), 10, "amount is original physical total");
    h.assertValueEqual(
        reading.aspects().getFirst().capacity(),
        one.capacity() + two.capacity(),
        "empty label adds aspect capacity");
    h.setBlock(L.below(2).north(), Blocks.AIR);
    h.assertFalse(locus.reading().valid(), "removing required water invalidates construct");
    h.assertTrue(locus.contents().isEmpty(), "invalid construct grants no contents");
    h.assertValueEqual(one.amount(), 10, "failure never changes physical jar");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void locusEyeLimitExpansionDedupAndPersistence(GameTestHelper h) {
    var locus = structure(h);
    for (int i = 0; i < 14; i++) jar(h, J.offset(i % 7, 0, i / 7), 1);
    h.assertTrue(locus.addEye(eye(h, J)), "baseline eye");
    h.assertFalse(locus.addEye(eye(h, J.east())), "one eye baseline limit");
    h.assertValueEqual(locus.discover().size(), 12, "baseline twelve jar limit");
    h.setBlock(L.below(2).west(3), EssentiaModule.host("crystal_aqua"));
    h.assertTrue(locus.addEye(eye(h, J.east())), "extra cluster adds eye");
    h.assertValueEqual(
        locus.discover().size(), 14, "two overlapping groups deduplicate physical jars");
    var save = locus.saveWithFullMetadata(h.getLevel().registryAccess());
    locus.eyes.clear();
    locus.loadWithComponents(save, h.getLevel().registryAccess());
    h.assertValueEqual(locus.eyes.size(), 2, "bound eyes survive save load");
    h.assertValueEqual(locus.contents().totalAmount(), 14, "no virtual duplicate contents on load");
    h.setBlock(J, Blocks.AIR);
    h.assertValueEqual(locus.contents().totalAmount(), 13, "removed jar immediately excluded");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void embeddedAggregatorRealHostStorageAndInfusionNeverDoubleDrain(
      GameTestHelper h) {
    var locus = structure(h);
    var source = jar(h, J, 10);
    locus.addEye(eye(h, J));
    var aggregate = aggregator(h);
    h.assertTrue(
        EssentiaModule.structure(h.getLevel(), h.absolutePos(L)).valid(),
        "aggregator retains central cluster resource");
    var cap =
        h.getLevel()
            .getCapability(EssentiaCapabilities.STORAGE, h.absolutePos(L.below()), Direction.WEST);
    h.assertValueEqual(
        cap.extract(Aspects.resolve(h.getLevel(), TTAspects.AQUA), 4, true),
        4,
        "real host simulation");
    h.assertValueEqual(source.amount(), 10, "simulation preserves jar");
    h.assertValueEqual(
        cap.extract(Aspects.resolve(h.getLevel(), TTAspects.AQUA), 4, false),
        4,
        "real host extraction");
    h.assertValueEqual(source.amount(), 6, "original source drained once");
    var infusion = new EssentiaSources(h.absolutePos(L.east(2)), 8).ignoring(be -> be != aggregate);
    h.assertTrue(
        infusion.drain(h.getLevel(), Aspects.resolve(h.getLevel(), TTAspects.AQUA), 0),
        "genuine host infusion source discovery uses IAspectSource bridge");
    h.assertValueEqual(source.amount(), 5, "infusion drained original jar once");
    h.assertFalse(
        aggregate.drain(Aspects.resolve(h.getLevel(), TTAspects.AQUA), 6),
        "oversized exact drain rejected");
    h.assertValueEqual(source.amount(), 5, "failed exact request preserves source");
    h.setBlock(L.below(), Blocks.AIR);
    h.assertTrue(aggregate.isBlocked(), "removed aggregator fails source validation");
    h.setBlock(L.below(), EssentiaModule.host("crystal_aqua"));
    h.assertValueEqual(
        locus.contents().totalAmount(),
        5,
        "restoring plain cluster leaves reading and original content");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void visAndTallyReadLocusAndBelowWaterPositions(GameTestHelper h) {
    var locus = structure(h);
    var source = jar(h, J, 20);
    locus.addEye(eye(h, J));
    BlockPos reader = L.below(3).north();
    h.setBlock(reader, SensingModule.VIS.get());
    SensingModule.VisEntity vis = h.getBlockEntity(reader);
    h.assertTrue(vis.reading().valid(), "unlinked reader under pool discovers locus");
    h.assertValueEqual(vis.reading().aspects().getFirst().amount(), 20, "reader sees real amount");
    var water =
        ThaumaturgeIntegration.read(h.getLevel(), h.absolutePos(reader.above()), Direction.DOWN);
    h.assertTrue(water.valid(), "bound eye aimed at pool water reads construct");
    long tally =
        com.sanhiruzu.autoturgy.sensing.TallyService.count(
            h.getLevel(),
            h.absolutePos(reader),
            eye(h, L),
            StackFilters.Filter.all(),
            0,
            new HashSet<>(),
            0);
    h.assertValueEqual(tally, 20L, "tally construct total");
    h.assertValueEqual(source.amount(), 20, "all reads are nonconsuming");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 160)
  public static void actualHostTubesExtractAggregatorIntoLabeledJar(GameTestHelper h) {
    var locus = structure(h);
    var source = jar(h, J, 20);
    locus.addEye(eye(h, J));
    aggregator(h);
    for (int x = 6; x <= 9; x++) h.setBlock(new BlockPos(x, 3, 5), EssentiaModule.host("tube"));
    BlockPos dest = new BlockPos(9, 2, 5);
    var target = jar(h, dest, 0);
    target.setAspectFilter(TTAspects.AQUA);
    h.runAfterDelay(
        120,
        () -> {
          h.assertTrue(target.amount() > 0, "actual host tube ticks deliver real essentia");
          h.assertValueEqual(
              source.amount() + target.amount(), 20, "tube source and target conserve exact total");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void actualHostEssentiaMirrorDrawsDistantMonitoredJarThroughAggregator(
      GameTestHelper h) {
    var locus = structure(h);
    BlockPos remote = L.south(15);
    var source = jar(h, remote, 9);
    h.assertTrue(locus.addEye(eye(h, remote)), "distant original jar in locus range");
    aggregator(h);
    BlockPos entrance = L.north(3), exit = L.north(20);
    for (BlockPos p : List.of(entrance, exit)) {
      h.setBlock(p.north(), Blocks.STONE);
      h.setBlock(
          p,
          EssentiaModule.host("mirror_essentia")
              .defaultBlockState()
              .setValue(
                  com.leclowndu93150.thaumaturge.content.device.mirror.BlockMirror.FACING,
                  Direction.SOUTH));
    }
    com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirrorEssentia
        a = h.getBlockEntity(entrance),
        b = h.getBlockEntity(exit);
    a.linked = b.linked = true;
    a.link = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(exit));
    b.link = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(entrance));
    h.assertTrue(
        b.drain(Aspects.resolve(h.getLevel(), TTAspects.AQUA), 1),
        "real linked essence mirror uses host source scan at other end");
    h.assertValueEqual(
        source.amount(),
        8,
        "original jar outside mirror scan range drained only through aggregator");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void blockedJarSidePermissionsAndUnloadedEyesFailClosed(GameTestHelper h) {
    var locus = structure(h);
    var source = jar(h, J, 10);
    locus.addEye(eye(h, J));
    var aggregate = aggregator(h);
    var aqua = Aspects.resolve(h.getLevel(), TTAspects.AQUA);
    source.setBraced(true);
    h.assertValueEqual(
        aggregate.extract(aqua, 5, false), 0, "jar infusion brace blocks aggregate extraction");
    source.setBraced(false);
    locus.eyes.clear();
    locus.eyes.add(
        new LoadedTargets.Target(h.getLevel().dimension(), h.absolutePos(J), Direction.DOWN));
    h.assertValueEqual(
        aggregate.extract(aqua, 5, false),
        0,
        "bound eye access side respects jar output permission");
    BlockPos distant = h.absolutePos(J).offset(100000, 0, 0);
    locus.eyes.clear();
    locus.eyes.add(new LoadedTargets.Target(h.getLevel().dimension(), distant, Direction.UP));
    h.assertTrue(locus.discover().isEmpty(), "unloaded eye omitted");
    h.assertFalse(h.getLevel().hasChunkAt(distant), "locus never force loads");
    h.assertValueEqual(source.amount(), 10, "all refusals conserve actual source");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void actualHostReservoirJoinsConnectedJarReading(GameTestHelper h) {
    var locus = structure(h);
    jar(h, J, 10);
    BlockPos p = J.east();
    h.setBlock(p, EssentiaModule.host("essentia_reservoir"));
    com.leclowndu93150.thaumaturge.content.essentia.reservoir.BlockEntityEssentiaReservoir
        reservoir = h.getBlockEntity(p);
    reservoir.setAspects(AspectList.EMPTY.add(Aspects.resolve(h.getLevel(), TTAspects.IGNIS), 7));
    h.assertTrue(locus.addEye(eye(h, J)), "jar seed");
    h.assertValueEqual(locus.discover().size(), 2, "actual reservoir eligible");
    h.assertValueEqual(
        locus.contents().totalAmount(), 17, "reservoir actual mixed contents counted");
    h.assertTrue(
        locus.reading().aspects().stream()
            .anyMatch(a -> a.id().equals("thaumaturge:ignis") && a.capacity() > 0),
        "reservoir reports real capacity");
    h.succeed();
  }
}
