package com.sanhiruzu.autoturgy;

import static com.sanhiruzu.autoturgy.LogisticsGameTests.*;

import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.inventarium.*;
import com.sanhiruzu.autoturgy.sensing.SensingModule;
import com.sanhiruzu.autoturgy.storage.StorageModule;
import com.sanhiruzu.autoturgy.transport.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class LogisticsReviewGameTests {
  @GameTest(template = "empty", timeoutTicks = 120)
  public static void greedyFairShareOfRepeatedSingleDeposits(GameTestHelper h) {
    var greedy = chest(h, A, StorageModule.Kind.GREEDY);
    greedy.settings[0] = 1 << Direction.DOWN.ordinal();
    h.setBlock(A.below(), Blocks.BARREL);
    BarrelBlockEntity source = h.getBlockEntity(A.below());
    for (int tick : new int[] {12, 27, 42, 57, 72, 87})
      h.runAfterDelay(
          tick,
          () -> {
            ItemStack old = source.getItem(0);
            source.setItem(0, new ItemStack(Items.STONE, old.getCount() + 1));
          });
    h.runAfterDelay(
        102,
        () -> {
          h.assertValueEqual(
              greedy.totalVariant(new ItemStack(Items.STONE)),
              3,
              "50 percent of six singles is three");
          h.assertValueEqual(source.getItem(0).getCount(), 3, "bottom barrel retains its share");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void greedyAllowsOneWayAcquisitionFromAnotherGreedy(GameTestHelper h) {
    var receiver = chest(h, A, StorageModule.Kind.GREEDY);
    var source = chest(h, A.east(), StorageModule.Kind.GREEDY);
    receiver.settings[0] = 1 << Direction.EAST.ordinal();
    source.settings[0] = 0;
    h.runAfterDelay(12, () -> source.items.setStackInSlot(0, new ItemStack(Items.STONE, 10)));
    h.runAfterDelay(
        27,
        () -> {
          h.assertValueEqual(
              receiver.totalVariant(new ItemStack(Items.STONE)), 5, "one-way source can share");
          h.assertValueEqual(
              source.totalVariant(new ItemStack(Items.STONE)), 5, "source retains half");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void tenaciousSeparatesComponentVariantsAndRealHopperKeepsReserve(
      GameTestHelper h) {
    var chest = chest(h, A, StorageModule.Kind.TENACIOUS);
    ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
    named.set(DataComponents.CUSTOM_NAME, Component.literal("First"));
    ItemStack other = named.copy();
    other.set(DataComponents.CUSTOM_NAME, Component.literal("Second"));
    chest.items.setStackInSlot(0, named);
    chest.items.setStackInSlot(1, other);
    h.assertTrue(
        chest.automation.extractItem(0, 1, false).isEmpty(),
        "first named variant retains its own item");
    h.assertTrue(
        chest.automation.extractItem(1, 1, false).isEmpty(), "second variant also retained");
    var hopperChest = chest(h, B, StorageModule.Kind.TENACIOUS);
    hopperChest.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
    h.setBlock(B.below(), Blocks.HOPPER);
    h.runAfterDelay(
        27,
        () -> {
          h.assertValueEqual(
              hopperChest.totalVariant(new ItemStack(Items.DIAMOND)),
              1,
              "real vanilla hopper cannot bypass reserve");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void eagerBlacklistTradesBothDirectionsWithChestReserve(GameTestHelper h) {
    var chest = chest(h, A, StorageModule.Kind.EAGER);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    chest.owner = player.getUUID();
    chest.filter = stone(3).converted();
    chest.items.setStackInSlot(0, new ItemStack(Items.STONE, 10));
    player.getInventory().setItem(0, new ItemStack(Items.DIRT, 7));
    chest.trade(player);
    h.assertValueEqual(
        chest.totalVariant(new ItemStack(Items.DIRT)), 7, "nonlisted deposit accepted");
    h.assertValueEqual(
        chest.totalVariant(new ItemStack(Items.STONE)), 3, "blacklist quantity retained in chest");
    h.assertValueEqual(
        player.getInventory().countItem(Items.STONE),
        7,
        "listed surplus returned to authorized player");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void leasesKeepCarriedCustodyAheadOfPendingAndSurviveCancellation(
      GameTestHelper h) {
    h.setBlock(A, Blocks.BARREL);
    BarrelBlockEntity source = h.getBlockEntity(A);
    source.setItem(0, new ItemStack(Items.STONE, 12));
    var snapshot = InventoryIndex.scan(h.getLevel(), List.of(target(h, A)), 27);
    Requisitions requests = new Requisitions();
    UUID owner = UUID.randomUUID(),
        tome = UUID.randomUUID(),
        worker1 = UUID.randomUUID(),
        worker2 = UUID.randomUUID();
    var order = requests.request(owner, tome, new ItemStack(Items.STONE), 10, null).orElseThrow();
    requests.reconcile(snapshot);
    var pending = requests.claim(order.id, worker1, snapshot.sources().getFirst(), 5).orElseThrow();
    var carrying =
        requests.claim(order.id, worker2, snapshot.sources().getFirst(), 5).orElseThrow();
    ItemStack actual = source.removeItem(0, 5);
    requests.collected(carrying.id, worker2, actual, 5);
    requests.request(owner, tome, actual, -5, null);
    h.assertValueEqual(
        carrying.returnAmount(), 0, "collected items kept ahead of earlier pending lease");
    h.assertTrue(
        requests.leases().stream().noneMatch(l -> l.id.equals(pending.id)),
        "uncollected excess released");
    h.assertTrue(
        requests
            .claim(order.id, UUID.randomUUID(), snapshot.sources().getFirst(), 100000)
            .isEmpty(),
        "stale allocations cannot claim reduced demand");
    requests.reconcile(InventoryIndex.scan(h.getLevel(), List.of(target(h, A)), 27));
    h.assertValueEqual(order.reserved(), 0, "carried quantity is not reserved again");
    requests.cancel(order.id, owner);
    h.assertValueEqual(
        carrying.returnAmount(), 5, "cancel records recovery for physical carried items");
    Requisitions restored = new Requisitions();
    restored.load(requests.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
    var lease = restored.leases().iterator().next();
    h.assertValueEqual(lease.carried(), 5, "carried metadata persists");
    h.assertValueEqual(lease.returnAmount(), 5, "recovery obligation persists");
    h.assertValueEqual(
        restored.deliveredByWorker(lease.id, worker2, actual, 5),
        0,
        "cancelled items cannot count as delivery");
    source.setItem(0, new ItemStack(Items.STONE, 12));
    h.assertValueEqual(
        restored.returned(lease.id, worker2, actual, 5), 5, "physical return releases lease");
    h.assertTrue(restored.leases().isEmpty(), "recovery completes");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void mixedOculusEyesExpandAndSelectIndependentTypes(GameTestHelper h) {
    h.setBlock(A, InventariumModule.SCRIBE.get());
    InventariumModule.ScribeEntity scribe = h.getBlockEntity(A);
    h.setBlock(A.south(), Blocks.BARREL);
    h.setBlock(A.east(), Blocks.CHEST);
    h.setBlock(A.south(4), Blocks.BARREL);
    h.assertValueEqual(
        scribe.candidates().size(), 1, "default type comes from behind and radius three");
    h.setBlock(A.above(), InventariumModule.OCULUS.get());
    InventariumModule.OculusEntity oculus = h.getBlockEntity(A.above());
    oculus.eyes.add(target(h, A));
    oculus.eyes.add(target(h, A.east()));
    h.assertValueEqual(scribe.candidates().size(), 3, "one self-eye and one type-eye combine");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void structureRequiresEightShelvesAndDedicatedMemoryAndScribeBudget(
      GameTestHelper h) {
    var core = structure(h);
    h.assertTrue(core.structure.valid(), "stacked construct valid");
    h.assertTrue(
        InventariumModule.ENCHANTING_SHELF
                .get()
                .getEnchantPowerBonus(
                    InventariumModule.ENCHANTING_SHELF.get().defaultBlockState(),
                    h.getLevel(),
                    h.absolutePos(A))
            == 4,
        "enchanting shelf supplies four ordinary shelves of power");
    h.assertTrue(
        InventariumModule.ENSORCELLED_SHELF
                .get()
                .getEnchantPowerBonus(
                    InventariumModule.ENSORCELLED_SHELF.get().defaultBlockState(),
                    h.getLevel(),
                    h.absolutePos(A))
            == 8,
        "ensorcelled shelf supplies eight ordinary shelves of power");
    h.setBlock(A.offset(-2, 0, -2), Blocks.AIR);
    h.assertTrue(
        !InventariumModule.structure(h.getLevel(), h.absolutePos(A)).valid(),
        "seven shelves insufficient");
    h.setBlock(A.offset(-2, 0, -2), Blocks.BOOKSHELF);
    for (int i = 0; i < 3; i++) {
      BlockPos p = B.south(i);
      h.setBlock(p, InventariumModule.TOME_BLOCK.get());
      InventariumModule.TomeEntity tome = h.getBlockEntity(p);
      tome.target = target(h, A);
      boolean assigned = core.memory(tome.identity, target(h, p));
      h.assertTrue(assigned == (i < 2), "two physical memory blocks grant exactly two devices");
    }
    for (BlockPos pos : List.of(B.north(), B.north(2), B.north(3))) {
      h.setBlock(pos, InventariumModule.SCRIBE.get());
      InventariumModule.ScribeEntity s = h.getBlockEntity(pos);
      s.target = target(h, A);
    }
    h.assertTrue(core.admitScribe(h.absolutePos(B.north())), "baseline scribe admitted");
    h.assertTrue(core.admitScribe(h.absolutePos(B.north(2))), "one cluster adds one scribe");
    h.assertTrue(!core.admitScribe(h.absolutePos(B.north(3))), "third requires another cluster");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void handMirrorEmptyListsDoNothingAndSecondListWorks(GameTestHelper h) {
    pair(h);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    ItemStack mirror = MirrorModule.HAND.get().getDefaultInstance();
    CompoundTag data = new CompoundTag();
    data.put("route", target(h, A).save());
    SensingModule.itemData(mirror, data);
    player.getInventory().setItem(0, mirror);
    player.getInventory().setItem(1, new ItemStack(Items.STONE, 9));
    h.assertValueEqual(
        MirrorModule.HandMirrorItem.deposit(player, mirror),
        0,
        "unconfigured automatic lists take nothing");
    data.put("filter2", StackFilters.save(stone(4).converted(), h.getLevel().registryAccess()));
    SensingModule.itemData(mirror, data);
    h.assertValueEqual(
        MirrorModule.HandMirrorItem.deposit(player, mirror),
        5,
        "second list sends only listed surplus");
    h.assertValueEqual(player.getInventory().getItem(1).getCount(), 4, "blacklist reserve remains");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void catalogueQueriesBeyond256TypesAndRevisionsRefuseStaleActions(
      GameTestHelper h) {
    var core = structure(h);
    List<InventoryIndex.Entry> entries = new ArrayList<>();
    for (Item item : BuiltInRegistriesForTests.items()) {
      if (entries.size() >= 300) break;
      entries.add(new InventoryIndex.Entry(new ItemStack(item), 1));
    }
    entries.set(299, new InventoryIndex.Entry(new ItemStack(Items.DIAMOND), 1));
    core.index = new InventoryIndex.Snapshot(List.of(), entries, 0, 0);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.moveTo(h.absolutePos(A).getCenter());
    InventariumMenu menu = new InventariumMenu(5, player.getInventory(), core, null, null);
    h.assertTrue(
        menu.intent(player, menu.revision(), 0, "", 90, false), "server page ninety accepted");
    h.assertValueEqual(menu.counts.get(22), 300, "all three hundred types reachable");
    h.assertTrue(
        !menu.catalogue.getItem(0).isEmpty(), "late catalogue page contains real identity");
    h.assertTrue(
        !menu.intent(player, menu.revision(), 0, "diamond", 0, false),
        "same-tick query flood refused");
    h.runAfterDelay(
        3,
        () -> {
          h.assertTrue(
              menu.intent(player, menu.revision(), 0, "diamond", 0, false),
              "query accepted after bounded cadence");
          h.assertTrue(
              menu.catalogue
                  .getItem(0)
                  .getHoverName()
                  .getString()
                  .toLowerCase(Locale.ROOT)
                  .contains("diamond"),
              "query searches whole catalogue");
          h.succeed();
        });
  }

  static final class BuiltInRegistriesForTests {
    static List<Item> items() {
      return net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
          .filter(i -> i != Items.AIR)
          .toList();
    }
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void displaySyncNeverBroadcastsOrdersCustodyOrPhysicalInventory(GameTestHelper h) {
    var core = structure(h);
    core.orders.request(UUID.randomUUID(), UUID.randomUUID(), new ItemStack(Items.STONE), 8, null);
    CompoundTag saved = core.saveWithoutMetadata(h.getLevel().registryAccess());
    CompoundTag display = core.getUpdateTag(h.getLevel().registryAccess());
    h.assertTrue(saved.contains("orders"), "persistent save retains complete orders");
    h.assertTrue(
        !display.contains("orders")
            && !display.contains("scribes")
            && !display.contains("filter")
            && !display.contains("target"),
        "chunk display exposes only bounded display fields");
    var chest = chest(h, B, StorageModule.Kind.EAGER);
    chest.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 32));
    h.assertTrue(
        chest.saveWithoutMetadata(h.getLevel().registryAccess()).contains("items"),
        "physical inventory saved");
    h.assertTrue(
        !chest.getUpdateTag(h.getLevel().registryAccess()).contains("items"),
        "physical inventory not broadcast to chunk watchers");
    var before = core.orders.demand(stone(1));
    core.handleUpdateTag(display, h.getLevel().registryAccess());
    h.assertValueEqual(
        core.orders.demand(stone(1)), before, "display decoding does not clear persistent state");
    h.succeed();
  }
}
