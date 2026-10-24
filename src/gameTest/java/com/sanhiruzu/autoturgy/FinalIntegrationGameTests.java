package com.sanhiruzu.autoturgy;

import static com.sanhiruzu.autoturgy.LogisticsGameTests.*;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.wands.WandAccess;
import com.leclowndu93150.thaumaturge.content.aura.BlockEntityRechargePedestal;
import com.leclowndu93150.thaumaturge.content.aura.node.*;
import com.leclowndu93150.thaumaturge.content.crucible.BlockEntityCrucible;
import com.leclowndu93150.thaumaturge.content.infusion.*;
import com.leclowndu93150.thaumaturge.content.wands.*;
import com.leclowndu93150.thaumaturge.content.workbench.*;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.inventarium.*;
import com.sanhiruzu.autoturgy.sensing.*;
import com.sanhiruzu.autoturgy.storage.StorageModule;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = Autoturgy.ID, bus = EventBusSubscriber.Bus.MOD)
public final class FinalIntegrationGameTests {
  private static final Map<BlockPos, DeepStore> STORES = new HashMap<>();

  @SubscribeEvent
  public static void capabilities(RegisterCapabilitiesEvent e) {
    e.registerBlock(
        Capabilities.ItemHandler.BLOCK,
        (level, pos, state, be, side) -> {
          DeepStore store = STORES.get(pos);
          return store != null && side == Direction.WEST ? store : null;
        },
        Blocks.IRON_BLOCK);
  }

  static final class DeepStore implements IItemHandler {
    final int[] quantities = {1_600_000_000, 1_600_000_000, 1_600_000_000};
    boolean refuse;
    int calls;

    public int getSlots() {
      return 3;
    }

    public ItemStack getStackInSlot(int slot) {
      return new ItemStack(Items.IRON_INGOT, quantities[slot]);
    }

    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      return stack;
    }

    public int getSlotLimit(int slot) {
      return Integer.MAX_VALUE;
    }

    public boolean isItemValid(int slot, ItemStack stack) {
      return false;
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      if (refuse) return ItemStack.EMPTY;
      int count = Math.min(amount, quantities[slot]);
      if (!simulate) {
        quantities[slot] -= count;
        calls++;
      }
      return new ItemStack(Items.IRON_INGOT, count);
    }

    long total() {
      return Arrays.stream(quantities).mapToLong(i -> i).sum();
    }
  }

  private static DeepStore deep(GameTestHelper h, BlockPos p) {
    h.setBlock(p, Blocks.IRON_BLOCK);
    DeepStore store = new DeepStore();
    STORES.put(h.absolutePos(p), store);
    h.getLevel().invalidateCapabilities(h.absolutePos(p));
    return store;
  }

  @GameTest(template = "empty", timeoutTicks = 50)
  public static void survivalLensUseRemovalReloadAndBreakConserveComponents(GameTestHelper h) {
    h.setBlock(A, SensingModule.TALLY.get());
    SensingModule.TallyEntity tally = h.getBlockEntity(A);
    var player = new ProgressionGameTests.TestPlayer(h.getLevel());
    player.moveTo(h.absolutePos(A).getCenter());
    ItemStack lens = new ItemStack(SensingModule.UNBOUND_LENS.get(), 2);
    lens.set(DataComponents.CUSTOM_NAME, Component.literal("Owned Lens"));
    player.setItemInHand(InteractionHand.MAIN_HAND, lens);
    BlockHitResult hit =
        new BlockHitResult(h.absolutePos(A).getCenter(), Direction.UP, h.absolutePos(A), false);
    h.assertTrue(
        player
            .gameMode
            .useItemOn(player, h.getLevel(), lens, InteractionHand.MAIN_HAND, hit)
            .consumesAction(),
        "real survival block dispatch installs lens");
    h.assertValueEqual(lens.getCount(), 1, "one paid physical lens leaves hand");
    h.assertValueEqual(tally.lensMode(), 1, "owned lens enables mode");
    var menu = new ControlMenu(7, player.getInventory(), tally);
    h.assertFalse(menu.clickMenuButton(player, 5), "free menu lens packet refused");
    tally.configure(2, 1);
    h.assertValueEqual(tally.lensMode(), 1, "direct configure cannot create lens");
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SensingModule.YIELD_LENS.get()));
    player.gameMode.useItemOn(
        player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    h.assertValueEqual(
        player.getMainHandItem().getCount(),
        1,
        "occupied lens slot refuses replacement without payment");
    tally.loadWithComponents(
        tally.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
    h.assertValueEqual(tally.lensMode(), 1, "owned lens survives reload");
    player.getInventory().clearContent();
    for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    player.setShiftKeyDown(true);
    player.gameMode.useItemOn(
        player, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
    h.assertValueEqual(tally.lensMode(), 0, "sneak empty hand restores clear lens");
    h.assertValueEqual(
        player.getInventory().countItem(SensingModule.UNBOUND_LENS.get()),
        1,
        "actual lens returned to empty hand slot");
    ItemStack returned = player.getInventory().getItem(0);
    h.assertValueEqual(returned.getHoverName().getString(), "Owned Lens", "physical name retained");
    player.setShiftKeyDown(false);
    tally.installLens(returned, player);
    h.setBlock(A, Blocks.AIR);
    var drops =
        h.getLevel()
            .getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new AABB(h.absolutePos(A)).inflate(1));
    h.assertValueEqual(
        drops.stream()
            .filter(e -> e.getItem().is(SensingModule.UNBOUND_LENS.get()))
            .mapToInt(e -> e.getItem().getCount())
            .sum(),
        1,
        "replacement drops exactly one owned lens");
    h.assertTrue(
        drops.stream().anyMatch(e -> e.getItem().getHoverName().getString().equals("Owned Lens")),
        "broken lens keeps components");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void tallyRearPlacementAndOverheadBridgeRespectSidesAndCycles(GameTestHelper h) {
    h.setBlock(
        A,
        SensingModule.TALLY.get().defaultBlockState().setValue(DeviceBlock.FACING, Direction.WEST));
    SensingModule.TallyEntity tally = h.getBlockEntity(A);
    DeepStore source = deep(h, A.east());
    h.assertValueEqual(
        tally.measured(), 4_800_000_000L, "fresh box reads rear neighbor through its west face");
    h.setBlock(
        A,
        SensingModule.TALLY.get().defaultBlockState().setValue(DeviceBlock.FACING, Direction.EAST));
    tally = h.getBlockEntity(A);
    h.assertValueEqual(tally.measured(), 0L, "opposite placement targets opposite neighbor");
    h.setBlock(A.above(), SensingModule.REMOTE.get());
    SensingModule.RemoteEntity remote = h.getBlockEntity(A.above());
    remote.target =
        new LoadedTargets.Target(h.getLevel().dimension(), h.absolutePos(A.east()), Direction.WEST);
    h.assertValueEqual(
        tally.measured(), source.total(), "overhead bound comparator bridges inventory target");
    remote.target =
        new LoadedTargets.Target(h.getLevel().dimension(), h.absolutePos(A.east()), Direction.EAST);
    h.assertValueEqual(tally.measured(), 0L, "bridge keeps target sided restriction");
    remote.target = target(h, A);
    h.assertValueEqual(tally.measured(), 0L, "overhead cycle fails closed");
    remote.target = new LoadedTargets.Target(Level.NETHER, h.absolutePos(A.east()), Direction.WEST);
    h.assertValueEqual(tally.measured(), 0L, "cross dimension bridge refused");
    remote.target =
        new LoadedTargets.Target(
            h.getLevel().dimension(), h.absolutePos(A).east(100000), Direction.WEST);
    h.assertValueEqual(tally.measured(), 0L, "unloaded remote refused");
    h.assertFalse(h.getLevel().hasChunkAt(remote.target.pos()), "remote did not force a chunk");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void freshVisReaderUsesRealAboveJarAndOverheadRemote(GameTestHelper h)
      throws Exception {
    h.setBlock(A, SensingModule.VIS.get());
    EssentiaGameTests.jar(h, A.above(), 17);
    // jar fixture placed a support, so restore the physical reader beneath it.
    h.setBlock(A, SensingModule.VIS.get());
    SensingModule.VisEntity vis = h.getBlockEntity(A);
    h.assertTrue(vis.reading().valid(), "fresh reader directly below real jar reads it");
    h.assertValueEqual(vis.reading().aspects().getFirst().amount(), 17, "actual jar amount");
    EssentiaGameTests.jar(h, B, 29);
    h.setBlock(A.above(), SensingModule.REMOTE.get());
    SensingModule.RemoteEntity remote = h.getBlockEntity(A.above());
    remote.target = target(h, B);
    h.assertValueEqual(
        vis.reading().aspects().getFirst().amount(),
        29,
        "physical overhead bridge selects distant loaded jar");
    vis.target = target(h, B);
    remote.target = target(h, A);
    h.assertValueEqual(
        vis.reading().aspects().getFirst().amount(), 29, "own eye overrides overhead comparator");
    vis.target = null;
    h.assertFalse(vis.reading().valid(), "bridge cycle refuses self");
    writeReaderSetup(h);
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 45)
  public static void directBelowJarAutomaticOutputMatchesOverheadBridgeWithoutDownStorage(
      GameTestHelper h) {
    var jar = EssentiaGameTests.jar(h, A.above(), 29);
    h.setBlock(A, SensingModule.VIS.get());
    h.setBlock(B, SensingModule.VIS.get());
    h.setBlock(B.above(), SensingModule.REMOTE.get());
    SensingModule.VisEntity direct = h.getBlockEntity(A), bridged = h.getBlockEntity(B);
    SensingModule.RemoteEntity remote = h.getBlockEntity(B.above());
    remote.target =
        new LoadedTargets.Target(
            h.getLevel().dimension(), h.absolutePos(A.above()), Direction.UP);
    h.assertTrue(
        h.getLevel().getCapability(EssentiaCapabilities.STORAGE, jar.getBlockPos(), Direction.DOWN)
            == null,
        "actual jar exposes no downward transfer storage");
    h.runAfterDelay(
        12,
        () -> {
          h.assertValueEqual(remote.signal(), 2, "actual host jar comparator remains two");
          for (var reader : List.of(direct, bridged)) {
            h.assertTrue(reader.reading().automaticCapacity(), "known jar capacity is automatic");
            h.assertValueEqual(
                reader.reading().aspects().getFirst().capacity(),
                jar.capacity(),
                "public jar capacity survives either reading side");
            assertReaderOutput(h, reader, 2, "all aspects, real Aqua29 jar");
            reader.selectKey("thaumaturge:aqua");
          }
        });
    h.runAfterDelay(
        24,
        () -> {
          for (var reader : List.of(direct, bridged)) {
            assertReaderOutput(h, reader, 2, "selected Aqua29, automatic capacity");
            reader.settings[2] = 58;
          }
        });
    h.runAfterDelay(
        36,
        () -> {
          for (var reader : List.of(direct, bridged))
            assertReaderOutput(h, reader, 8, "selected Aqua29, manual capacity58");
          h.assertValueEqual(jar.amount(), 29, "reading never extracts essentia");
          h.assertTrue(
              h.getLevel()
                      .getCapability(EssentiaCapabilities.STORAGE, jar.getBlockPos(), Direction.DOWN)
                  == null,
              "reading grants no new downward transfer access");
          h.succeed();
        });
  }

  private static void assertReaderOutput(
      GameTestHelper h, SensingModule.VisEntity reader, int expected, String context) {
    h.assertValueEqual(reader.signal(), expected, context + " server output");
    h.assertValueEqual(
        reader.getBlockState().getAnalogOutputSignal(h.getLevel(), reader.getBlockPos()),
        expected,
        context + " comparator output");
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void actualCrucibleAndNodeUseKnownCapacityAndFullness(GameTestHelper h) {
    h.setBlock(A, SensingModule.VIS.get());
    SensingModule.VisEntity reader = h.getBlockEntity(A);
    h.setBlock(A.above(), host("crucible"));
    BlockEntityCrucible crucible = h.getBlockEntity(A.above());
    var aqua = Aspects.resolve(h.getLevel(), TTAspects.AQUA);
    var aer = Aspects.resolve(h.getLevel(), TTAspects.AER);
    int amount = BlockEntityCrucible.MAX_ASPECT / 2;
    crucible.addAspects(aqua, amount);
    h.onEachTick(
        () -> {
          if (h.getLevel().getGameTime() % 10 != 0) return;
          reader.serverTick();
          h.assertTrue(reader.reading().automaticCapacity(), "actual crucible has known capacity");
          h.assertValueEqual(
              reader.reading().aspects().getFirst().capacity(),
              BlockEntityCrucible.MAX_ASPECT,
              "actual crucible uses public maximum");
          assertReaderOutput(h, reader, 8, "half-full actual crucible");
          reader.settings[2] = amount;
          reader.serverTick();
          assertReaderOutput(h, reader, 15, "manual crucible capacity");
          h.assertValueEqual(crucible.getAspects().amountOf(aqua), amount, "crucible not drained");
          reader.settings[2] = 0;
          h.setBlock(A.above(), host("node"));
          BlockEntityNode node = h.getBlockEntity(A.above());
          node.applyNodeData(
              new NodeData(
                  com.leclowndu93150.thaumaturge.api.nodes.NodeType.NORMAL,
                  Optional.empty(),
                  AspectList.EMPTY.add(aqua, 20).add(aer, 9),
                  AspectList.EMPTY.add(aqua, 100).add(aer, 10)));
          h.assertTrue(reader.reading().node(), "actual node keeps current/base semantics");
          reader.settings[0] = 1;
          reader.serverTick();
          assertReaderOutput(h, reader, 14, "node highest fullness uses Aer9/10");
          reader.settings[0] = 2;
          reader.serverTick();
          assertReaderOutput(h, reader, 3, "node lowest fullness uses Aqua20/100");
          reader.settings[2] = 100;
          reader.serverTick();
          assertReaderOutput(h, reader, 2, "node manual capacity uses Aer9/100");
          h.assertValueEqual(node.getAspects().totalAmount(), 29, "node not drained");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void actualPedestalAndArcaneWorkbenchReadTheirWandParts(GameTestHelper h) {
    h.setBlock(A, SensingModule.VIS.get());
    SensingModule.VisEntity reader = h.getBlockEntity(A);
    var parts = WandParts.starter();
    var wandItem =
        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumaturge", "wand"));
    ItemStack wand = ItemWand.create(wandItem, parts.cap(), parts.rod(), false);
    for (var primal : TTAspects.PRIMALS)
      wand = WandAccess.withVis(wand, primal, parts.maxCentivis() / 2);
    ItemStack filled = wand;
    h.onEachTick(
        () -> {
          if (h.getLevel().getGameTime() % 10 != 0) return;
          for (String holder : List.of("recharge_pedestal", "arcane_workbench")) {
            h.setBlock(A.above(), host(holder));
            if (h.getBlockEntity(A.above()) instanceof BlockEntityRechargePedestal pedestal)
              pedestal.setItem(filled.copy());
            else {
              BlockEntityArcaneWorkbench workbench = h.getBlockEntity(A.above());
              workbench.getInventory().setItem(InventoryArcaneWorkbench.WAND_SLOT, filled.copy());
            }
            reader.settings[2] = 0;
            reader.selectKey("");
            h.assertTrue(
                reader.reading().valid() && reader.reading().automaticCapacity(),
                holder + " exposes actual wand capacity");
            h.assertValueEqual(
                reader.reading().aspects().getFirst().capacity(),
                parts.maxCentivis(),
                holder + " capacity comes from actual parts");
            reader.serverTick();
            assertReaderOutput(h, reader, 8, holder + " all six half-full primals");
            reader.selectKey("thaumaturge:aqua");
            reader.serverTick();
            assertReaderOutput(h, reader, 8, holder + " selected Aqua half full");
            reader.settings[2] = parts.maxCentivis() / 4;
            reader.serverTick();
            assertReaderOutput(h, reader, 15, holder + " manual capacity");
            h.assertValueEqual(
                reader.reading().aspects().stream().mapToInt(a -> a.amount()).sum(),
                6 * (parts.maxCentivis() / 2),
                holder + " reading does not spend vis");
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void actualInfusionDemandRetainsUnknownAutomaticAndManualCapacity(GameTestHelper h) {
    h.setBlock(A, SensingModule.VIS.get());
    SensingModule.VisEntity reader = h.getBlockEntity(A);
    h.setBlock(A.above(), host("infusion_matrix"));
    BlockEntityInfusionMatrix matrix = h.getBlockEntity(A.above());
    var aqua = Aspects.resolve(h.getLevel(), TTAspects.AQUA);
    var job =
        new InfusionCraftJob(
            List.of(new ItemStack(Items.IRON_INGOT)),
            AspectList.EMPTY.add(aqua, 29),
            new ItemStack(Items.DIAMOND),
            new ItemStack(Items.GOLD_INGOT),
            0,
            Optional.empty());
    var saved = matrix.saveWithFullMetadata(h.getLevel().registryAccess());
    com.leclowndu93150.thaumaturge.serialization.TTNbt.store(
        saved, "Job", InfusionCraftJob.CODEC, h.getLevel().registryAccess(), job);
    matrix.loadWithComponents(saved, h.getLevel().registryAccess());
    h.onEachTick(
        () -> {
          if (h.getLevel().getGameTime() % 10 != 0) return;
          h.assertTrue(reader.reading().valid(), "actual matrix remaining demand is readable");
          h.assertFalse(reader.reading().automaticCapacity(), "demand has no invented maximum");
          reader.serverTick();
          assertReaderOutput(h, reader, 0, "unknown automatic infusion capacity");
          reader.settings[2] = 58;
          reader.serverTick();
          assertReaderOutput(h, reader, 8, "actual infusion Aqua29 demand/manual58");
          h.assertValueEqual(matrix.remainingEssentia().amountOf(aqua), 29, "demand unchanged");
          h.succeed();
        });
  }

  private static void writeReaderSetup(GameTestHelper h) throws Exception {
    var commands = new ArrayList<String>();
    commands.add("fill 200 79 200 216 79 206 minecraft:stone");
    commands.add("setblock 200 80 200 autoturgy:tally_box[facing=north]");
    commands.add("setblock 200 80 201 minecraft:barrel");
    commands.add("item replace block 200 80 201 container.0 with minecraft:iron_ingot 1");
    commands.add("item replace block 200 80 201 container.1 with minecraft:diamond 64");
    commands.add("setblock 204 80 200 autoturgy:tally_box[facing=north]");
    commands.add("setblock 204 81 200 autoturgy:remote_comparator");
    var remote = new net.minecraft.nbt.CompoundTag();
    remote.put(
        "target",
        new LoadedTargets.Target(Level.OVERWORLD, new BlockPos(200, 80, 201), Direction.UP).save());
    commands.add("data merge block 204 81 200 " + remote);
    var jar = EssentiaGameTests.jar(h, B, 29);
    commands.add("setblock 208 80 200 autoturgy:vis_reader");
    commands.add(
        "setblock 208 81 200 thaumaturge:jar_normal"
            + jar.saveWithoutMetadata(h.getLevel().registryAccess()));
    commands.add("setblock 212 80 200 autoturgy:vis_reader");
    commands.add("setblock 212 81 200 autoturgy:remote_comparator");
    remote.put(
        "target",
        new LoadedTargets.Target(Level.OVERWORLD, new BlockPos(208, 81, 200), Direction.UP).save());
    commands.add("data merge block 212 81 200 " + remote);
    ItemStack filter = new ItemStack(SensingModule.FILTER.get());
    SensingModule.itemData(filter, StackFilters.save(metals(false), h.getLevel().registryAccess()));
    for (ItemStack item :
        List.of(
            filter,
            new ItemStack(SensingModule.UNBOUND_LENS.get()),
            new ItemStack(SensingModule.YIELD_LENS.get()))) {
      String spec =
          new net.minecraft.commands.arguments.item.ItemInput(
                  item.getItemHolder(), item.getComponentsPatch())
              .serialize(h.getLevel().registryAccess());
      var parsed =
          new net.minecraft.commands.arguments.item.ItemParser(h.getLevel().registryAccess())
              .parse(new com.mojang.brigadier.StringReader(spec));
      var round =
          new net.minecraft.commands.arguments.item.ItemInput(parsed.item(), parsed.components())
              .createItemStack(1, true);
      h.assertTrue(
          ItemStack.matches(item, round),
          "canonical reader tool command preserves physical components");
      commands.add("give @s " + spec);
    }
    var source = h.getLevel().getServer().createCommandSourceStack().withPermission(4);
    for (String command : commands) {
      var parsed = h.getLevel().getServer().getCommands().getDispatcher().parse(command, source);
      h.assertTrue(
          !parsed.getReader().canRead() && parsed.getExceptions().isEmpty(),
          "canonical command parses: " + command);
    }
    var path =
        java.nio.file.Path.of(System.getProperty("autoturgy.testReport"))
            .getParent()
            .resolve("client-readers.mcfunction");
    java.nio.file.Files.write(path, commands, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static StackFilters.Filter metals(boolean blacklist) {
    return new StackFilters.Filter(
        List.of(
            new StackFilters.Sample(new ItemStack(Items.IRON_INGOT), 2),
            new StackFilters.Sample(new ItemStack(Items.DIAMOND), 64)),
        blacklist,
        new StackFilters.MatchOptions(false, false));
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void survivalPlacementTargetsRearAndEmitsOnlyFront(GameTestHelper h) {
    h.setBlock(A.below(), Blocks.STONE);
    DeepStore source = deep(h, A.east());
    var player = new ProgressionGameTests.TestPlayer(h.getLevel());
    player.moveTo(h.absolutePos(A.north(2)).getCenter());
    player.setYRot(-90);
    ItemStack box = new ItemStack(SensingModule.TALLY.get());
    player.setItemInHand(InteractionHand.MAIN_HAND, box);
    BlockHitResult hit =
        new BlockHitResult(
            h.absolutePos(A).getCenter(), Direction.UP, h.absolutePos(A.below()), false);
    player.gameMode.useItemOn(player, h.getLevel(), box, InteractionHand.MAIN_HAND, hit);
    h.assertBlockPresent(SensingModule.TALLY.get(), A);
    h.assertValueEqual(box.getCount(), 0, "real survival placement consumes one box");
    var state = h.getLevel().getBlockState(h.absolutePos(A));
    h.assertValueEqual(
        state.getValue(DeviceBlock.FACING),
        Direction.WEST,
        "placement marks front toward player heading");
    SensingModule.TallyEntity tally = h.getBlockEntity(A);
    h.assertValueEqual(
        tally.measured(), source.total(), "placed rear directly selects sided inventory");
    h.runAfterDelay(
        12,
        () -> {
          h.assertValueEqual(
              state.getSignal(h.getLevel(), h.absolutePos(A), Direction.EAST),
              15,
              "query from front receives saturated signal");
          h.assertValueEqual(
              state.getSignal(h.getLevel(), h.absolutePos(A), Direction.WEST),
              0,
              "target inventory receives no rear feedback");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void directAboveCrucibleNodeAndWandHolderUseActualHostState(GameTestHelper h) {
    h.setBlock(A, SensingModule.VIS.get());
    SensingModule.VisEntity reader = h.getBlockEntity(A);
    h.setBlock(A.above(), host("crucible"));
    h.assertTrue(reader.reading().valid(), "unbound reader recognizes actual crucible above");
    h.setBlock(A.above(), host("node"));
    var node =
        (com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode)
            h.getBlockEntity(A.above());
    var aqua =
        com.leclowndu93150.thaumaturge.api.aspect.Aspects.resolve(
            h.getLevel(), com.leclowndu93150.thaumaturge.api.aspect.TTAspects.AQUA);
    var aspects = com.leclowndu93150.thaumaturge.api.aspect.AspectList.EMPTY.add(aqua, 31);
    node.applyNodeData(
        new com.leclowndu93150.thaumaturge.content.aura.node.NodeData(
            com.leclowndu93150.thaumaturge.api.nodes.NodeType.NORMAL,
            Optional.empty(),
            aspects,
            aspects));
    h.assertTrue(
        reader.reading().node(), "fresh physical arrangement selects actual node semantics");
    h.assertValueEqual(
        reader.reading().aspects().getFirst().amount(),
        31,
        "actual node contents read without eye");
    h.setBlock(A.above(), host("recharge_pedestal"));
    var holder =
        (com.leclowndu93150.thaumaturge.content.aura.BlockEntityRechargePedestal)
            h.getBlockEntity(A.above());
    var parts = com.leclowndu93150.thaumaturge.content.wands.WandParts.starter();
    var item =
        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumaturge", "wand"));
    ItemStack wand =
        com.leclowndu93150.thaumaturge.content.wands.ItemWand.create(
            item, parts.cap(), parts.rod(), false);
    wand =
        com.leclowndu93150.thaumaturge.api.wands.WandAccess.withVis(
            wand, com.leclowndu93150.thaumaturge.api.aspect.TTAspects.AQUA, 113);
    holder.setItem(wand);
    h.assertTrue(
        reader.reading().valid() && reader.reading().automaticCapacity(),
        "direct holder uses actual wand parts");
    h.assertValueEqual(
        reader.reading().aspects().stream()
            .filter(a -> a.id().endsWith(":aqua"))
            .findFirst()
            .orElseThrow()
            .amount(),
        113,
        "direct holder returns actual centivis");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void physicalLensesKeepQuantityRulesForLooseItemsAndPredictedDrops(
      GameTestHelper h) {
    h.setBlock(A, SensingModule.TALLY.get());
    SensingModule.TallyEntity tally = h.getBlockEntity(A);
    tally.target = target(h, B);
    tally.settings[4] = 1;
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    tally.installLens(new ItemStack(SensingModule.UNBOUND_LENS.get()), player);
    var sample = new StackFilters.Sample(new ItemStack(Items.DIAMOND), 4);
    tally.filter =
        new StackFilters.Filter(
            List.of(sample), false, new StackFilters.MatchOptions(false, false));
    var item =
        new net.minecraft.world.entity.item.ItemEntity(
            h.getLevel(),
            h.absolutePos(B).getX() + .5,
            h.absolutePos(B).getY() + .5,
            h.absolutePos(B).getZ() + .5,
            new ItemStack(Items.DIAMOND, 3));
    h.getLevel().addFreshEntity(item);
    h.assertFalse(tally.quantitiesSatisfied(), "paid loose lens requires sample quantity");
    item.setItem(new ItemStack(Items.DIAMOND, 4));
    h.assertTrue(tally.quantitiesSatisfied(), "loose quantity reaches exact threshold");
    tally.removeLens(player);
    tally.installLens(new ItemStack(SensingModule.YIELD_LENS.get()), player);
    h.setBlock(B, Blocks.STONE);
    tally.filter =
        new StackFilters.Filter(
            List.of(
                new StackFilters.Sample(new ItemStack(Items.STONE), 1),
                new StackFilters.Sample(new ItemStack(Items.COBBLESTONE), 1)),
            false,
            new StackFilters.MatchOptions(false, false));
    h.assertTrue(
        tally.quantitiesSatisfied(),
        "yield alternatives honor all sample requirements without extraction");
    h.assertBlockPresent(Blocks.STONE, B);
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void fullInventoryLensReturnDropsTheOwnedItemOnce(GameTestHelper h) {
    h.setBlock(A, SensingModule.TALLY.get());
    SensingModule.TallyEntity tally = h.getBlockEntity(A);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.moveTo(h.absolutePos(A).getCenter());
    ItemStack lens = new ItemStack(SensingModule.YIELD_LENS.get());
    lens.set(DataComponents.CUSTOM_NAME, Component.literal("Full Return"));
    tally.installLens(lens, player);
    for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    tally.removeLens(player);
    tally.removeLens(player);
    h.setBlock(A, Blocks.AIR);
    var drops =
        h.getLevel()
            .getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new AABB(h.absolutePos(A)).inflate(2));
    h.assertValueEqual(
        drops.stream()
            .filter(
                e ->
                    e.getItem().is(SensingModule.YIELD_LENS.get())
                        && e.getItem().getHoverName().getString().equals("Full Return"))
            .mapToInt(e -> e.getItem().getCount())
            .sum(),
        1,
        "full inventory return safely drops physical component once");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void tallyQuantitiesAllAnyBlacklistAndRemoteIndex(GameTestHelper h) {
    h.setBlock(A, SensingModule.TALLY.get());
    h.setBlock(A.south(), Blocks.BARREL);
    SensingModule.TallyEntity tally = h.getBlockEntity(A);
    BarrelBlockEntity barrel = h.getBlockEntity(A.south());
    barrel.setItem(0, new ItemStack(Items.IRON_INGOT, 1));
    tally.filter = metals(false);
    tally.settings[4] = 1;
    h.assertFalse(tally.quantitiesSatisfied(), "missing diamond and insufficient iron fail all");
    barrel.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
    h.assertFalse(tally.quantitiesSatisfied(), "one sufficient sample cannot satisfy all");
    tally.settings[4] = 2;
    h.assertTrue(tally.quantitiesSatisfied(), "one sufficient sample satisfies any");
    tally.settings[4] = 1;
    barrel.setItem(1, new ItemStack(Items.DIAMOND, 63));
    h.assertFalse(tally.quantitiesSatisfied(), "sample quantity matters");
    barrel.setItem(1, new ItemStack(Items.DIAMOND, 64));
    h.assertTrue(tally.quantitiesSatisfied(), "all quantities satisfied");
    tally.filter = metals(true);
    h.assertFalse(tally.quantitiesSatisfied(), "blacklist limits exclude exact quantities");
    barrel.setItem(0, new ItemStack(Items.IRON_INGOT, 1));
    barrel.setItem(1, new ItemStack(Items.DIAMOND, 63));
    h.assertTrue(tally.quantitiesSatisfied(), "blacklist allows each below limit");
    var core = structure(h);
    // structure replaces A; put the tally at a separate loaded location.
    h.setBlock(B, SensingModule.TALLY.get());
    tally = h.getBlockEntity(B);
    core.index = InventoryIndex.scan(h.getLevel(), List.of(target(h, A.south())), 27);
    tally.filter = metals(true);
    tally.settings[4] = 1;
    h.setBlock(B.above(), SensingModule.REMOTE.get());
    SensingModule.RemoteEntity remote = h.getBlockEntity(B.above());
    remote.target = target(h, A);
    h.assertTrue(tally.quantitiesSatisfied(), "quantities preserve remote index count semantics");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 110)
  public static void greedyDeepStoragePercentConservesAndNeverRepeats(GameTestHelper h) {
    var greedy = chest(h, A, StorageModule.Kind.GREEDY);
    greedy.settings[0] = 1 << Direction.EAST.ordinal();
    DeepStore source = deep(h, A.east());
    h.runAfterDelay(12, () -> source.quantities[0] += 100_000_001);
    h.runAfterDelay(
        27,
        () -> {
          h.assertValueEqual(
              greedy.totalVariant(new ItemStack(Items.IRON_INGOT)),
              1728,
              "large percent product yields bounded real transfer");
          h.assertValueEqual(
              source.total(), 4_900_000_001L - 1728, "deep totals conserve above 2^32");
          h.assertValueEqual(source.calls, 1, "only bounded accepted quantity is extracted");
        });
    h.runAfterDelay(
        42,
        () -> {
          h.assertValueEqual(
              greedy.totalVariant(new ItemStack(Items.IRON_INGOT)),
              1728,
              "full destination defers earned share");
          greedy.clearContent();
        });
    h.runAfterDelay(
        57,
        () -> {
          h.assertValueEqual(
              greedy.totalVariant(new ItemStack(Items.IRON_INGOT)),
              1728,
              "earned deep share continues on a later tick");
          h.assertValueEqual(
              source.total(), 4_900_000_001L - 3456, "two bounded transfers conserve long balance");
          greedy.clearContent();
          source.refuse = true;
        });
    h.runAfterDelay(
        72,
        () -> {
          h.assertValueEqual(
              greedy.totalVariant(new ItemStack(Items.IRON_INGOT)),
              0,
              "refusal retains earned share without fabricating items");
          source.refuse = false;
          source.quantities[0] = 1_600_000_000;
        });
    h.runAfterDelay(
        87,
        () -> {
          h.assertValueEqual(
              greedy.totalVariant(new ItemStack(Items.IRON_INGOT)),
              0,
              "external withdrawal cancels outstanding entitlement and preserves old baseline");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void actualInventariumMenuPacketsPreserveDeepCountAndSignedHighWords(
      GameTestHelper h) {
    var core = structure(h);
    DeepStore source = deep(h, B);
    var target =
        new LoadedTargets.Target(h.getLevel().dimension(), h.absolutePos(B), Direction.WEST);
    core.index = InventoryIndex.scan(h.getLevel(), List.of(target), 27);
    h.assertValueEqual(
        core.index.count(new ItemStack(Items.IRON_INGOT)),
        source.total(),
        "actual sided scanner indexes 64bit total");
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    var server = new InventariumMenu(7, player.getInventory(), core, null, null);
    var opening =
        new RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
    InventariumMenu.writeOpening(opening, false, false);
    var client = new InventariumMenu(7, player.getInventory(), opening);
    opening.release();
    server.setSynchronizer(
        new ContainerSynchronizer() {
          public void sendInitialData(
              AbstractContainerMenu menu,
              NonNullList<ItemStack> stacks,
              ItemStack carried,
              int[] data) {
            for (int i = 0; i < stacks.size(); i++) client.slots.get(i).set(stacks.get(i).copy());
            for (int i = 0; i < data.length; i++) sendDataChange(menu, i, data[i]);
          }

          public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
            client.slots.get(slot).set(stack.copy());
          }

          public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {}

          public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            ClientboundContainerSetDataPacket.STREAM_CODEC.encode(
                buf, new ClientboundContainerSetDataPacket(7, id, value));
            var received = ClientboundContainerSetDataPacket.STREAM_CODEC.decode(buf);
            client.setData(received.getId(), received.getValue());
            buf.release();
          }
        });
    h.assertValueEqual(
        client.value(0, 0),
        4_800_000_000L,
        "actual menu and encoded signed short packets preserve count above 2^32");
    core.index =
        new InventoryIndex.Snapshot(
            List.of(),
            List.of(new InventoryIndex.Entry(new ItemStack(Items.IRON_INGOT), Long.MAX_VALUE)),
            3,
            0);
    server.refresh();
    server.broadcastChanges();
    h.assertValueEqual(
        client.value(0, 0),
        Long.MAX_VALUE,
        "all four words preserve full positive long including signed low words");
    h.succeed();
  }
}
