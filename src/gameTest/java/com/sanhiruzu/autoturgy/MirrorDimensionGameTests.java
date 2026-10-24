package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirror;
import com.sanhiruzu.autoturgy.common.LoadedTargets;
import com.sanhiruzu.autoturgy.signal.RedcrystalNetwork;
import com.sanhiruzu.autoturgy.transport.MirrorRoutes;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class MirrorDimensionGameTests {
  @GameTest(template = "empty", timeoutTicks = 40)
  public static void mercurialIdenticalCoordinatesAcrossDimensionsKeepIdentityAndDirections(
      GameTestHelper h) {
    var overworld = h.getLevel();
    var nether = overworld.getServer().getLevel(Level.NETHER);
    BlockPos mirrorPos = h.absolutePos(LogisticsGameTests.A).atY(64),
        crystalPos = mirrorPos.south(),
        sourcePos = crystalPos.west();
    // Loading here creates an isolated test fixture. Production resolution never calls getChunk.
    nether.getChunkAt(mirrorPos);
    var mirrorBlock = LogisticsGameTests.host("mirror");
    var crystalBlock = RedcrystalNetwork.BLOCKS.get(RedcrystalNetwork.Variant.MERCURIAL).get();
    try {
      for (var world : java.util.List.of(overworld, nether)) {
        world.setBlockAndUpdate(mirrorPos.below(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(crystalPos.below(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(mirrorPos, mirrorBlock.defaultBlockState());
        world.setBlockAndUpdate(crystalPos, crystalBlock.defaultBlockState());
      }
      BlockEntityMirror entrance = (BlockEntityMirror) overworld.getBlockEntity(mirrorPos),
          exit = (BlockEntityMirror) nether.getBlockEntity(mirrorPos);
      entrance.linked = exit.linked = true;
      entrance.link = GlobalPos.of(nether.dimension(), mirrorPos);
      exit.link = GlobalPos.of(overworld.dimension(), mirrorPos);
      var a = (RedcrystalNetwork.CrystalEntity) overworld.getBlockEntity(crystalPos);
      var b = (RedcrystalNetwork.CrystalEntity) nether.getBlockEntity(crystalPos);
      a.target = new LoadedTargets.Target(overworld.dimension(), mirrorPos, Direction.UP);
      b.target = new LoadedTargets.Target(nether.dimension(), mirrorPos, Direction.UP);
      overworld.setBlockAndUpdate(sourcePos, Blocks.REDSTONE_BLOCK.defaultBlockState());
      RedcrystalNetwork.recompute(a);
      h.assertValueEqual(a.internalSignal(), 15, "source dimension active");
      h.assertValueEqual(
          b.internalSignal(), 15, "same coordinate in another dimension is a distinct node");
      b.settings[0] &= ~(1 << (Direction.NORTH.ordinal() * 2));
      RedcrystalNetwork.recompute(a);
      h.assertValueEqual(b.internalSignal(), 0, "mirror-facing receive permission is respected");
      b.settings[0] |= 1 << (Direction.NORTH.ordinal() * 2);
      RedcrystalNetwork.recompute(a);
      h.assertValueEqual(b.internalSignal(), 15, "receive permission restores route");
      h.assertTrue(
          MirrorRoutes.insert(overworld.getServer(), a.target, new ItemStack(Items.STONE, 3), false)
              .isEmpty(),
          "loaded cross-dimension item route accepted");
      h.assertValueEqual(
          LogisticsGameTests.queuedAt(exit), 3, "real remote mirror owns transported items");
      overworld.setBlockAndUpdate(sourcePos, Blocks.AIR.defaultBlockState());
      RedcrystalNetwork.recompute(a);
      h.assertValueEqual(a.internalSignal(), 0, "source-free entrance turns off");
      h.assertValueEqual(b.internalSignal(), 0, "source-free remote cycle turns off");
      exit.linked = false;
      exit.link = null;
      h.assertValueEqual(
          MirrorRoutes.insert(overworld.getServer(), a.target, new ItemStack(Items.STONE, 3), false)
              .getCount(),
          3,
          "broken reciprocal link refuses intact stack");
      h.succeed();
    } finally {
      for (var world : java.util.List.of(overworld, nether)) {
        world.setBlockAndUpdate(mirrorPos, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(crystalPos, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(sourcePos, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(mirrorPos.below(), Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(crystalPos.below(), Blocks.AIR.defaultBlockState());
      }
    }
  }
}
