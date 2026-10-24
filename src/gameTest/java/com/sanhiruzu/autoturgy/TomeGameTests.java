package com.sanhiruzu.autoturgy;

import static com.sanhiruzu.autoturgy.LogisticsGameTests.*;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.nodes.NodeType;
import com.leclowndu93150.thaumaturge.content.aura.node.*;
import com.leclowndu93150.thaumaturge.content.aura.relay.BlockEntityVisRelay;
import com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirror;
import com.sanhiruzu.autoturgy.inventarium.*;
import com.sanhiruzu.autoturgy.transport.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class TomeGameTests {
  static BlockEntityNode power(GameTestHelper h) {
    h.setBlock(A.above(3), host("node"));
    h.setBlock(A.above(2), host("vis_relay"));
    BlockEntityNode node = h.getBlockEntity(A.above(3));
    var ordo = Aspects.resolve(h.getLevel().registryAccess(), TTAspects.ORDO);
    var aspects = AspectList.EMPTY.add(ordo, 30);
    node.applyNodeData(new NodeData(NodeType.NORMAL, Optional.empty(), aspects, aspects));
    node.setEnergized(true);
    return node;
  }

  static void relay(GameTestHelper h) {
    BlockEntityVisRelay relay = h.getBlockEntity(A.above(2));
    relay.refreshLink(h.getLevel());
  }

  static void output(GameTestHelper h, BlockPos p) {
    h.setBlock(p.below(), Blocks.STONE);
    h.setBlock(p, host("mirror"));
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void placedTomeValidatesPaidRangeGhostOverflowRevisionAndMemory(GameTestHelper h) {
    var core = structure(h);
    BlockPos pos = B.south(2);
    h.setBlock(pos, InventariumModule.TOME_BLOCK.get());
    InventariumModule.TomeEntity tome = h.getBlockEntity(pos);
    tome.target = target(h, A);
    power(h);
    h.setBlock(B, Blocks.BARREL);
    BarrelBlockEntity source = h.getBlockEntity(B);
    source.setItem(0, new ItemStack(Items.STONE, 2));
    h.runAfterDelay(
        3,
        () -> {
          relay(h);
          h.assertTrue(tome.operational(), "placed tome pays actual relay and claims one memory");
          core.index = InventoryIndex.scan(h.getLevel(), List.of(target(h, B)), 648);
          var player = h.makeMockPlayer(GameType.SURVIVAL);
          player.moveTo(h.absolutePos(pos).getCenter());
          var menu = new InventariumMenu(7, player.getInventory(), core, tome, null);
          for (int i = 0; i < 3; i++)
            h.assertTrue(
                menu.intent(player, menu.revision(), 101, "", 0, false),
                "bounded request intent accepted");
          h.assertValueEqual(
              core.orders.demand(stone(1)), 2L, "ordinary request stops at actual stock");
          menu.intent(player, menu.revision(), 109, "", 0, false);
          h.assertValueEqual(
              core.orders.demand(stone(1)), 3L, "explicit Alt overflow permits unavailable stock");
          ItemStack physical = new ItemStack(Items.DIAMOND, 5);
          menu.setCarried(physical);
          menu.clicked(InventariumMenu.ROWS, 0, ClickType.PICKUP, player);
          h.assertValueEqual(
              menu.getCarried().getCount(), 5, "ghost copies without consuming cursor");
          menu.intent(player, menu.revision(), 149, "", 0, false);
          h.assertValueEqual(
              core.orders.orders().stream()
                  .filter(o -> o.template().is(Items.DIAMOND))
                  .mapToInt(Requisitions.Order::remaining)
                  .sum(),
              1,
              "out-of-stock ghost request accepted");
          int old = menu.revision();
          menu.intent(player, old, 0, "diamond", 0, false);
          h.assertTrue(
              !menu.intent(player, old, 101, "", 0, false),
              "stale row revision cannot order a changed identity");
          menu.intent(player, menu.revision(), 3, "", 0, false);
          h.assertValueEqual(
              tome.settings[0], 2, "Mirrors button selects separate output configuration");
          player.moveTo(h.absolutePos(pos.east(12)).getCenter());
          h.assertTrue(!menu.stillValid(player), "distance validated by server");
          player.moveTo(h.absolutePos(pos).getCenter());
          h.setBlock(A.west().below(2), Blocks.AIR);
          h.setBlock(A.east().below(2), Blocks.AIR);
          h.assertTrue(!menu.stillValid(player), "removing dedicated memory invalidates access");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 70)
  public static void tomeRealInputKeepsRefusedItemsAndUsesEntranceRoute(GameTestHelper h) {
    structure(h);
    BlockPos pos = B.west(2), entrance = B, exit = B.south(2);
    h.setBlock(pos, InventariumModule.TOME_BLOCK.get());
    InventariumModule.TomeEntity tome = h.getBlockEntity(pos);
    tome.target = target(h, A);
    power(h);
    output(h, entrance);
    output(h, exit);
    BlockEntityMirror a = h.getBlockEntity(entrance), b = h.getBlockEntity(exit);
    a.linked = b.linked = true;
    a.link = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(exit));
    b.link = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(entrance));
    tome.input.setStackInSlot(0, new ItemStack(Items.STONE, 7));
    h.runAfterDelay(
        3,
        () -> {
          relay(h);
          h.assertTrue(tome.operational(), "paid input ready");
        });
    h.runAfterDelay(
        23,
        () -> {
          h.assertValueEqual(
              tome.input.getStackInSlot(0).getCount(),
              7,
              "unconfigured route retains physical input");
          tome.inputRoute = target(h, entrance);
        });
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(
              tome.input.getStackInSlot(0).isEmpty(), "accepted input transferred from real slot");
          int output =
              queued(h, exit)
                  + h
                      .getLevel()
                      .getEntitiesOfClass(
                          net.minecraft.world.entity.item.ItemEntity.class,
                          new net.minecraft.world.phys.AABB(h.absolutePos(exit)).inflate(4))
                      .stream()
                      .filter(e -> e.getItem().is(Items.STONE))
                      .mapToInt(e -> e.getItem().getCount())
                      .sum();
          h.assertValueEqual(output, 7, "seven actual items at paired exit");
          h.assertValueEqual(queued(h, entrance), 0, "input does not emerge at entrance");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void disperserRoutesTwoExplicitTomeCustodiesToDistinctOutputs(GameTestHelper h) {
    var core = structure(h);
    h.setBlock(A.north().below(2), InventariumModule.MEMORY.get());
    BlockPos t1 = B.west(2),
        t2 = B.west(2).south(2),
        o1 = B,
        o2 = B.south(3),
        adapterPos = B.south(4),
        sourcePos = B.south(2);
    output(h, o1);
    output(h, o2);
    h.setBlock(t1, InventariumModule.TOME_BLOCK.get());
    h.setBlock(t2, InventariumModule.TOME_BLOCK.get());
    InventariumModule.TomeEntity one = h.getBlockEntity(t1), two = h.getBlockEntity(t2);
    one.target = two.target = target(h, A);
    one.destination = target(h, o1);
    two.destination = target(h, o2);
    h.setBlock(adapterPos, MirrorModule.DISPERSING.get());
    MirrorModule.MirrorEntity adapter = h.getBlockEntity(adapterPos);
    adapter.target = target(h, A);
    h.setBlock(sourcePos, Blocks.BARREL);
    BarrelBlockEntity source = h.getBlockEntity(sourcePos);
    source.setItem(0, new ItemStack(Items.STONE, 10));
    power(h);
    h.runAfterDelay(
        3,
        () -> {
          relay(h);
          h.assertTrue(
              one.operational() && two.operational(),
              "two tomes receive distinct dedicated memory");
          core.index = InventoryIndex.scan(h.getLevel(), List.of(target(h, sourcePos)), 648);
          UUID owner1 = UUID.randomUUID(), owner2 = UUID.randomUUID(), worker = UUID.randomUUID();
          var order1 =
              core.orders
                  .request(owner1, one.identity, new ItemStack(Items.STONE), 4, one.destination)
                  .orElseThrow();
          var order2 =
              core.orders
                  .request(owner2, two.identity, new ItemStack(Items.STONE), 6, two.destination)
                  .orElseThrow();
          core.orders.reconcile(core.index);
          var lease1 =
              core.orders
                  .claim(order1.id, worker, core.index.sources().getFirst(), 4)
                  .orElseThrow();
          var lease2 =
              core.orders
                  .claim(order2.id, worker, core.index.sources().getFirst(), 6)
                  .orElseThrow();
          ItemStack actual1 = source.removeItem(0, 4), actual2 = source.removeItem(0, 6);
          core.orders.collected(lease1.id, worker, actual1, 4);
          core.orders.collected(lease2.id, worker, actual2, 6);
          h.assertValueEqual(
              adapter
                  .accept(new MirrorModule.DeliveryToken(lease1.id, worker, owner2), actual1, false)
                  .getCount(),
              4,
              "wrong owner token rejected");
          h.assertTrue(
              adapter
                  .accept(new MirrorModule.DeliveryToken(lease1.id, worker, owner1), actual1, false)
                  .isEmpty(),
              "first real custody delivered");
          h.assertTrue(
              adapter
                  .accept(new MirrorModule.DeliveryToken(lease2.id, worker, owner2), actual2, false)
                  .isEmpty(),
              "second real custody delivered");
          h.assertValueEqual(queued(h, o1), 4, "first output four");
          h.assertValueEqual(queued(h, o2), 6, "second output six");
          h.assertValueEqual(
              core.orders.demand(stone(1)), 0L, "both orders complete only after actual transfers");
          h.succeed();
        });
  }
}
