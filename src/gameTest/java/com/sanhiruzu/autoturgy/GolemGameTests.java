package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.content.golem.*;
import com.leclowndu93150.thaumaturge.registry.*;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.golems.*;
import com.sanhiruzu.autoturgy.inventarium.*;
import com.sanhiruzu.autoturgy.sensing.SensingModule;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class GolemGameTests {
  static final BlockPos TASK = new BlockPos(7, 2, 6),
      SOURCE = new BlockPos(11, 2, 9),
      DEST = new BlockPos(11, 2, 3),
      TOME = new BlockPos(8, 2, 4),
      BENCH = new BlockPos(7, 2, 7);

  static void floor(GameTestHelper h) {
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
  }

  static EntityThaumaturgeGolem golem(
      GameTestHelper h, Player owner, BlockPos pos, BlockPos home, boolean smart) {
    var g = TTEntities.THAUMATURGE_GOLEM.get().create(h.getLevel());
    g.setProperties(
        GolemProperties.createDefault()
            .withMaterial(TTGolemParts.IRON.get())
            .withArms(TTGolemParts.ARMS_FINE.get())
            .withHead(smart ? TTGolemParts.HEAD_SMART.get() : TTGolemParts.HEAD_BASIC.get()));
    g.setOwner(owner);
    g.setValidSpawn();
    g.setPersistenceRequired();
    g.restrictTo(h.absolutePos(home), 32);
    BlockPos p = h.absolutePos(pos);
    g.moveTo(p.getX() + .5, p.getY(), p.getZ() + .5, 0, 0);
    g.updateEntityAttributes();
    h.getLevel().addFreshEntity(g);
    return g;
  }

  static Player owner(GameTestHelper h, BlockPos pos) {
    var p = new ProgressionGameTests.TestPlayer(h.getLevel());
    p.setGameMode(GameType.SURVIVAL);
    p.moveTo(h.absolutePos(pos).getCenter());
    return p;
  }

  static int hands(EntityThaumaturgeGolem g) {
    return g.hands().contents().stream().mapToInt(ItemStack::getCount).sum();
  }

  static int containerCount(Container c) {
    int n = 0;
    for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
    return n;
  }

  static void fetter(GameTestHelper h, BlockPos p) {
    h.setBlock(p, LogisticsGameTests.host("golem_fetter"));
    h.setBlock(p.north(), Blocks.REDSTONE_BLOCK);
  }

  static void bell(GameTestHelper h, Player p, ItemStack stack, BlockPos pos) {
    p.setItemInHand(InteractionHand.MAIN_HAND, stack);
    stack.useOn(
        new UseOnContext(
            p,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(
                h.absolutePos(pos).getCenter(), Direction.UP, h.absolutePos(pos), false)));
  }

  record Delivery(
      InventariumModule.CoreEntity core,
      GolemModule.LinkEntity task,
      InventariumModule.TomeEntity tome,
      BarrelBlockEntity source,
      BarrelBlockEntity destination,
      Player owner,
      List<EntityThaumaturgeGolem> golems) {}

  static Delivery delivery(GameTestHelper h, int workers, int stock) {
    floor(h);
    var core = LogisticsGameTests.structure(h);
    TomeGameTests.power(h);
    h.setBlock(new BlockPos(8, 4, 6), LogisticsGameTests.host("vis_relay"));
    h.setBlock(TASK, GolemModule.TASKMASTER.get());
    GolemModule.LinkEntity task = h.getBlockEntity(TASK);
    task.target = LogisticsGameTests.target(h, LogisticsGameTests.A);
    h.setBlock(TOME, InventariumModule.TOME_BLOCK.get());
    InventariumModule.TomeEntity tome = h.getBlockEntity(TOME);
    tome.target = task.target;
    tome.destination = LogisticsGameTests.target(h, DEST);
    h.setBlock(SOURCE, Blocks.BARREL);
    BarrelBlockEntity source = h.getBlockEntity(SOURCE);
    source.setItem(0, new ItemStack(Items.STONE, Math.min(64, stock)));
    if (stock > 64) source.setItem(1, new ItemStack(Items.STONE, stock - 64));
    h.setBlock(DEST, Blocks.BARREL);
    BarrelBlockEntity dest = h.getBlockEntity(DEST);
    BlockPos scribePos = SOURCE.west();
    h.setBlock(
        scribePos,
        InventariumModule.SCRIBE
            .get()
            .defaultBlockState()
            .setValue(DeviceBlock.FACING, Direction.WEST));
    InventariumModule.ScribeEntity scribe = h.getBlockEntity(scribePos);
    scribe.target = task.target;
    scribe.settings[0] = Direction.UP.ordinal();
    core.admitScribe(h.absolutePos(scribePos));
    Player owner = owner(h, TASK);
    List<EntityThaumaturgeGolem> golems = new ArrayList<>();
    for (int i = 0; i < workers; i++) {
      var g = golem(h, owner, TASK.south(1 + i), TASK.south(), false);
      h.assertTrue(task.link(owner, g).equals("linked"), "actual fill-role golem links");
      golems.add(g);
    }
    return new Delivery(core, task, tome, source, dest, owner, golems);
  }

  static void order(GameTestHelper h, Delivery f, int count) {
    TomeGameTests.relay(h);
    ((com.leclowndu93150.thaumaturge.content.aura.relay.BlockEntityVisRelay)
            h.getBlockEntity(new BlockPos(8, 4, 6)))
        .refreshLink(h.getLevel());
    h.assertTrue(f.tome.operational(), "actual Ordo relay pays tome");
    h.assertTrue(f.task.core() == f.core, "taskmaster claims separate real memory");
    f.core.refresh();
    h.assertTrue(
        f.core.index.count(new ItemStack(Items.STONE)) > 0,
        "real paid scribe indexes physical stock");
    f.core.orders.request(
        f.owner.getUUID(), f.tome.identity, new ItemStack(Items.STONE), count, f.tome.destination);
    f.core.orders.reconcile(f.core.index);
    f.core.changed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void bellOwnershipSixLinksRelinkClearAndPersistence(GameTestHelper h) {
    floor(h);
    h.setBlock(BENCH, GolemModule.INHIBITOR.get());
    h.setBlock(BENCH.east(3), GolemModule.INHIBITOR.get());
    GolemModule.LinkEntity device = h.getBlockEntity(BENCH),
        other = h.getBlockEntity(BENCH.east(3));
    Player owner = owner(h, BENCH), stranger = owner(h, BENCH);
    ItemStack bell = new ItemStack(GolemModule.BELL.get());
    bell(h, owner, bell, BENCH);
    List<EntityThaumaturgeGolem> workers = new ArrayList<>();
    h.assertTrue(
        SensingModule.itemData(bell).hasUUID("device"),
        "actual item useOn stores bell device attunement");
    for (int i = 0; i < 7; i++) {
      var g = golem(h, owner, BENCH.south(2), BENCH.south(2), false);
      owner.interactOn(g, InteractionHand.MAIN_HAND);
      workers.add(g);
    }
    h.assertValueEqual(device.links.size(), 6, "bell enforces six actual host entities");
    h.assertTrue(GolemModule.linked(workers.get(6)) == null, "seventh refused");
    h.assertTrue(
        device.link(stranger, workers.getFirst()).equals("owner"), "foreign owner refused");
    bell(h, owner, bell, BENCH.east(3));
    owner.interactOn(workers.getFirst(), InteractionHand.MAIN_HAND);
    h.assertValueEqual(device.links.size(), 5, "old device cleaned");
    h.assertTrue(GolemModule.linked(workers.getFirst()) == other, "exactly one current device");
    var saved = other.saveWithFullMetadata(h.getLevel().registryAccess());
    other.loadWithComponents(saved, h.getLevel().registryAccess());
    h.assertTrue(GolemModule.linked(workers.getFirst()) == other, "device UUID survives save");
    owner.setShiftKeyDown(true);
    bell(h, owner, bell, BENCH.east(3));
    h.assertTrue(other.links.isEmpty(), "sneak bell clears links");
    h.assertTrue(GolemModule.linked(workers.getFirst()) == null, "entity cleared");
    bell(h, owner, bell, new BlockPos(1, 1, 1));
    h.assertTrue(SensingModule.itemData(bell).isEmpty(), "other block clears attunement");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void hostRoleTraitsHomeRangeAndMemoryAreAuthoritative(GameTestHelper h) {
    var f = delivery(h, 0, 3);
    var g = golem(h, f.owner, TASK.south(), TASK.south(), false);
    h.assertFalse(
        TTSeals.FILL.get().forbiddenTraits().isEmpty(),
        "installed host fill forbids actual traits");
    h.assertTrue(GolemModule.role(g, false), "fine arms host build has fill eligibility");
    var clumsy =
        GolemProperties.createDefault()
            .withMaterial(TTGolemParts.IRON.get())
            .withArms(TTGolemParts.ARMS_CLAWS.get());
    g.setProperties(clumsy);
    h.assertTrue(
        g.properties().hasTrait(TTGolemTraits.CLUMSY.get()),
        "installed claw arms mapping is Clumsy");
    h.assertTrue(f.task.link(f.owner, g).equals("role"), "actual host role excludes Clumsy");
    g.setProperties(
        clumsy.withArms(TTGolemParts.ARMS_FINE.get()).withMaterial(TTGolemParts.IRON.get()));
    g.restrictTo(h.absolutePos(TASK.east(6)), 32);
    h.assertTrue(f.task.link(f.owner, g).equals("role"), "home beyond five refused");
    h.setBlock(LogisticsGameTests.A.west().below(2), Blocks.AIR);
    h.setBlock(LogisticsGameTests.A.east().below(2), Blocks.AIR);
    h.assertTrue(f.task.core() == null, "no actual memory blocks means no assignment");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void actualHostGolemWalksPicksCarriesAndDelivers(GameTestHelper h) {
    var f = delivery(h, 1, 9);
    var g = f.golems.getFirst();
    Vec3 start = g.position();
    boolean[] carried = {false};
    double[] farthest = {0};
    h.runAfterDelay(4, () -> order(h, f, 9));
    h.onEachTick(
        () -> {
          farthest[0] = Math.max(farthest[0], g.position().distanceToSqr(start));
          if (hands(g) > 0) {
            carried[0] = true;
            h.assertValueEqual(
                f.source.getItem(0).getCount() + hands(g) + f.destination.getItem(0).getCount(),
                9,
                "every tick physical custody conserved");
          }
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(f.core.orders.orders().isEmpty(), "real delivery completes demand");
          h.assertValueEqual(
              f.destination.getItem(0).getCount(), 9, "actual destination owns all stock");
          h.assertTrue(carried[0] && farthest[0] > 9, "walked several blocks with actual hands");
          h.assertValueEqual(hands(g), 0, "hands relinquished exactly delivered stack");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void twoCompetingActualWorkersNeverExtractReservationTwice(GameTestHelper h) {
    var f = delivery(h, 2, 90);
    h.runAfterDelay(4, () -> order(h, f, 90));
    h.onEachTick(
        () -> {
          int total =
              containerCount(f.source)
                  + containerCount(f.destination)
                  + f.golems.stream().mapToInt(GolemGameTests::hands).sum();
          h.assertValueEqual(total, 90, "competing real hands conserve source total");
        });
    h.succeedWhen(
        () -> {
          h.assertValueEqual(
              f.destination.getItem(0).getCount()
                  + (f.destination.getItem(1).isEmpty() ? 0 : f.destination.getItem(1).getCount()),
              90,
              "both loads delivered");
          h.assertTrue(f.core.orders.orders().isEmpty(), "no phantom reservations");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void cancellationDuringActualCarryingReturnsSurplus(GameTestHelper h) {
    var f = delivery(h, 1, 12);
    boolean[] cancelled = {false};
    h.runAfterDelay(4, () -> order(h, f, 12));
    h.onEachTick(
        () -> {
          if (!cancelled[0] && hands(f.golems.getFirst()) > 0) {
            var order = f.core.orders.orders().iterator().next();
            f.core.orders.cancel(order.id, f.owner.getUUID());
            f.core.changed();
            cancelled[0] = true;
          }
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(cancelled[0], "cancel reached actual carrying stage");
          h.assertTrue(f.core.orders.orders().isEmpty(), "recovery acknowledged");
          h.assertValueEqual(
              f.source.getItem(0).getCount(), 12, "all real items returned to original source");
          h.assertTrue(f.destination.isEmpty(), "cancelled order delivered nothing");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 450)
  public static void fullDestinationWaitsInHandsThenPartialDeliveryConserves(GameTestHelper h) {
    var f = delivery(h, 1, 12);
    for (int i = 0; i < 27; i++) f.destination.setItem(i, new ItemStack(Items.STONE, 64));
    boolean[] waited = {false};
    h.runAfterDelay(4, () -> order(h, f, 12));
    h.runAfterDelay(
        150,
        () -> {
          h.assertValueEqual(
              hands(f.golems.getFirst()), 12, "full target retains physical carried ownership");
          h.assertValueEqual(
              f.core.orders.orders().iterator().next().remaining(),
              12,
              "refusal never fulfills metadata");
          f.destination.setItem(0, new ItemStack(Items.STONE, 60));
          waited[0] = true;
        });
    h.runAfterDelay(
        190,
        () -> {
          h.assertValueEqual(
              hands(f.golems.getFirst()), 8, "only four committed; rest remain in host hands");
          f.destination.setItem(1, new ItemStack(Items.STONE, 56));
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(waited[0], "wait observed");
          h.assertTrue(f.core.orders.orders().isEmpty(), "partial retries finish");
          h.assertValueEqual(hands(f.golems.getFirst()), 0, "all custody released exactly once");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 450)
  public static void realEntityAndCoreSaveReloadResumeHandsWithoutRecollection(GameTestHelper h) {
    var f = delivery(h, 1, 13);
    EntityThaumaturgeGolem[] worker = {f.golems.getFirst()};
    boolean[] reloaded = {false};
    h.runAfterDelay(4, () -> order(h, f, 13));
    h.onEachTick(
        () -> {
          if (!reloaded[0] && hands(worker[0]) > 0) {
            Vec3 before = worker[0].position();
            BlockPos home = worker[0].getRestrictCenter();
            float radius = worker[0].getRestrictRadius();
            h.assertTrue(
                !worker[0].blockPosition().equals(home), "reload occurs away from host home");
            var entity = worker[0].saveWithoutId(new CompoundTag());
            var core = f.core.saveWithFullMetadata(h.getLevel().registryAccess());
            worker[0].remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var replacement = TTEntities.THAUMATURGE_GOLEM.get().create(h.getLevel());
            replacement.load(entity);
            replacement.setValidSpawn();
            h.getLevel().addFreshEntity(replacement);
            worker[0] = replacement;
            f.core.loadWithComponents(core, h.getLevel().registryAccess());
            reloaded[0] = true;
            h.assertValueEqual(hands(replacement), 13, "host serializes actual equipment custody");
            h.runAfterDelay(
                2,
                () -> {
                  h.assertTrue(
                      replacement.position().distanceToSqr(before) < 1,
                      "reload does not teleport carried journey home");
                  h.assertTrue(
                      replacement.getRestrictCenter().equals(home)
                          && replacement.getRestrictRadius() == radius,
                      "original home and range restored after first host tick");
                });
          }
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(reloaded[0], "real save reload happened while carrying");
          h.assertValueEqual(f.destination.getItem(0).getCount(), 13, "saved hands delivered once");
          h.assertTrue(
              f.source.isEmpty() && f.core.orders.orders().isEmpty(),
              "no recollection or dangling lease");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void actualHostPickupDropsOnceAndRetiresLinkAndLease(GameTestHelper h) {
    var f = delivery(h, 1, 7);
    var g = f.golems.getFirst();
    h.getLevel().addNewPlayer((net.minecraft.server.level.ServerPlayer) f.owner);
    boolean[] picked = {false};
    h.runAfterDelay(4, () -> order(h, f, 7));
    h.onEachTick(
        () -> {
          if (!picked[0] && hands(g) > 0) {
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            f.owner.setShiftKeyDown(true);
            f.owner.interactOn(g, InteractionHand.MAIN_HAND);
            picked[0] = true;
            h.assertTrue(g.isRemoved(), "real host sneak interaction picks up golem");
            h.assertTrue(
                f.task.links.isEmpty() && f.core.orders.leases().isEmpty(),
                "permanent host pickup retires link and carried metadata");
            h.assertValueEqual(
                f.core.orders.orders().iterator().next().remaining(),
                7,
                "dropped items do not fulfill order");
            h.assertValueEqual(
                loose(h, g.blockPosition(), Items.STONE),
                7,
                "host drops carried stack exactly once");
            f.owner.discard();
            h.succeed();
          }
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void actualHostDeathDropsHandsOnceAndLeavesDemandRecoverable(GameTestHelper h) {
    var f = delivery(h, 1, 8);
    var g = f.golems.getFirst();
    h.runAfterDelay(4, () -> order(h, f, 8));
    boolean[] killed = {false};
    h.onEachTick(
        () -> {
          if (!killed[0] && hands(g) > 0) {
            killed[0] = true;
            g.hurt(g.damageSources().genericKill(), Float.MAX_VALUE);
            h.assertFalse(g.isAlive(), "actual host death reached");
            h.assertTrue(
                f.core.orders.leases().isEmpty() && f.task.links.isEmpty(),
                "dead worker lease and link retired");
            h.assertValueEqual(
                f.core.orders.orders().iterator().next().remaining(),
                8,
                "death does not satisfy request");
            h.assertValueEqual(
                loose(h, g.blockPosition(), Items.STONE), 8, "death drops physical hands once");
            h.succeed();
          }
        });
  }

  static int loose(GameTestHelper h, BlockPos at, Item item) {
    return h
        .getLevel()
        .getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class,
            new AABB(at).inflate(4),
            e -> e.isAlive() && e.getItem().is(item))
        .stream()
        .mapToInt(e -> e.getItem().getCount())
        .sum();
  }

  @GameTest(template = "empty", timeoutTicks = 350)
  public static void actualDispersingMirrorReceivesEarmarkedWorkerDelivery(GameTestHelper h) {
    var f = delivery(h, 1, 11);
    BlockPos p = TASK.east(2);
    h.setBlock(LogisticsGameTests.A.north().below(2), InventariumModule.MEMORY.get());
    h.setBlock(p, com.sanhiruzu.autoturgy.transport.MirrorModule.DISPERSING.get());
    com.sanhiruzu.autoturgy.transport.MirrorModule.MirrorEntity mirror = h.getBlockEntity(p);
    mirror.target = f.task.target;
    h.runAfterDelay(
        4,
        () -> {
          h.assertTrue(mirror.core() == f.core, "disperser owns a third separate memory slot");
          h.assertValueEqual(
              mirror.input.insertItem(0, new ItemStack(Items.STONE), false).getCount(),
              1,
              "unsolicited matching input rejected");
          order(h, f, 11);
        });
    h.succeedWhen(
        () -> {
          h.assertValueEqual(
              f.destination.getItem(0).getCount(),
              11,
              "actual worker token routed to selected tome output");
          h.assertTrue(f.core.orders.orders().isEmpty(), "explicit mirror delivery fulfills once");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 350)
  public static void actualHostTaskAndPartsRebuildCannotStealLeasedHands(GameTestHelper h) {
    var f = delivery(h, 1, 9);
    var g = f.golems.getFirst();
    BlockPos rogue = SOURCE.south(2);
    h.setBlock(rogue, Blocks.BARREL);
    BarrelBlockEntity wrong = h.getBlockEntity(rogue);
    var sealPos =
        new com.leclowndu93150.thaumaturge.api.golems.seals.SealPos(
            h.absolutePos(rogue), Direction.UP);
    var seal =
        com.leclowndu93150.thaumaturge.content.golem.seals.SealEntity.place(
            sealPos,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumaturge", "fill"),
            TTSeals.FILL.get(),
            f.owner.getUUID());
    h.assertTrue(
        com.leclowndu93150.thaumaturge.content.golem.seals.SealHandler.addSealEntity(
            h.getLevel(), seal),
        "real competing host Fill seal exists");
    boolean[] injected = {false};
    h.runAfterDelay(4, () -> order(h, f, 9));
    h.onEachTick(
        () -> {
          if (!injected[0] && hands(g) > 0) {
            injected[0] = true;
            var task =
                com.leclowndu93150.thaumaturge.api.golems.tasks.Task.atBlock(
                    sealPos, h.absolutePos(rogue));
            task.setLifespan((short) 500);
            task.assignTo(g.getUUID());
            task.setClaimant(g.getUUID());
            com.leclowndu93150.thaumaturge.content.golem.tasks.TaskBoard.of(h.getLevel())
                .post(task);
            com.leclowndu93150.thaumaturge.content.golem.tasks.TaskHandoff.assign(g, task);
            g.setProperties(g.properties().withHead(TTGolemParts.HEAD_SMART.get()));
            g.updateEntityAttributes();
            h.runAfterDelay(
                2,
                () -> {
                  h.assertFalse(
                      task.isClaimed(),
                      "host reservation released before any competing completion");
                  h.assertTrue(
                      task.assignedGolem() == null && task.claimant() == null,
                      "host assignment ownership relinquished");
                  if (f.core.orders.leases().stream()
                      .anyMatch(l -> l.worker.equals(g.getUUID()) && l.carried() > 0))
                    h.assertTrue(
                        g.getTask() == null || !g.getTask().isClaimed(),
                        "no competing claimed host task owns leased hands");
                  h.assertTrue(wrong.isEmpty(), "real host Fill seal did not steal carried items");
                });
          }
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(injected[0], "real host task and selector rebuild occurred during custody");
          h.assertValueEqual(
              f.destination.getItem(0).getCount(), 9, "all leased items reach legitimate output");
          h.assertTrue(
              wrong.isEmpty() && f.core.orders.leases().isEmpty(),
              "no diversion or duplicate reservations");
          com.leclowndu93150.thaumaturge.content.golem.seals.SealHandler.removeSealEntity(
              h.getLevel(), sealPos, false);
        });
  }

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void sourceSideRevocationBeforePickupIsRevalidated(GameTestHelper h) {
    var f = delivery(h, 1, 1);
    f.source.clearContent();
    h.setBlock(
        SOURCE,
        com.sanhiruzu.autoturgy.storage.StorageModule.CHESTS
            .get(com.sanhiruzu.autoturgy.storage.StorageModule.Kind.TENACIOUS)
            .get());
    com.sanhiruzu.autoturgy.storage.StorageModule.ChestEntity source = h.getBlockEntity(SOURCE);
    source.items.setStackInSlot(0, new ItemStack(Items.STONE, 10));
    boolean[] revoked = {false};
    h.runAfterDelay(4, () -> order(h, f, 9));
    h.onEachTick(
        () -> {
          if (!revoked[0] && f.core.orders.leases().stream().anyMatch(l -> l.carried() == 0)) {
            source.settings[0] = 0;
            source.changed();
            revoked[0] = true;
          }
        });
    h.runAfterDelay(
        90,
        () -> {
          h.assertTrue(revoked[0], "side revoked while real golem was walking");
          h.assertValueEqual(
              source.items.getStackInSlot(0).getCount(),
              10,
              "forbidden extraction leaves physical stock");
          h.assertTrue(
              f.destination.isEmpty() && hands(f.golems.getFirst()) == 0, "no hidden sided bypass");
          source.settings[0] = 1 << Direction.UP.ordinal();
          source.changed();
          f.core.refresh();
        });
    h.succeedWhen(
        () -> {
          h.assertValueEqual(
              f.destination.getItem(0).getCount(),
              9,
              "restored side permits requested physical amount");
          h.assertValueEqual(
              source.items.getStackInSlot(0).getCount(), 1, "Tenacious real reserve remains");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 250)
  public static void overlappingInhibitorsRestoreHostWalkingAndPreserveExternalPause(
      GameTestHelper h) {
    floor(h);
    BlockPos one = new BlockPos(3, 4, 4), two = new BlockPos(7, 4, 4);
    Player owner = owner(h, one);
    for (BlockPos p : List.of(one, two)) {
      fetter(h, p.below());
      h.setBlock(p, GolemModule.INHIBITOR.get());
      GolemModule.LinkEntity d = h.getBlockEntity(p);
      d.playerIdentity = owner.getUUID();
    }
    var g = golem(h, owner, new BlockPos(5, 2, 6), new BlockPos(5, 2, 6), false);
    Vec3 start = g.position();
    h.runAfterDelay(2, () -> g.restrictTo(h.absolutePos(new BlockPos(12, 2, 6)), 32));
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(g.position().distanceToSqr(start) < .2, "actual host home walking paused");
          h.setBlock(one, Blocks.AIR);
        });
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(
              g.position().distanceToSqr(start) < .2, "second active inhibitor retains pause");
          GolemModule.LinkEntity d = h.getBlockEntity(two);
          d.onChunkUnloaded();
        });
    h.runAfterDelay(
        120,
        () -> {
          h.assertTrue(
              g.position().distanceToSqr(start) > 1,
              "last unload restores real host home goal; pos="
                  + g.position()
                  + " start="
                  + start
                  + " inhibited="
                  + GolemModule.inhibited(g)
                  + " nav="
                  + g.getNavigation().getPath()
                  + " goals="
                  + g.goalSelector.getAvailableGoals().stream()
                      .map(w -> w.getGoal().getClass().getSimpleName() + ":" + w.isRunning())
                      .toList());
          g.setNoAi(true);
          h.setBlock(two, Blocks.AIR);
          h.assertTrue(g.isNoAi(), "external NoAI remains unchanged");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 50)
  public static void fetterChainRangeAndLinkedOnlyOwnershipModes(GameTestHelper h) {
    floor(h);
    BlockPos p = new BlockPos(2, 6, 3);
    Player owner = owner(h, p);
    h.setBlock(p, GolemModule.INHIBITOR.get());
    GolemModule.LinkEntity d = h.getBlockEntity(p);
    d.playerIdentity = owner.getUUID();
    for (int n = 1; n <= 4; n++) fetter(h, p.below(n));
    var g = golem(h, owner, new BlockPos(14, 2, 3), new BlockPos(14, 2, 3), false);
    var foreign = golem(h, owner(h, p), new BlockPos(13, 2, 3), new BlockPos(13, 2, 3), false);
    h.runAfterDelay(
        2,
        () -> {
          h.assertValueEqual(d.radius(), 32, "four fetters increase radius rather than link count");
          h.assertTrue(GolemModule.inhibited(g), "owner golem in expanded range");
          h.assertFalse(GolemModule.inhibited(foreign), "foreign owner unaffected");
          d.link(owner, g);
          h.assertFalse(GolemModule.inhibited(g), "default linked exemption");
          d.configure(0, 1);
          h.assertTrue(GolemModule.inhibited(g), "alternate linked-only mode");
          h.setBlock(p.below(2), Blocks.AIR);
          h.assertValueEqual(d.radius(), 8, "gap breaks vertical active chain");
          h.assertFalse(GolemModule.inhibited(g), "outside reduced radius resumes");
          h.succeed();
        });
  }
}
