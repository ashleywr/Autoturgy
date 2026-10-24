package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.nodes.NodeType;
import com.leclowndu93150.thaumaturge.content.aura.node.*;
import com.leclowndu93150.thaumaturge.content.aura.relay.BlockEntityVisRelay;
import com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirror;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.inventarium.*;
import com.sanhiruzu.autoturgy.sensing.*;
import com.sanhiruzu.autoturgy.signal.RedcrystalNetwork;
import com.sanhiruzu.autoturgy.storage.StorageModule;
import com.sanhiruzu.autoturgy.transport.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class LogisticsGameTests {
  static final BlockPos A = new BlockPos(3, 2, 3), B = new BlockPos(8, 2, 3);

  static Block host(String id) {
    return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("thaumaturge", id));
  }

  static LoadedTargets.Target target(GameTestHelper h, BlockPos pos) {
    return new LoadedTargets.Target(h.getLevel().dimension(), h.absolutePos(pos), Direction.UP);
  }

  static StackFilters.Filter stone(int quantity) {
    return new StackFilters.Filter(
        List.of(new StackFilters.Sample(new ItemStack(Items.STONE), quantity)),
        false,
        new StackFilters.MatchOptions(true, false));
  }

  static StorageModule.ChestEntity chest(GameTestHelper h, BlockPos pos, StorageModule.Kind kind) {
    h.setBlock(pos, StorageModule.CHESTS.get(kind).get());
    return h.getBlockEntity(pos);
  }

  static void pair(GameTestHelper h) {
    h.setBlock(A.below(), Blocks.STONE);
    h.setBlock(B.below(), Blocks.STONE);
    h.setBlock(A, host("mirror"));
    h.setBlock(B, host("mirror"));
    BlockEntityMirror a = h.getBlockEntity(A), b = h.getBlockEntity(B);
    a.linked = b.linked = true;
    a.link = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(B));
    b.link = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(A));
  }

  static int queued(GameTestHelper h, BlockPos pos) {
    return queuedAt((BlockEntityMirror) h.getBlockEntity(pos));
  }

  static int queuedAt(BlockEntityMirror mirror) {
    CompoundTag nbt = mirror.saveWithoutMetadata(mirror.getLevel().registryAccess());
    int amount = 0;
    ListTag list = nbt.getList("Items", Tag.TAG_COMPOUND);
    for (int i = 0; i < list.size(); i++)
      amount +=
          ItemStack.parseOptional(mirror.getLevel().registryAccess(), list.getCompound(i))
              .getCount();
    return amount;
  }

  static InventariumModule.CoreEntity structure(GameTestHelper h) {
    h.setBlock(A.below(2), host("amber_block"));
    h.setBlock(A.below(), host("crystal_ordo"));
    for (int x : new int[] {-2, 0, 2})
      for (int z : new int[] {-2, 0, 2})
        if (x != 0 || z != 0) h.setBlock(A.offset(x, 0, z), Blocks.BOOKSHELF);
    h.setBlock(A.west().below(2), InventariumModule.MEMORY.get());
    h.setBlock(A.east().below(2), InventariumModule.MEMORY.get());
    h.setBlock(A, InventariumModule.CORE.get());
    InventariumModule.CoreEntity core = h.getBlockEntity(A);
    core.structure = InventariumModule.structure(h.getLevel(), h.absolutePos(A));
    return core;
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void mawLeavesOverflowAndFinicalRejects(GameTestHelper h) {
    var destination = chest(h, A, StorageModule.Kind.TENACIOUS);
    for (int i = 0; i < 27; i++)
      destination.items.setStackInSlot(i, new ItemStack(Items.STONE, 64));
    destination.items.setStackInSlot(0, new ItemStack(Items.STONE, 60));
    h.setBlock(A.above(), StorageModule.FINICAL.get());
    StorageModule.MawEntity maw = h.getBlockEntity(A.above());
    maw.filter = stone(1);
    ItemEntity accepted =
        h.spawnItem(
            Items.STONE, A.above().getX() + .5f, A.above().getY() + .1f, A.above().getZ() + .5f);
    accepted.setItem(new ItemStack(Items.STONE, 10));
    accepted.setNoGravity(true);
    accepted.setDeltaMovement(0, 0, 0);
    ItemEntity rejected =
        h.spawnItem(
            Items.DIRT, A.above().getX() + .5f, A.above().getY() + .1f, A.above().getZ() + .5f);
    rejected.setNoGravity(true);
    rejected.setDeltaMovement(0, 0, 0);
    h.runAfterDelay(
        8,
        () -> {
          h.assertValueEqual(
              destination.items.getStackInSlot(0).getCount(), 64, "only four accepted");
          h.assertValueEqual(accepted.getItem().getCount(), 6, "six remain physically loose");
          h.assertTrue(rejected.isAlive(), "unmatched dirt stays loose");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 50)
  public static void greedyOnlySharesNewDepositsAndRefusesReciprocalAndBelow(GameTestHelper h) {
    var greedy = chest(h, A, StorageModule.Kind.GREEDY);
    var reciprocal = chest(h, A.west(), StorageModule.Kind.GREEDY);
    reciprocal.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 20));
    h.setBlock(A.east(), Blocks.BARREL);
    BarrelBlockEntity source = h.getBlockEntity(A.east());
    source.setItem(0, new ItemStack(Items.STONE, 20));
    h.setBlock(A.below(), Blocks.HOPPER);
    HopperBlockEntity below = h.getBlockEntity(A.below());
    below.setItem(0, new ItemStack(Items.GOLD_INGOT, 20));
    greedy.settings[0] = 1 << Direction.EAST.ordinal() | 1 << Direction.WEST.ordinal();
    greedy.settings[3] &= ~(1 << Direction.DOWN.ordinal());
    h.runAfterDelay(
        12,
        () -> {
          h.assertTrue(greedy.isEmpty(), "initial contents are baseline");
          source.setItem(0, new ItemStack(Items.STONE, 40));
        });
    h.runAfterDelay(
        25,
        () -> {
          h.assertValueEqual(
              greedy.totalType(new ItemStack(Items.STONE)), 10, "takes half of twenty new");
          h.assertValueEqual(source.getItem(0).getCount(), 30, "source retains old plus its share");
          h.assertValueEqual(
              reciprocal.totalType(new ItemStack(Items.DIAMOND)), 20, "no reciprocal theft");
          h.assertValueEqual(
              greedy.totalType(new ItemStack(Items.GOLD_INGOT)), 0, "bottom hopper not acquired");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void greedyStockAndExcessRespectFreshDepositLimit(GameTestHelper h) {
    var greedy = chest(h, A, StorageModule.Kind.GREEDY);
    h.setBlock(A.east(), Blocks.BARREL);
    BarrelBlockEntity source = h.getBlockEntity(A.east());
    greedy.settings[0] = 1 << Direction.EAST.ordinal();
    greedy.settings[1] = 1;
    greedy.settings[2] = 5;
    h.runAfterDelay(12, () -> source.setItem(0, new ItemStack(Items.STONE, 20)));
    h.runAfterDelay(
        24,
        () -> {
          h.assertValueEqual(greedy.totalType(new ItemStack(Items.STONE)), 5, "own target five");
          greedy.settings[1] = 2;
          greedy.settings[2] = 18;
          source.setItem(0, new ItemStack(Items.STONE, 25));
        });
    h.runAfterDelay(
        37,
        () -> {
          h.assertValueEqual(source.getItem(0).getCount(), 18, "neighbor reserve eighteen");
          h.assertValueEqual(
              greedy.totalType(new ItemStack(Items.STONE)), 12, "seven new excess taken");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void tenaciousKeepsOneAcrossSlotsButManualRemovalAndReloadWork(GameTestHelper h) {
    var chest = chest(h, A, StorageModule.Kind.TENACIOUS);
    chest.items.setStackInSlot(0, new ItemStack(Items.STONE, 2));
    chest.items.setStackInSlot(1, new ItemStack(Items.STONE, 1));
    h.assertValueEqual(
        chest.automation.extractItem(0, 64, false).getCount(), 2, "automation can take two");
    h.assertTrue(chest.automation.extractItem(1, 64, false).isEmpty(), "last item protected");
    CompoundTag saved = chest.saveWithoutMetadata(h.getLevel().registryAccess());
    chest.clearContent();
    chest.loadWithComponents(saved, h.getLevel().registryAccess());
    h.assertValueEqual(chest.getItem(1).getCount(), 1, "physical last item persists");
    h.assertValueEqual(chest.removeItem(1, 64).getCount(), 1, "manual removal allowed");
    chest.items.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 12));
    h.getLevel().destroyBlock(h.absolutePos(A), false);
    h.assertValueEqual(
        h
            .getLevel()
            .getEntitiesOfClass(
                ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(A)).inflate(1))
            .stream()
            .filter(e -> e.getItem().is(Items.GOLD_INGOT))
            .mapToInt(e -> e.getItem().getCount())
            .sum(),
        12,
        "break drops contents once");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void eagerTradesOnlyAuthorizedMainInventoryAndSneakBypasses(GameTestHelper h) {
    var chest = chest(h, A, StorageModule.Kind.EAGER);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    chest.owner = UUID.randomUUID();
    chest.filter = stone(8);
    chest.items.setStackInSlot(0, new ItemStack(Items.STONE, 20));
    player.getInventory().setItem(0, new ItemStack(Items.STONE, 2));
    chest.trade(player);
    h.assertValueEqual(player.getInventory().getItem(0).getCount(), 2, "unauthorized no trade");
    chest.authorized.add(player.getUUID());
    chest.trade(player);
    h.assertValueEqual(player.getInventory().getItem(0).getCount(), 8, "authorized restock");
    h.assertValueEqual(chest.totalType(new ItemStack(Items.STONE)), 14, "real chest consumed six");
    player.getInventory().setItem(0, new ItemStack(Items.STONE, 10));
    chest.trade(player);
    h.assertValueEqual(chest.totalType(new ItemStack(Items.STONE)), 16, "two excess deposited");
    player.setShiftKeyDown(true);
    player.getInventory().setItem(0, new ItemStack(Items.STONE, 20));
    chest.trade(player);
    h.assertValueEqual(player.getInventory().getItem(0).getCount(), 20, "sneak bypass");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void indexDeduplicatesBudgetsAndHonorsSideAndUnloadedRefusal(GameTestHelper h) {
    var chest = chest(h, A, StorageModule.Kind.TENACIOUS);
    chest.items.setStackInSlot(0, new ItemStack(Items.STONE, 10));
    chest.settings[0] = 1 << Direction.UP.ordinal();
    var up = target(h, A);
    var down = new LoadedTargets.Target(h.getLevel().dimension(), h.absolutePos(A), Direction.DOWN);
    var index = InventoryIndex.scan(h.getLevel(), List.of(up, up, down), 27);
    h.assertValueEqual(index.slots(), 27, "one physical inventory");
    h.assertValueEqual(index.count(stone(1)), 9L, "only extractable count");
    h.assertTrue(
        InventoryIndex.scan(h.getLevel(), List.of(down), 27).entries().isEmpty(),
        "disabled side hidden");
    h.assertValueEqual(
        InventoryIndex.scan(h.getLevel(), List.of(up), 26).slots(),
        0,
        "no partial budget containers");
    var far =
        new LoadedTargets.Target(
            h.getLevel().dimension(), new BlockPos(29999900, 64, -29999900), Direction.UP);
    h.assertTrue(!h.getLevel().hasChunkAt(far.pos()), "far chunk starts unloaded");
    h.assertTrue(
        InventoryIndex.scan(h.getLevel(), List.of(far), 16384).entries().isEmpty(), "far refused");
    h.assertTrue(!h.getLevel().hasChunkAt(far.pos()), "scan never loads");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void doubleChestIsOnePhysicalInventory(GameTestHelper h) {
    var facing =
        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    var type = net.minecraft.world.level.block.state.properties.BlockStateProperties.CHEST_TYPE;
    h.setBlock(
        A,
        Blocks.CHEST
            .defaultBlockState()
            .setValue(facing, Direction.NORTH)
            .setValue(type, net.minecraft.world.level.block.state.properties.ChestType.LEFT));
    h.setBlock(
        A.east(),
        Blocks.CHEST
            .defaultBlockState()
            .setValue(facing, Direction.NORTH)
            .setValue(type, net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
    ChestBlockEntity left = h.getBlockEntity(A), right = h.getBlockEntity(A.east());
    left.setItem(0, new ItemStack(Items.STONE, 10));
    right.setItem(0, new ItemStack(Items.STONE, 20));
    var index = InventoryIndex.scan(h.getLevel(), List.of(target(h, A), target(h, A.east())), 54);
    h.assertValueEqual(index.slots(), 54, "double chest capacity once");
    h.assertValueEqual(index.count(stone(1)), 30L, "both halves once");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void structureScribesOculusAndUnpaidScan(GameTestHelper h) {
    var core = structure(h);
    h.setBlock(A.south(2), InventariumModule.ENCHANTING_SHELF.get());
    h.setBlock(A.east(2), InventariumModule.ENSORCELLED_SHELF.get());
    h.assertValueEqual(
        InventariumModule.structure(h.getLevel(), h.absolutePos(A)).capacity(),
        1188,
        "six basic plus 216+486 shelves");
    h.setBlock(B, InventariumModule.SCRIBE.get());
    InventariumModule.ScribeEntity scribe = h.getBlockEntity(B);
    scribe.target = target(h, A);
    h.setBlock(B.east(), Blocks.BARREL);
    h.setBlock(B.south(), Blocks.CHEST);
    var single = scribe.candidates();
    h.assertValueEqual(single.size(), 1, "default one inventory type");
    h.setBlock(B.above(), InventariumModule.OCULUS.get());
    InventariumModule.OculusEntity o = h.getBlockEntity(B.above());
    o.eyes.add(target(h, B.east()));
    o.eyes.add(target(h, B.south()));
    h.assertValueEqual(scribe.candidates().size(), 2, "oculus adds selected type");
    core.scribes.add(h.absolutePos(B));
    core.refresh();
    h.assertTrue(core.index.entries().isEmpty(), "no free scan without Ordo relay");
    h.setBlock(A.below(2), Blocks.STONE);
    h.assertTrue(
        !InventariumModule.structure(h.getLevel(), h.absolutePos(A)).valid(), "amber mandatory");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void paidScribeUsesActualHostOrdoRelay(GameTestHelper h) {
    var core = structure(h);
    h.setBlock(B, InventariumModule.SCRIBE.get());
    InventariumModule.ScribeEntity scribe = h.getBlockEntity(B);
    scribe.target = target(h, A);
    core.scribes.add(h.absolutePos(B));
    h.setBlock(B.south(), Blocks.BARREL);
    BarrelBlockEntity barrel = h.getBlockEntity(B.south());
    barrel.setItem(0, new ItemStack(Items.STONE, 17));
    BlockPos nodePos = B.above(3), relayPos = B.above(2);
    h.setBlock(nodePos, host("node"));
    h.setBlock(relayPos, host("vis_relay"));
    BlockEntityNode node = h.getBlockEntity(nodePos);
    var ordo = Aspects.resolve(h.getLevel().registryAccess(), TTAspects.ORDO);
    AspectList aspects = AspectList.EMPTY.add(ordo, 20);
    node.applyNodeData(new NodeData(NodeType.NORMAL, Optional.empty(), aspects, aspects));
    node.setEnergized(true);
    h.runAfterDelay(
        3,
        () -> {
          BlockEntityVisRelay relay = h.getBlockEntity(relayPos);
          relay.refreshLink(h.getLevel());
          h.assertTrue(relay.isLinked(), "real host relay finds energized node");
          int before = node.availableCentivis(ordo);
          h.assertTrue(before > 0, "host node accrued Ordo");
          core.refresh();
          h.assertValueEqual(
              core.index.count(stone(1)), 17L, "paid scribe indexes physical barrel");
          h.assertValueEqual(
              node.availableCentivis(ordo), before - 1, "one real Ordo centivis consumed");
          h.assertValueEqual(barrel.getItem(0).getCount(), 17, "index never takes stock");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void competingOrdersReconcileCancelPersistAndTallyDemand(GameTestHelper h) {
    var core = structure(h);
    h.setBlock(B, Blocks.BARREL);
    BarrelBlockEntity barrel = h.getBlockEntity(B);
    barrel.setItem(0, new ItemStack(Items.STONE, 10));
    core.index = InventoryIndex.scan(h.getLevel(), List.of(target(h, B)), 81);
    UUID owner = UUID.randomUUID();
    var one =
        core.orders
            .request(owner, UUID.randomUUID(), new ItemStack(Items.STONE), 8, null)
            .orElseThrow();
    var two =
        core.orders
            .request(owner, UUID.randomUUID(), new ItemStack(Items.STONE), 8, null)
            .orElseThrow();
    core.orders.reconcile(core.index);
    h.assertValueEqual(one.reserved(), 8, "first gets eight");
    h.assertValueEqual(two.reserved(), 2, "second gets only remaining two");
    h.assertValueEqual(core.orders.demand(stone(1)), 16L, "demand includes unavailable six");
    core.settings[0] = 1;
    h.assertValueEqual(
        TallyService.count(
            h.getLevel(), h.absolutePos(A.east()), target(h, A), stone(1), 0, new HashSet<>(), 0),
        16L,
        "CountProvider exposes true demand");
    h.assertTrue(!core.orders.cancel(one.id, UUID.randomUUID()), "other user cannot cancel");
    h.assertTrue(core.orders.cancel(one.id, owner), "owner cancellation accepted");
    core.orders.reconcile(core.index);
    h.assertValueEqual(two.reserved(), 8, "cancel releases reservation");
    barrel.setItem(0, new ItemStack(Items.STONE, 3));
    core.index = InventoryIndex.scan(h.getLevel(), List.of(target(h, B)), 81);
    core.orders.reconcile(core.index);
    h.assertValueEqual(two.reserved(), 3, "stock removal lowers reservation");
    CompoundTag saved = core.saveWithoutMetadata(h.getLevel().registryAccess());
    core.orders.cancel(two.id, owner);
    core.loadWithComponents(saved, h.getLevel().registryAccess());
    h.assertValueEqual(core.orders.demand(stone(1)), 8L, "orders persist");
    h.assertValueEqual(barrel.getItem(0).getCount(), 3, "no request materialized anything");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void realMirrorRouteInjectsDestinationAndRefusesBrokenOrUnloaded(GameTestHelper h) {
    pair(h);
    ItemStack input = new ItemStack(Items.STONE, 12);
    h.assertTrue(
        MirrorRoutes.insert(h.getLevel().getServer(), target(h, A), input, true).isEmpty(),
        "simulation accepts loaded pair");
    h.assertValueEqual(queued(h, B), 0, "simulation queues nothing");
    h.assertTrue(
        MirrorRoutes.insert(h.getLevel().getServer(), target(h, A), input, false).isEmpty(),
        "committed route accepted");
    h.assertValueEqual(input.getCount(), 12, "caller retains original value until custody update");
    h.assertValueEqual(queued(h, A), 0, "entrance does not eject");
    h.assertValueEqual(queued(h, B), 12, "actual destination owns twelve");
    BlockEntityMirror entrance = h.getBlockEntity(A);
    entrance.link = GlobalPos.of(h.getLevel().dimension(), new BlockPos(29999800, 64, -29999800));
    h.assertValueEqual(
        MirrorRoutes.insert(h.getLevel().getServer(), target(h, A), input, false).getCount(),
        12,
        "unloaded endpoint refuses all");
    h.assertTrue(!h.getLevel().hasChunkAt(entrance.link.pos()), "route never loads endpoint");
    entrance.linked = false;
    entrance.link = null;
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void dispersingRequiresEarmarkedDemandAndPreservesRemainders(GameTestHelper h) {
    var core = structure(h);
    h.setBlock(B.below(), Blocks.STONE);
    h.setBlock(B, host("mirror"));
    BlockPos tomePos = B.west(2), adapterPos = B.south(3), sourcePos = B.south(2);
    h.setBlock(tomePos, InventariumModule.TOME_BLOCK.get());
    InventariumModule.TomeEntity tome = h.getBlockEntity(tomePos);
    tome.target = target(h, A);
    tome.destination = target(h, B);
    h.setBlock(adapterPos, MirrorModule.DISPERSING.get());
    MirrorModule.MirrorEntity adapter = h.getBlockEntity(adapterPos);
    adapter.target = target(h, A);
    h.setBlock(sourcePos, Blocks.BARREL);
    BarrelBlockEntity source = h.getBlockEntity(sourcePos);
    source.setItem(0, new ItemStack(Items.STONE, 10));
    BlockPos nodePos = A.above(3), relayPos = A.above(2);
    h.setBlock(nodePos, host("node"));
    h.setBlock(relayPos, host("vis_relay"));
    BlockEntityNode node = h.getBlockEntity(nodePos);
    var ordo = Aspects.resolve(h.getLevel().registryAccess(), TTAspects.ORDO);
    var aspects = AspectList.EMPTY.add(ordo, 20);
    node.applyNodeData(new NodeData(NodeType.NORMAL, Optional.empty(), aspects, aspects));
    node.setEnergized(true);
    h.runAfterDelay(
        3,
        () -> {
          BlockEntityVisRelay relay = h.getBlockEntity(relayPos);
          relay.refreshLink(h.getLevel());
          h.assertTrue(tome.operational(), "placed tome has paid dedicated memory");
          core.index = InventoryIndex.scan(h.getLevel(), List.of(target(h, sourcePos)), 648);
          UUID owner = UUID.randomUUID(), worker = UUID.randomUUID();
          var order =
              core.orders
                  .request(owner, tome.identity, new ItemStack(Items.STONE), 4, target(h, B))
                  .orElseThrow();
          core.orders.reconcile(core.index);
          var lease =
              core.orders.claim(order.id, worker, core.index.sources().getFirst(), 4).orElseThrow();
          ItemStack actual = source.removeItem(0, 10);
          core.orders.collected(lease.id, worker, actual, 4);
          core.index = InventoryIndex.scan(h.getLevel(), List.of(target(h, sourcePos)), 648);
          core.orders.reconcile(core.index);
          h.assertValueEqual(
              adapter.input.insertItem(0, actual, false).getCount(),
              10,
              "matching but unsolicited items rejected");
          var token = new MirrorModule.DeliveryToken(lease.id, worker, owner);
          h.assertValueEqual(
              adapter.accept(token, actual, true).getCount(),
              6,
              "simulation respects explicit custody cap");
          h.assertValueEqual(order.remaining(), 4, "simulation changes no request");
          h.assertValueEqual(
              adapter.accept(token, actual, false).getCount(), 6, "six retained by caller");
          h.assertValueEqual(queued(h, B), 4, "four real items queued at explicit output");
          h.assertValueEqual(core.orders.demand(stone(1)), 0L, "only delivered demand removed");
          h.assertValueEqual(
              adapter.accept(token, actual, false).getCount(),
              10,
              "used token refuses further delivery");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void handMirrorPreservesQuantityReserveAcrossStacks(GameTestHelper h) {
    pair(h);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    ItemStack mirror = MirrorModule.HAND.get().getDefaultInstance();
    CompoundTag data = new CompoundTag();
    data.put("route", target(h, A).save());
    data.put("filter", StackFilters.save(stone(7).converted(), h.getLevel().registryAccess()));
    SensingModule.itemData(mirror, data);
    player.getInventory().setItem(0, mirror);
    player.getInventory().setItem(1, new ItemStack(Items.STONE, 5));
    player.getInventory().setItem(2, new ItemStack(Items.STONE, 10));
    player.getInventory().setItem(3, new ItemStack(Items.DIRT, 12));
    h.assertValueEqual(
        MirrorModule.HandMirrorItem.deposit(player, mirror), 8, "only eight above reserve");
    h.assertValueEqual(
        player.getInventory().getItem(1).getCount() + player.getInventory().getItem(2).getCount(),
        7,
        "seven remain across slots");
    h.assertValueEqual(player.getInventory().getItem(3).getCount(), 12, "filter keeps dirt");
    h.assertValueEqual(queued(h, B), 8, "real destination eight");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void mercurialCarriesThroughRealMirrorPairAndTurnsOff(GameTestHelper h) {
    pair(h);
    BlockPos left = A.south(), right = B.south();
    h.setBlock(left.below(), Blocks.STONE);
    h.setBlock(right.below(), Blocks.STONE);
    var block = RedcrystalNetwork.BLOCKS.get(RedcrystalNetwork.Variant.MERCURIAL).get();
    h.setBlock(left, block);
    h.setBlock(right, block);
    RedcrystalNetwork.CrystalEntity a = h.getBlockEntity(left), b = h.getBlockEntity(right);
    a.target = target(h, A);
    b.target = target(h, B);
    h.setBlock(left.west(), Blocks.REDSTONE_BLOCK);
    RedcrystalNetwork.recompute(a);
    h.assertValueEqual(b.internalSignal(), 15, "paired mirror carries real source");
    h.setBlock(left.west(), Blocks.AIR);
    RedcrystalNetwork.recompute(a);
    h.assertValueEqual(a.internalSignal(), 0, "source removed left");
    h.assertValueEqual(b.internalSignal(), 0, "zero-loss mirror cycle cannot latch");
    h.succeed();
  }
}
