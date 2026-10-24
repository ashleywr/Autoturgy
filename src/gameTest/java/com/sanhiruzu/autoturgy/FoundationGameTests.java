package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.host.ThaumaturgeIntegration;
import com.sanhiruzu.autoturgy.sensing.*;
import com.sanhiruzu.autoturgy.signal.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class FoundationGameTests {
  @GameTest(template = "empty", timeoutTicks = 40)
  public static void signumCountsAndBindsNonlivingEntitiesWithExtendedPower(GameTestHelper helper) {
    BlockPos pos = new BlockPos(5, 2, 5);
    helper.setBlock(pos, SensingModule.SIGNUM.get());
    SensingModule.SignumEntity signum = helper.getBlockEntity(pos);
    signum.entityType = "minecraft:minecart";
    signum.settings[1] = 2; // Age options do not exclude nonliving entities.
    net.minecraft.world.entity.Entity first = null;
    for (int i = 0; i < 20; i++) {
      var cart = helper.spawn(net.minecraft.world.entity.EntityType.MINECART, pos.north(2));
      cart.setNoGravity(true);
      if (first == null) first = cart;
    }
    var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
    ItemStack eye = SensingModule.ENTITY_EYE.get().getDefaultInstance();
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, eye);
    var interaction =
        new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract(
            player, net.minecraft.world.InteractionHand.MAIN_HAND, first);
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(interaction);
    helper.assertTrue(interaction.isCanceled(), "entity-eye event handles nonliving interaction");
    helper.assertValueEqual(
        SensingModule.itemData(eye).getString("entityType"),
        "minecraft:minecart",
        "eye binds nonliving type");
    helper.assertValueEqual(signum.measured(), 20L, "children filter does not suppress minecarts");
    helper.runAfterDelay(
        12,
        () -> {
          helper.assertValueEqual(
              signum.internalSignal(), 20, "extended count reaches redcrystal API");
          helper.assertValueEqual(signum.signal(), 15, "vanilla strength remains bounded");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void signumSupportsSpecificAndGeneralPlayers(GameTestHelper helper) {
    BlockPos pos = new BlockPos(5, 2, 5);
    helper.setBlock(pos, SensingModule.SIGNUM.get());
    SensingModule.SignumEntity signum = helper.getBlockEntity(pos);
    signum.entityType = "minecraft:player";
    signum.playerIdentity = UUID.randomUUID();
    var player =
        net.neoforged.neoforge.common.util.FakePlayerFactory.get(
            helper.getLevel(),
            new com.mojang.authlib.GameProfile(UUID.randomUUID(), "AutoturgyTest"));
    var point = helper.absolutePos(pos.north(2)).getCenter();
    player.moveTo(point.x, point.y, point.z);
    helper.getLevel().addNewPlayer(player);
    helper.assertValueEqual(signum.measured(), 0L, "specific identity rejects another player");
    signum.settings[3] = 0;
    helper.assertValueEqual(signum.measured(), 1L, "general player mode ignores bound UUID");
    signum.settings[3] = 1;
    signum.playerIdentity = player.getUUID();
    helper.assertValueEqual(signum.measured(), 1L, "specific identity detects its bound player");
    player.discard();
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void signumAdjacentWallExtendsOppositeAndIgnoresGlass(GameTestHelper helper) {
    BlockPos pos = new BlockPos(5, 2, 1);
    helper.setBlock(pos, SensingModule.SIGNUM.get());
    SensingModule.SignumEntity signum = helper.getBlockEntity(pos);
    signum.entityType = "minecraft:cow";
    var cow = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW, pos.south(8));
    cow.setNoGravity(true);
    helper.assertValueEqual(signum.measured(), 0L, "unblocked default half-span is five");
    helper.setBlock(pos.north(), Blocks.STONE);
    helper.assertValueEqual(signum.measured(), 1L, "north wall extends south reach to ten");
    helper.setBlock(pos.south(), Blocks.STONE);
    helper.assertValueEqual(signum.measured(), 0L, "two opposite walls suppress that axis");
    helper.setBlock(pos.south(), Blocks.AIR);
    helper.setBlock(pos.north(), Blocks.GLASS);
    helper.assertValueEqual(signum.measured(), 0L, "glass does not shape or extend detection");
    helper.setBlock(pos.north(), Blocks.SHORT_GRASS);
    helper.assertValueEqual(signum.measured(), 0L, "replaceable neighbors do not shape detection");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void yieldLensPredictsNormalAndSilkDropsWithoutMutation(GameTestHelper helper) {
    BlockPos source = new BlockPos(6, 2, 3);
    helper.setBlock(DEVICE, SensingModule.TALLY.get());
    SensingModule.TallyEntity tally = helper.getBlockEntity(DEVICE);
    tally.target = target(helper, source, Direction.UP);
    tally.installLens(
        new ItemStack(SensingModule.YIELD_LENS.get()),
        helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));
    helper.setBlock(source, Blocks.STONE);
    for (Item item : List.of(Items.COBBLESTONE, Items.STONE)) {
      tally.filter =
          new StackFilters.Filter(
              List.of(new StackFilters.Sample(new ItemStack(item), 1)),
              false,
              new StackFilters.MatchOptions(false, false));
      helper.assertValueEqual(
          tally.measured(), 1L, "stone predicts ordinary and silk-touch alternative");
    }
    helper.assertBlockPresent(Blocks.STONE, source);
    helper.setBlock(source, Blocks.DIAMOND_ORE);
    helper.getLevel().getRandom().setSeed(123456L);
    for (Item item : List.of(Items.DIAMOND, Items.DIAMOND_ORE)) {
      tally.filter =
          new StackFilters.Filter(
              List.of(new StackFilters.Sample(new ItemStack(item), 1)),
              false,
              new StackFilters.MatchOptions(false, false));
      helper.assertValueEqual(
          tally.measured(), 1L, "ore predicts tool-required diamond and silk ore");
      helper.assertValueEqual(tally.measured(), 1L, "repeated predictions are stable");
    }
    helper.assertValueEqual(
        helper.getLevel().getRandom().nextLong(),
        net.minecraft.util.RandomSource.create(123456L).nextLong(),
        "prediction does not consume world RNG");
    helper.assertBlockPresent(Blocks.DIAMOND_ORE, source);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void wandHolderReaderUsesRealWandCapacity(GameTestHelper helper) {
    BlockPos pedestalPos = new BlockPos(5, 2, 3);
    helper.setBlock(
        pedestalPos,
        BuiltInRegistries.BLOCK.get(
            ResourceLocation.fromNamespaceAndPath("thaumaturge", "recharge_pedestal")));
    var pedestal =
        (com.leclowndu93150.thaumaturge.content.aura.BlockEntityRechargePedestal)
            helper.getBlockEntity(pedestalPos);
    var starter = com.leclowndu93150.thaumaturge.content.wands.WandParts.starter();
    Item item =
        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("thaumaturge", "wand"));
    ItemStack wand =
        com.leclowndu93150.thaumaturge.content.wands.ItemWand.create(
            item, starter.cap(), starter.rod(), false);
    for (var aspect : TTAspects.PRIMALS)
      wand =
          com.leclowndu93150.thaumaturge.api.wands.WandAccess.withVis(
              wand, aspect, starter.maxCentivis() / 2);
    pedestal.setItem(wand);
    var reading =
        ThaumaturgeIntegration.read(
            helper.getLevel(), helper.absolutePos(pedestalPos), Direction.UP);
    helper.assertTrue(
        reading.valid() && reading.automaticCapacity(),
        "recharge pedestal exposes actual wand capacity");
    helper.assertValueEqual(
        ThaumaturgeIntegration.signal(reading, 0, 0, false, 0),
        8,
        "six half-full primal stores normalize against six capacities");
    helper.assertValueEqual(
        ThaumaturgeIntegration.signal(reading, 3, 0, false, 1),
        6,
        "aspect count is direct and ignores capacity");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void partialAndChangedInsertionRetainsEveryItem(GameTestHelper helper) {
    var source = new net.neoforged.neoforge.items.ItemStackHandler(1);
    source.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 64));
    var destination = new net.neoforged.neoforge.items.ItemStackHandler(1);
    destination.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 60));
    var result = Transfers.moveItems(source, destination, StackFilters.Filter.all(), 16);
    helper.assertValueEqual(result.moved(), 4, "only real capacity moves");
    helper.assertValueEqual(source.getStackInSlot(0).getCount(), 60, "source retains excess");
    helper.assertValueEqual(destination.getStackInSlot(0).getCount(), 64, "destination fills");
    helper.assertTrue(result.leftovers().isEmpty(), "ordinary insertion has no custody remainder");
    var changingDestination =
        new net.neoforged.neoforge.items.ItemStackHandler(1) {
          @Override
          public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return simulate ? ItemStack.EMPTY : stack;
          }
        };
    result = Transfers.moveItems(source, changingDestination, StackFilters.Filter.all(), 16);
    helper.assertValueEqual(result.moved(), 0, "simulation does not promise real insertion");
    helper.assertValueEqual(
        source.getStackInSlot(0).getCount(), 60, "rejected commit returns to source");
    var refusingSource =
        new net.neoforged.neoforge.items.ItemStackHandler(1) {
          @Override
          public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
          }
        };
    refusingSource.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 16));
    result =
        Transfers.moveItems(refusingSource, changingDestination, StackFilters.Filter.all(), 16);
    helper.assertValueEqual(
        result.leftovers().getFirst().getCount(),
        16,
        "failed rollback returns explicit physical custody");
    helper.assertValueEqual(
        refusingSource.getStackInSlot(0).getCount(), 0, "custody is not duplicated in source");
    helper.succeed();
  }

  private static final BlockPos DEVICE = new BlockPos(3, 2, 3);

  private static LoadedTargets.Target target(
      GameTestHelper helper, BlockPos relative, Direction side) {
    return new LoadedTargets.Target(
        helper.getLevel().dimension(), helper.absolutePos(relative), side);
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void inverterRespondsToRearSource(GameTestHelper helper) {
    helper.setBlock(DEVICE, SignalModule.INVERTER.get());
    helper.setBlock(DEVICE.south(), Blocks.REDSTONE_BLOCK);
    helper.runAfterDelay(
        3,
        () -> {
          DeviceEntity inverter = helper.getBlockEntity(DEVICE);
          helper.assertValueEqual(inverter.signal(), 0, "rear 15 inverts to zero");
          helper.setBlock(DEVICE.south(), Blocks.AIR);
        });
    helper.runAfterDelay(
        6,
        () -> {
          DeviceEntity inverter = helper.getBlockEntity(DEVICE);
          helper.assertValueEqual(inverter.signal(), 15, "removed source inverts to 15");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 140)
  public static void hourglassPulseAndPersistence(GameTestHelper helper) {
    helper.setBlock(DEVICE, SignalModule.HOURGLASS.get());
    SignalModule.ClockEntity clock = helper.getBlockEntity(DEVICE);
    clock.activate();
    helper.runAfterDelay(
        20,
        () -> {
          int elapsed = clock.timerState().elapsed();
          var saved = clock.saveWithFullMetadata(helper.getLevel().registryAccess());
          clock.loadWithComponents(saved, helper.getLevel().registryAccess());
          helper.assertValueEqual(
              clock.timerState().elapsed(), elapsed, "elapsed survives save/load");
          helper.assertTrue(
              clock.comparator() > 0 && clock.comparator() < 15, "comparator reports in-progress");
        });
    helper.runAfterDelay(
        100, () -> helper.assertValueEqual(clock.signal(), 15, "five seconds ends in a pulse"));
    helper.runAfterDelay(
        104,
        () -> {
          helper.assertValueEqual(clock.signal(), 0, "pulse stops");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void amplifiedCycleTurnsOff(GameTestHelper helper) {
    var positions =
        List.of(
            new BlockPos(4, 2, 4),
            new BlockPos(5, 2, 4),
            new BlockPos(5, 2, 5),
            new BlockPos(4, 2, 5));
    for (BlockPos pos : positions) helper.setBlock(pos.below(), Blocks.STONE);
    for (BlockPos pos : positions)
      helper.setBlock(pos, RedcrystalNetwork.BLOCKS.get(RedcrystalNetwork.Variant.AMPLIFIED).get());
    helper.setBlock(3, 2, 4, Blocks.REDSTONE_BLOCK);
    helper.runAfterDelay(
        8,
        () -> {
          for (BlockPos pos : positions) {
            DeviceEntity crystal = helper.getBlockEntity(pos);
            helper.assertValueEqual(
                crystal.internalSignal(), 31, "amplified graph receives extended power");
            helper.assertValueEqual(crystal.signal(), 15, "vanilla is clamped");
          }
          helper.setBlock(3, 2, 4, Blocks.AIR);
        });
    helper.runAfterDelay(
        16,
        () -> {
          for (BlockPos pos : positions)
            helper.assertValueEqual(
                ((DeviceEntity) helper.getBlockEntity(pos)).internalSignal(),
                0,
                "source-free amplifier cycle turns off");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void resonanceCrossesSolidsLosslessly(GameTestHelper helper) {
    helper.setBlock(2, 1, 3, Blocks.STONE);
    helper.setBlock(8, 1, 3, Blocks.STONE);
    helper.setBlock(
        2, 2, 3, RedcrystalNetwork.BLOCKS.get(RedcrystalNetwork.Variant.RESONATING).get());
    helper.setBlock(
        8, 2, 3, RedcrystalNetwork.BLOCKS.get(RedcrystalNetwork.Variant.RESONATING).get());
    for (int x = 3; x < 8; x++) helper.setBlock(x, 2, 3, Blocks.STONE);
    helper.setBlock(1, 2, 3, Blocks.REDSTONE_BLOCK);
    helper.runAfterDelay(
        8,
        () -> {
          DeviceEntity receiver = helper.getBlockEntity(new BlockPos(8, 2, 3));
          helper.assertValueEqual(
              receiver.signal(), 15, "wireless strength ignores solid obstacle and distance");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 50)
  public static void remoteComparatorRefusesWrongDimensionAndRange(GameTestHelper helper) {
    helper.setBlock(DEVICE, SensingModule.REMOTE.get());
    BlockPos chestPos = new BlockPos(6, 2, 3);
    helper.setBlock(chestPos, Blocks.CHEST);
    ChestBlockEntity chest = helper.getBlockEntity(chestPos);
    chest.setItem(0, new ItemStack(Items.DIAMOND, 64));
    SensingModule.RemoteEntity remote = helper.getBlockEntity(DEVICE);
    remote.target = target(helper, chestPos, Direction.UP);
    helper.runAfterDelay(
        8,
        () -> {
          helper.assertTrue(remote.signal() > 0, "loaded chest comparator is visible");
          remote.target =
              new LoadedTargets.Target(Level.NETHER, helper.absolutePos(chestPos), Direction.UP);
        });
    helper.runAfterDelay(
        16,
        () -> {
          helper.assertValueEqual(remote.signal(), 0, "cross-dimension target rejected");
          remote.target =
              new LoadedTargets.Target(
                  helper.getLevel().dimension(),
                  helper.absolutePos(chestPos).offset(100000, 0, 0),
                  Direction.UP);
        });
    helper.runAfterDelay(
        24,
        () -> {
          helper.assertValueEqual(remote.signal(), 0, "distant target rejected");
          helper.assertFalse(
              helper.getLevel().hasChunkAt(remote.target.pos()),
              "reader has not loaded target chunk");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void tallyCountsFilteredItemsAndRejectsCycles(GameTestHelper helper) {
    helper.setBlock(DEVICE, SensingModule.TALLY.get());
    BlockPos second = DEVICE.east(2), chestPos = DEVICE.east(4);
    helper.setBlock(second, SensingModule.TALLY.get());
    helper.setBlock(chestPos, Blocks.CHEST);
    ChestBlockEntity chest = helper.getBlockEntity(chestPos);
    chest.setItem(0, new ItemStack(Items.GOLD_INGOT, 23));
    chest.setItem(1, new ItemStack(Items.IRON_INGOT, 32));
    SensingModule.TallyEntity a = helper.getBlockEntity(DEVICE), b = helper.getBlockEntity(second);
    a.filter =
        new StackFilters.Filter(
            List.of(new StackFilters.Sample(new ItemStack(Items.GOLD_INGOT), 7)),
            false,
            new StackFilters.MatchOptions(false, false));
    a.target = target(helper, second, Direction.UP);
    b.target = target(helper, chestPos, Direction.UP);
    helper.assertValueEqual(a.measured(), 23L, "chain reads only matching real inventory");
    helper.assertValueEqual(chest.getItem(0).getCount(), 23, "tally never extracts");
    b.target = target(helper, DEVICE, Direction.UP);
    helper.assertValueEqual(a.measured(), 0L, "cycle terminates without invented stock");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void filterDamageComponentsQuantityAndCopy(GameTestHelper helper) {
    ItemStack sample = new ItemStack(Items.IRON_PICKAXE);
    sample.setDamageValue(5);
    ItemStack other = sample.copy();
    other.setDamageValue(6);
    var options = new StackFilters.MatchOptions(true, false, false, false);
    var filter =
        new StackFilters.Filter(List.of(new StackFilters.Sample(sample, 12)), false, options);
    helper.assertTrue(
        StackFilters.accepts(filter, other), "damage may be ignored independently of components");
    var strict =
        new StackFilters.Filter(
            filter.samples(), false, new StackFilters.MatchOptions(true, true, false, false));
    helper.assertFalse(StackFilters.accepts(strict, other), "damage option enforces wear");
    other.set(DataComponents.CUSTOM_NAME, Component.literal("Renamed"));
    helper.assertFalse(StackFilters.accepts(filter, other), "components reject renamed item");
    var copy =
        StackFilters.load(
            StackFilters.save(filter.converted(), helper.getLevel().registryAccess()),
            helper.getLevel().registryAccess());
    helper.assertTrue(copy.blacklist(), "conversion polarity survives copy");
    helper.assertValueEqual(
        StackFilters.configuredQuantity(copy, sample), 12, "quantity survives copy");
    sample.setDamageValue(10);
    helper.assertValueEqual(
        filter.samples().getFirst().template().getDamageValue(),
        5,
        "sample owns an immutable defensive copy");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void hostJarReaderUsesActualCapacityWithoutDrain(GameTestHelper helper) {
    BlockPos jarPos = new BlockPos(5, 2, 3);
    Block jar =
        BuiltInRegistries.BLOCK.get(
            ResourceLocation.fromNamespaceAndPath("thaumaturge", "jar_normal"));
    helper.setBlock(jarPos, jar);
    BlockEntityJar be = helper.getBlockEntity(jarPos);
    be.setStorageContents(TTAspects.AER, be.capacity() / 2);
    helper.setBlock(DEVICE, SensingModule.VIS.get());
    SensingModule.VisEntity reader = helper.getBlockEntity(DEVICE);
    reader.target = target(helper, jarPos, Direction.UP);
    helper.runAfterDelay(
        15,
        () -> {
          helper.assertValueEqual(reader.signal(), 8, "host jar half-fullness rounds upward");
          helper.assertValueEqual(be.amount(), be.capacity() / 2, "reading consumes no essentia");
          helper.assertTrue(reader.reading().automaticCapacity(), "actual jar capacity available");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void redcrystalDirectionAndAdjustableThresholds(GameTestHelper helper) {
    BlockPos aPos = new BlockPos(3, 2, 3), bPos = aPos.east(), cPos = bPos.east();
    for (BlockPos pos : List.of(aPos, bPos, cPos)) helper.setBlock(pos.below(), Blocks.STONE);
    helper.setBlock(aPos, RedcrystalNetwork.BLOCKS.get(RedcrystalNetwork.Variant.NORMAL).get());
    helper.setBlock(bPos, RedcrystalNetwork.BLOCKS.get(RedcrystalNetwork.Variant.DIM).get());
    helper.setBlock(cPos, RedcrystalNetwork.BLOCKS.get(RedcrystalNetwork.Variant.DENSE).get());
    RedcrystalNetwork.CrystalEntity a = helper.getBlockEntity(aPos),
        b = helper.getBlockEntity(bPos),
        c = helper.getBlockEntity(cPos);
    a.settings[0] = 1 << (Direction.WEST.ordinal() * 2) | 2 << (Direction.EAST.ordinal() * 2);
    b.settings[2] = 11;
    c.settings[3] = 10;
    helper.setBlock(aPos.west(), Blocks.REDSTONE_BLOCK);
    helper.runAfterDelay(
        8,
        () -> {
          helper.assertValueEqual(a.internalSignal(), 15, "input direction accepts source");
          helper.assertValueEqual(b.internalSignal(), 11, "configured dim cap");
          helper.assertValueEqual(c.internalSignal(), 10, "configured dense threshold accepts ten");
          a.configure(Direction.EAST.ordinal(), -1); // output -> input
        });
    helper.runAfterDelay(
        16,
        () -> {
          helper.assertValueEqual(b.internalSignal(), 0, "input-only face cannot emit to neighbor");
          helper.assertValueEqual(c.internalSignal(), 0, "disconnected downstream chain turns off");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void cornerRoutingRequiresSharedSupportGeometry(GameTestHelper helper) {
    BlockPos support = new BlockPos(4, 2, 4), aPos = support.above(), bPos = support.east();
    helper.setBlock(support, Blocks.STONE);
    helper.setBlock(aPos, RedcrystalNetwork.BLOCKS.get(RedcrystalNetwork.Variant.NORMAL).get());
    helper.setBlock(
        bPos,
        RedcrystalNetwork.BLOCKS
            .get(RedcrystalNetwork.Variant.NORMAL)
            .get()
            .defaultBlockState()
            .setValue(RedcrystalNetwork.CrystalBlock.ATTACHMENT, Direction.WEST));
    helper.setBlock(aPos.west(), Blocks.REDSTONE_BLOCK);
    RedcrystalNetwork.CrystalEntity corner = helper.getBlockEntity(bPos);
    helper.runAfterDelay(
        8,
        () -> {
          helper.assertValueEqual(
              corner.internalSignal(), 13, "signal wraps shared support corner with two-step loss");
          corner.settings[1] = 0;
        });
    helper.runAfterDelay(
        16,
        () -> {
          helper.assertValueEqual(
              corner.internalSignal(), 0, "corner control disables wrapped connection");
          helper.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void signumRespectsEnclosureAgeAndEntityType(GameTestHelper helper) {
    BlockPos pos = new BlockPos(5, 2, 5);
    helper.setBlock(pos, SensingModule.SIGNUM.get());
    SensingModule.SignumEntity signum = helper.getBlockEntity(pos);
    signum.entityType = "minecraft:cow";
    var cow = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW, pos.north(2));
    cow.setNoGravity(true);
    for (Direction side : Direction.values()) helper.setBlock(pos.relative(side), Blocks.STONE);
    helper.assertValueEqual(signum.measured(), 0L, "sealed front cannot see living entity");
    helper.setBlock(pos.north(), Blocks.AIR);
    helper.assertValueEqual(signum.measured(), 1L, "opening front admits cow");
    signum.settings[1] = 2;
    helper.assertValueEqual(signum.measured(), 0L, "child mode excludes adult");
    cow.setAge(-1000);
    helper.assertValueEqual(signum.measured(), 1L, "child mode includes calf");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void tagAlternativesAndDeviceTargetPersistence(GameTestHelper helper) {
    var tagged =
        new StackFilters.Filter(
            List.of(new StackFilters.Sample(new ItemStack(Items.OAK_LOG), 3)),
            false,
            new StackFilters.MatchOptions(false, false, true, false));
    helper.assertTrue(
        StackFilters.accepts(tagged, new ItemStack(Items.BIRCH_LOG)),
        "shared logs tag accepts alternate wood");
    helper.assertFalse(
        StackFilters.accepts(tagged, new ItemStack(Items.DIAMOND)), "unrelated item is rejected");
    helper.setBlock(DEVICE, SensingModule.TALLY.get());
    SensingModule.TallyEntity tally = helper.getBlockEntity(DEVICE);
    tally.target = target(helper, DEVICE.east(3), Direction.DOWN);
    tally.filter = tagged;
    tally.settings[0] = 12;
    var saved = tally.saveWithFullMetadata(helper.getLevel().registryAccess());
    tally.target = null;
    tally.filter = StackFilters.Filter.all();
    tally.settings[0] = 0;
    tally.loadWithComponents(saved, helper.getLevel().registryAccess());
    helper.assertValueEqual(tally.settings[0], 12, "device controls persist");
    helper.assertValueEqual(
        tally.target,
        target(helper, DEVICE.east(3), Direction.DOWN),
        "target dimension position and face persist");
    helper.assertTrue(
        StackFilters.accepts(tally.filter, new ItemStack(Items.BIRCH_LOG)),
        "matching options persist");
    helper.succeed();
  }
}
