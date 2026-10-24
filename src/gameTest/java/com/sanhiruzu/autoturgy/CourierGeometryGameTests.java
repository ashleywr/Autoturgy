package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.golems.*;
import com.sanhiruzu.autoturgy.inventarium.*;
import com.sanhiruzu.autoturgy.transport.MirrorModule;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class CourierGeometryGameTests {
  @GameTest(template = "constructs", timeoutTicks = 40)
  public static void unpaidSameTickScribeCacheRecoversWithGenuineNextTickPaidScan(
      GameTestHelper h) {
    var f = GolemGameTests.delivery(h, 0, 7);
    // Reproduce a newly energized node before its first actual accrual tick.
    h.setBlock(LogisticsGameTests.A.above(3), Blocks.AIR);
    var node = TomeGameTests.power(h);
    TomeGameTests.relay(h);
    var relay =
        (com.leclowndu93150.thaumaturge.content.aura.relay.BlockEntityVisRelay)
            h.getBlockEntity(new BlockPos(8, 4, 6));
    relay.refreshLink(h.getLevel());
    f.core().refresh();
    h.assertValueEqual(
        f.core().index.count(new ItemStack(Items.STONE)), 0L, "unfunded first scan returns empty");
    node.serverTick(h.getLevel(), node.getBlockPos());
    f.core().refresh();
    h.assertValueEqual(
        f.core().index.count(new ItemStack(Items.STONE)),
        0L,
        "same real gameTime retains the unpaid cached result");
    int delay = (h.getLevel().getGameTime() + 1) % 20 == 0 ? 2 : 1;
    h.runAfterDelay(
        delay,
        () -> {
          TomeGameTests.relay(h);
          relay.refreshLink(h.getLevel());
          var aspect =
              com.leclowndu93150.thaumaturge.api.aspect.Aspects.resolve(
                  h.getLevel(), com.leclowndu93150.thaumaturge.api.aspect.TTAspects.ORDO);
          int before = node.availableCentivis(aspect);
          f.core().refresh();
          h.assertValueEqual(
              f.core().index.count(new ItemStack(Items.STONE)),
              7L,
              "later genuinely paid scan recovers exact physical stock");
          h.assertValueEqual(
              before - node.availableCentivis(aspect),
              1,
              "recovery charges exactly one actual host Ordo centivis");
          h.succeed();
        });
  }

  @GameTest(template = "constructs", timeoutTicks = 350)
  public static void realClientMirrorGeometryConservesDeliveryAtHostNavigationEndpoint(
      GameTestHelper h) {
    GolemGameTests.floor(h);
    var core = LogisticsGameTests.structure(h);
    TomeGameTests.power(h);
    BlockPos taskPos = new BlockPos(7, 2, 10),
        sourcePos = new BlockPos(10, 2, 3),
        mirrorPos = new BlockPos(9, 2, 10),
        home = taskPos.south(),
        destPos = new BlockPos(12, 2, 11),
        tomePos = new BlockPos(8, 2, 8);
    h.setBlock(LogisticsGameTests.A.north().below(2), InventariumModule.MEMORY.get());
    h.setBlock(taskPos, GolemModule.TASKMASTER.get());
    GolemModule.LinkEntity task = h.getBlockEntity(taskPos);
    task.target = LogisticsGameTests.target(h, LogisticsGameTests.A);
    h.setBlock(tomePos, InventariumModule.TOME_BLOCK.get());
    InventariumModule.TomeEntity tome = h.getBlockEntity(tomePos);
    tome.target = task.target;
    tome.destination = LogisticsGameTests.target(h, destPos);
    h.setBlock(mirrorPos, MirrorModule.DISPERSING.get());
    MirrorModule.MirrorEntity mirror = h.getBlockEntity(mirrorPos);
    mirror.target = task.target;
    h.setBlock(sourcePos, Blocks.BARREL);
    BarrelBlockEntity source = h.getBlockEntity(sourcePos);
    source.setItem(0, new ItemStack(Items.DIAMOND, 15));
    h.setBlock(destPos, Blocks.BARREL);
    BarrelBlockEntity dest = h.getBlockEntity(destPos);
    dest.setItem(0, new ItemStack(Items.DIAMOND, 2));
    h.setBlock(
        sourcePos.west(),
        InventariumModule.SCRIBE
            .get()
            .defaultBlockState()
            .setValue(DeviceBlock.FACING, Direction.WEST));
    InventariumModule.ScribeEntity scribe = h.getBlockEntity(sourcePos.west());
    scribe.target = task.target;
    scribe.settings[0] = Direction.UP.ordinal();
    core.admitScribe(h.absolutePos(sourcePos.west()));
    var owner = GolemGameTests.owner(h, taskPos);
    EntityThaumaturgeGolem[] worker = {GolemGameTests.golem(h, owner, home, home, false)};
    h.assertTrue(task.link(owner, worker[0]).equals("linked"), "real owned host courier linked");
    h.runAfterDelay(
        4,
        () -> {
          TomeGameTests.relay(h);
          h.assertTrue(tome.operational(), "real paid tome owns memory");
          h.assertTrue(
              task.core() == core && mirror.core() == core, "three physical memories admitted");
          core.refresh();
          core.orders.request(
              owner.getUUID(), tome.identity, new ItemStack(Items.DIAMOND), 1, tome.destination);
          core.orders.reconcile(core.index);
          core.changed();
        });
    boolean[] replayed = {false};
    h.onEachTick(
        () -> {
          if (!replayed[0] && GolemGameTests.hands(worker[0]) == 1) {
            replayed[0] = true;
            // Fixture replay of the independently observed client save position, after real pickup.
            var m = h.absolutePos(mirrorPos);
            worker[0].moveTo(m.getX() + .4993696836, m.getY(), m.getZ() - 2.103130885, 0, 0);
            worker[0].getNavigation().stop();
            var body = worker[0].getBoundingBox();
            double foot = m.getZ() - worker[0].getZ(), surface = m.getZ() - body.maxZ;
            System.out.println(
                "AUTOTURGY_PINNED_CLIENT footGap="
                    + foot
                    + " surfaceGap="
                    + surface
                    + " body="
                    + body
                    + " saved="
                    + worker[0].position()
                    + " hands="
                    + GolemGameTests.hands(worker[0]));
            h.assertTrue(
                foot > 2 && surface < 2,
                "actual host body proves foot-center endpoint differs from physical surface reach");
            h.assertTrue(
                GolemModule.withinWorkReach(worker[0], m),
                "shared real body-to-block reach accepts observed clear physical gap");
            h.setBlock(mirrorPos.north(), Blocks.STONE);
            h.assertFalse(
                GolemModule.withinWorkReach(worker[0], m),
                "same physical gap refuses a blocking wall");
            h.setBlock(mirrorPos.north(), Blocks.AIR);
            var entity = worker[0].saveWithoutId(new net.minecraft.nbt.CompoundTag());
            worker[0].remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var replacement =
                com.leclowndu93150.thaumaturge.registry.TTEntities.THAUMATURGE_GOLEM
                    .get()
                    .create(h.getLevel());
            replacement.load(entity);
            replacement.setValidSpawn();
            h.getLevel().addFreshEntity(replacement);
            worker[0] = replacement;
            var before = replacement.position();
            h.runAfterDelay(
                2,
                () ->
                    h.assertTrue(
                        replacement.position().distanceToSqr(before) < 1,
                        "saved carried worker is never teleported home"));
          }
        });
    h.runAfterDelay(
        180,
        () -> {
          var path = worker[0].getNavigation().getPath();
          var end = path == null ? null : path.getEndNode();
          System.out.println(
              "AUTOTURGY_CLIENT_GEOMETRY position="
                  + worker[0].position()
                  + " target="
                  + worker[0].getNavigation().getTargetPos()
                  + " done="
                  + worker[0].getNavigation().isDone()
                  + " end="
                  + (end == null ? null : end.asBlockPos())
                  + " body="
                  + worker[0].getBoundingBox()
                  + " hands="
                  + GolemGameTests.hands(worker[0])
                  + " dest="
                  + dest.getItem(0).getCount());
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(replayed[0], "observed client custody replayed through real host save/load");
          h.assertValueEqual(
              dest.getItem(0).getCount(),
              3,
              "actual client-relative navigation geometry delivers existing paid item");
          h.assertValueEqual(source.getItem(0).getCount(), 14, "source paid exactly one");
          h.assertValueEqual(
              GolemGameTests.hands(worker[0]), 0, "host hands empty only after real delivery");
          h.assertTrue(core.orders.leases().isEmpty(), "no stale carried lease");
        });
  }
}
