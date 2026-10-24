package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.content.golem.*;
import com.leclowndu93150.thaumaturge.registry.*;
import com.sanhiruzu.autoturgy.common.LoadedTargets;
import com.sanhiruzu.autoturgy.golems.GolemModule;
import com.sanhiruzu.autoturgy.inventarium.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class GolemUnloadGameTests {
  @GameTest(template = "empty", timeoutTicks = 1000)
  public static void actualDeathWithCoreChunkGenuinelyAbsentReleasesLeaseOnReload(
      GameTestHelper h) {
    var world = h.getLevel().getServer().getLevel(Level.NETHER);
    var marker = h.absolutePos(BlockPos.ZERO);
    BlockPos
        corePos = new BlockPos((marker.getX() >> 4) * 16 + 8, 160, (marker.getZ() >> 4) * 16 + 8),
        workerPos = corePos.east(128),
        sourcePos = corePos.east();
    ChunkPos coreChunk = new ChunkPos(corePos), workerChunk = new ChunkPos(workerPos);
    world.setChunkForced(coreChunk.x, coreChunk.z, true);
    world.setChunkForced(workerChunk.x, workerChunk.z, true);
    // Only fixture setup deliberately loads chunks. The absence poll never requests one.
    world.getChunkAt(corePos);
    world.getChunkAt(workerPos);
    for (BlockPos p :
        BlockPos.betweenClosed(workerPos.offset(-2, 0, -2), workerPos.offset(2, 3, 2)))
      world.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
    world.setBlockAndUpdate(workerPos.below(), Blocks.STONE.defaultBlockState());
    world.setBlockAndUpdate(corePos, InventariumModule.CORE.get().defaultBlockState());
    world.setBlockAndUpdate(sourcePos, Blocks.BARREL.defaultBlockState());
    InventariumModule.CoreEntity core =
        (InventariumModule.CoreEntity) world.getBlockEntity(corePos);
    BarrelBlockEntity source = (BarrelBlockEntity) world.getBlockEntity(sourcePos);
    source.setItem(0, new ItemStack(Items.STONE, 7));
    var owner = new ProgressionGameTests.TestPlayer(world);
    var g = TTEntities.THAUMATURGE_GOLEM.get().create(world);
    g.setProperties(
        GolemProperties.createDefault()
            .withMaterial(TTGolemParts.IRON.get())
            .withArms(TTGolemParts.ARMS_FINE.get()));
    g.setOwner(owner);
    g.setValidSpawn();
    g.restrictTo(workerPos, 32);
    g.moveTo(workerPos.getX() + .5, workerPos.getY(), workerPos.getZ() + .5, 0, 0);
    g.setNoAi(true);
    g.setInvulnerable(true);
    world.addFreshEntity(g);
    var at = new LoadedTargets.Target(world.dimension(), sourcePos, Direction.UP);
    var snapshot = InventoryIndex.scan(world, List.of(at), 81);
    var order =
        core.orders
            .request(owner.getUUID(), UUID.randomUUID(), new ItemStack(Items.STONE), 7, at)
            .orElseThrow();
    core.orders.reconcile(snapshot);
    var lease =
        core.orders.claim(order.id, g.getUUID(), snapshot.sources().getFirst(), 7).orElseThrow();
    var sourceHandler =
        world.getCapability(Capabilities.ItemHandler.BLOCK, sourcePos, Direction.UP);
    ItemStack physical = sourceHandler.extractItem(0, 7, false), evidence = physical.copy();
    h.assertTrue(
        g.hands().hold(physical).isEmpty(), "actual extracted items entered real host hands");
    core.orders.collected(lease.id, g.getUUID(), evidence, 7);
    core.orders.releaseUncollected(lease.id, g.getUUID());
    var custody = new LoadedTargets.Target(world.dimension(), corePos, Direction.UP).save();
    custody.putUUID("core", core.identity);
    g.getPersistentData().put(GolemModule.CUSTODY, custody);
    core.changed();
    UUID identity = core.identity;
    Runnable cleanup =
        () -> {
          world.setChunkForced(coreChunk.x, coreChunk.z, true);
          world.getChunkAt(corePos);
          world.setBlockAndUpdate(corePos, Blocks.AIR.defaultBlockState());
          world.setBlockAndUpdate(sourcePos, Blocks.AIR.defaultBlockState());
          g.discard();
          for (ItemEntity item :
              world.getEntitiesOfClass(ItemEntity.class, new AABB(workerPos).inflate(5)))
            item.discard();
          world.setBlockAndUpdate(workerPos.below(), Blocks.AIR.defaultBlockState());
          world.setChunkForced(coreChunk.x, coreChunk.z, false);
          world.setChunkForced(workerChunk.x, workerChunk.z, false);
        };
    int[] stage = {0};
    h.runAfterDelay(
        5,
        () -> {
          world.getChunkSource().save(true);
          world.setChunkForced(coreChunk.x, coreChunk.z, false);
          stage[0] = 1;
        });
    h.onEachTick(
        () -> {
          try {
            if (h.getTick() > 940) {
              cleanup.run();
              h.fail("fixture core chunk did not unload or deferred death did not reconcile");
            }
            if (stage[0] == 1 && !world.hasChunkAt(corePos)) {
              h.assertTrue(
                  g.tickCount > 0, "real worker remains ticking outside absent core chunk");
              h.assertValueEqual(
                  GolemGameTests.hands(g), 7, "carried stack survives source chunk absence");
              g.setInvulnerable(false);
              g.hurt(g.damageSources().genericKill(), Float.MAX_VALUE);
              h.assertFalse(g.isAlive(), "real host death while core chunk is absent");
              h.assertFalse(world.hasChunkAt(corePos), "death never forces core chunk");
              h.assertValueEqual(
                  world
                      .getEntitiesOfClass(
                          ItemEntity.class,
                          new AABB(workerPos).inflate(5),
                          e -> e.isAlive() && e.getItem().is(Items.STONE))
                      .stream()
                      .mapToInt(e -> e.getItem().getCount())
                      .sum(),
                  7,
                  "host hands dropped exactly once");
              world.setChunkForced(coreChunk.x, coreChunk.z, true);
              world.getChunkAt(corePos);
              stage[0] = 2;
            }
            if (stage[0] == 2
                && world.getBlockEntity(corePos) instanceof InventariumModule.CoreEntity loaded
                && loaded.identity.equals(identity)
                && loaded.orders.leases().isEmpty()) {
              h.assertValueEqual(
                  loaded.orders.find(order.id).orElseThrow().inFlight(),
                  0,
                  "core onLoad retires deferred dead-worker custody");
              h.assertValueEqual(
                  loaded.orders.find(order.id).orElseThrow().remaining(),
                  7,
                  "physical drop never fulfills the request");
              cleanup.run();
              stage[0] = 3;
              h.succeed();
            }
          } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
          }
        });
  }
}
