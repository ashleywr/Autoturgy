package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.content.essentia.smeltery.BlockEntityAlembic;
import com.sanhiruzu.autoturgy.fluids.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class FluidGameTests {
  static final BlockPos P = new BlockPos(4, 3, 4);

  static FluidModule.TankEntity tank(GameTestHelper h, BlockPos p) {
    h.setBlock(p, FluidModule.TANK.get());
    return h.getBlockEntity(p);
  }

  static IFluidHandler fluid(FluidModule.TankEntity tank) {
    return tank.handler(Direction.UP);
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void fullTankRefusesSourceAndRenewableWaterConserves(GameTestHelper h) {
    var tank = tank(h, P);
    h.setBlock(P.east(), Blocks.WATER);
    fluid(tank).fill(new FluidStack(Fluids.WATER, 16000), IFluidHandler.FluidAction.EXECUTE);
    h.assertFalse(tank.absorb(), "full tank refuses intake");
    h.assertBlockPresent(Blocks.WATER, P.east());
    fluid(tank).drain(1000, IFluidHandler.FluidAction.EXECUTE);
    h.assertTrue(tank.absorb(), "renewable adjacent water accepted");
    h.assertBlockPresent(Blocks.WATER, P.east());
    h.assertValueEqual(tank.tank.getFluidAmount(), 16000, "exact one bucket added");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void sourceSearchFollowsMatchingConnectedPathOnly(GameTestHelper h) {
    var tank = tank(h, P);
    h.setBlock(P.east(), Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 4));
    h.setBlock(P.east(2), Blocks.LAVA);
    h.assertTrue(tank.absorb(), "flowing connected lava reaches its source");
    h.assertBlockPresent(Blocks.AIR, P.east(2));
    h.assertValueEqual(tank.tank.getFluidAmount(), 1000, "source becomes exactly one bucket");
    h.setBlock(P.east(), Blocks.AIR);
    h.setBlock(P.east(3), Blocks.LAVA);
    h.assertFalse(tank.absorb(), "disconnected matching source cannot be reached");
    h.assertBlockPresent(Blocks.LAVA, P.east(3));
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void glyphReserveVoidReservoirAndReload(GameTestHelper h) {
    var tank = tank(h, P);
    tank.apply(Direction.UP, FluidModule.Glyph.RESERVOIR);
    tank.apply(Direction.DOWN, FluidModule.Glyph.PRESERVATION);
    fluid(tank).fill(new FluidStack(Fluids.WATER, 20000), IFluidHandler.FluidAction.EXECUTE);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    h.assertFalse(tank.removeGlyph(Direction.UP, player), "reservoir cannot strand excess");
    var saved = tank.saveWithFullMetadata(h.getLevel().registryAccess());
    tank.loadWithComponents(saved, h.getLevel().registryAccess());
    h.assertValueEqual(tank.capacity(), 32000, "capacity glyph persists");
    h.assertValueEqual(
        fluid(tank).drain(50000, IFluidHandler.FluidAction.EXECUTE).getAmount(),
        19999,
        "reserve prevents emptying");
    h.assertTrue(tank.removeGlyph(Direction.UP, player), "safe removal returns physical glyph");
    h.assertValueEqual(tank.tank.getFluidAmount(), 1, "removal preserves all fluid");
    tank.apply(Direction.NORTH, FluidModule.Glyph.VOID);
    h.assertValueEqual(
        fluid(tank).fill(new FluidStack(Fluids.WATER, 20000), IFluidHandler.FluidAction.EXECUTE),
        20000,
        "void accepts overflow");
    h.assertValueEqual(tank.tank.getFluidAmount(), 16000, "only overflow discarded");
    h.assertValueEqual(
        fluid(tank).fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE),
        0,
        "void does not mix incompatible fluids");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void temperanceGatesSidedBucketAndPipeIntake(GameTestHelper h) {
    var tank = tank(h, P);
    tank.apply(Direction.UP, FluidModule.Glyph.TEMPERANCE);
    var cap =
        h.getLevel()
            .getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(P), Direction.EAST);
    h.assertTrue(cap != null, "real sided capability registered");
    h.assertValueEqual(
        cap.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE),
        0,
        "unpowered sided intake refuses");
    h.setBlock(P.west(), Blocks.REDSTONE_BLOCK);
    h.assertValueEqual(
        cap.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE),
        500,
        "powered sided intake accepts");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void envySiphonAndConsumptionConserveActualFluid(GameTestHelper h) {
    FluidModule.TankEntity source = tank(h, P.west()),
        center = tank(h, P),
        destination = tank(h, P.east());
    fluid(source).fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
    fluid(destination).fill(new FluidStack(Fluids.WATER, 15750), IFluidHandler.FluidAction.EXECUTE);
    center.apply(Direction.WEST, FluidModule.Glyph.ENVY);
    center.apply(Direction.EAST, FluidModule.Glyph.SIPHONING);
    h.runAfterDelay(
        21,
        () -> {
          h.assertValueEqual(
              source.tank.getFluidAmount()
                  + center.tank.getFluidAmount()
                  + destination.tank.getFluidAmount(),
              17750,
              "partial destination conserves total");
          h.assertValueEqual(
              destination.tank.getFluidAmount(), 16000, "destination accepted only 250");
          center.apply(Direction.DOWN, FluidModule.Glyph.CONSUMPTION);
        });
    h.runAfterDelay(
        41,
        () -> {
          h.assertValueEqual(
              source.tank.getFluidAmount()
                  + center.tank.getFluidAmount()
                  + destination.tank.getFluidAmount(),
              17650,
              "one consumption glyph destroys exactly 100mB");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void bovineCooldownBelongsToCowAcrossTanksAndReload(GameTestHelper h) {
    FluidModule.TankEntity first = tank(h, P), second = tank(h, P.east(2));
    var cow = h.spawnWithNoFreeWill(EntityType.COW, P.east());
    cow.setNoGravity(true);
    first.apply(Direction.EAST, FluidModule.Glyph.BOVINE);
    second.apply(Direction.WEST, FluidModule.Glyph.BOVINE);
    h.assertTrue(first.milk(Direction.EAST), "first tank milks adult cow");
    h.assertFalse(second.milk(Direction.WEST), "second tank shares persisted cow cooldown");
    var saved = first.saveWithFullMetadata(h.getLevel().registryAccess());
    first.loadWithComponents(saved, h.getLevel().registryAccess());
    h.assertFalse(first.milk(Direction.EAST), "tank reload cannot reset cow cooldown");
    h.assertTrue(
        first.tank.getFluid().is(NeoForgeMod.MILK.get()), "standard NeoForge milk identity");
    h.assertValueEqual(first.tank.getFluidAmount(), 1000, "one actual bucket only");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void bottleSoupAndBucketContainerTransfers(GameTestHelper h) {
    var tank = tank(h, P);
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MUSHROOM_STEW));
    h.assertTrue(
        FluidModule.serve(
            fluid(tank), player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND),
        "stew pours");
    h.assertTrue(player.getMainHandItem().is(Items.BOWL), "pour returns bowl");
    h.assertValueEqual(tank.tank.getFluidAmount(), 250, "one serving 250mB");
    h.assertTrue(
        FluidModule.serve(
            fluid(tank), player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND),
        "empty bowl fills");
    h.assertTrue(player.getMainHandItem().is(Items.MUSHROOM_STEW), "real edible stew result");
    h.assertValueEqual(tank.tank.getFluidAmount(), 0, "serving drained once");
    fluid(tank).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
    FluidModule.serve(
        fluid(tank), player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND);
    h.assertTrue(player.getMainHandItem().is(Items.POTION), "water potion filled");
    h.assertValueEqual(tank.tank.getFluidAmount(), 750, "water bottle exact amount");
    var bucket = FluidUtil.getFluidHandler(new ItemStack(Items.BUCKET)).orElseThrow();
    h.assertValueEqual(
        bucket.fill(
            new FluidStack(FluidModule.VISHROOM.source.get(), 1000),
            IFluidHandler.FluidAction.EXECUTE),
        1000,
        "NeoForge bucket fills real soup");
    h.assertTrue(
        bucket.getContainer().is(FluidModule.VISHROOM.bucket.get()), "real placeable soup bucket");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void tankBreakDropRestoresFluidAndGlyphs(GameTestHelper h) {
    var tank = tank(h, P);
    tank.apply(Direction.UP, FluidModule.Glyph.RESERVOIR);
    fluid(tank).fill(new FluidStack(Fluids.LAVA, 19000), IFluidHandler.FluidAction.EXECUTE);
    h.getLevel().destroyBlock(h.absolutePos(P), true);
    var drops =
        h.getLevel()
            .getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(h.absolutePos(P)).inflate(1));
    h.assertValueEqual(drops.size(), 1, "one conserved tank drop");
    ItemStack drop = drops.getFirst().getItem();
    h.setBlock(P, FluidModule.TANK.get());
    FluidModule.TANK
        .get()
        .setPlacedBy(
            h.getLevel(), h.absolutePos(P), FluidModule.TANK.get().defaultBlockState(), null, drop);
    FluidModule.TankEntity restored = h.getBlockEntity(P);
    h.assertValueEqual(restored.tank.getFluidAmount(), 19000, "drop restores exact fluid");
    h.assertValueEqual(restored.capacity(), 32000, "drop restores glyph");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void boilerSubtractsActualEmptyContainerAspects(GameTestHelper h) {
    for (Fluid f : new Fluid[] {Fluids.WATER, Fluids.LAVA, NeoForgeMod.MILK.get()}) {
      AspectList actual = EssentiaBoiler.yield(new FluidStack(f, 1000)),
          full = AspectIndexAccess.of(f.getBucket()),
          empty = AspectIndexAccess.of(Items.BUCKET);
      h.assertTrue(!actual.isEmpty(), "host index must produce supported nonempty fluid yield");
      for (AspectInstance a : full.entries())
        h.assertValueEqual(
            actual.amountOf(a.aspect()),
            Math.max(0, a.amount() - empty.amountOf(a.aspect())),
            "exact net bucket yield");
      h.assertValueEqual(
          actual.amountOf(Aspects.resolve(h.getLevel(), TTAspects.METALLUM)),
          0,
          "fluid cannot produce bucket metal");
    }
    h.assertTrue(
        EssentiaBoiler.yield(new FluidStack(Fluids.EMPTY, 1000)).isEmpty(),
        "unsupported fluid has no invented yield");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void boilerFuelFullOutputAndPaidPendingReload(GameTestHelper h) {
    h.setBlock(P, EssentiaBoiler.BLOCK.get());
    h.setBlock(P.above(), LogisticsGameTests.host("alembic"));
    EssentiaBoiler.BoilerEntity boiler = h.getBlockEntity(P);
    BlockEntityAlembic alembic = h.getBlockEntity(P.above());
    boiler.tank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
    h.runAfterDelay(
        5,
        () -> {
          h.assertValueEqual(
              boiler.tank.getFluidAmount(), 1000, "unfueled boiler cannot consume fluid");
          h.assertValueEqual(alembic.amount(), 0, "unfueled boiler creates nothing");
          alembic.setStorageContents(TTAspects.AQUA, alembic.capacity());
          boiler.fuel.setStackInSlot(0, new ItemStack(Items.COAL));
        });
    h.runAfterDelay(
        15,
        () -> {
          h.assertValueEqual(boiler.tank.getFluidAmount(), 1000, "full output stalls intact");
          h.assertValueEqual(
              boiler.fuel.getStackInSlot(0).getCount(), 1, "blocked output does not burn fuel");
          alembic.setStorageContents(null, 0);
        });
    h.runAfterDelay(
        120,
        () -> {
          int expected = EssentiaBoiler.yield(new FluidStack(Fluids.WATER, 1000)).totalAmount();
          h.assertValueEqual(boiler.tank.getFluidAmount(), 0, "one paid whole batch consumed");
          h.assertValueEqual(alembic.amount(), expected, "real alembic contains actual host yield");
          h.assertTrue(boiler.fuel.getStackInSlot(0).isEmpty(), "actual fuel consumed");
          boiler.pending = AspectList.EMPTY.add(Aspects.resolve(h.getLevel(), TTAspects.AQUA), 3);
          var saved = boiler.saveWithFullMetadata(h.getLevel().registryAccess());
          boiler.pending = AspectList.EMPTY;
          boiler.loadWithComponents(saved, h.getLevel().registryAccess());
          h.assertValueEqual(boiler.pending.totalAmount(), 3, "paid pending survives reload");
          boiler.flush();
          h.assertValueEqual(alembic.amount(), expected + 3, "pending transferred once");
          boiler.flush();
          h.assertValueEqual(alembic.amount(), expected + 3, "second flush creates nothing");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void pouringMilkBucketHasRealItemCapabilityAndDrinksMilk(GameTestHelper h) {
    ItemStack item = new ItemStack(FluidModule.MILK_BUCKET.get());
    var handler = FluidUtil.getFluidHandler(item).orElseThrow();
    h.assertTrue(
        handler.getFluidInTank(0).is(NeoForgeMod.MILK.get()),
        "custom milk bucket exposes standard fluid");
    h.assertValueEqual(
        handler.drain(1000, IFluidHandler.FluidAction.EXECUTE).getAmount(),
        1000,
        "custom bucket transfers a full bucket");
    h.assertTrue(
        handler.getContainer().is(Items.BUCKET), "draining returns exactly one empty bucket");
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.addEffect(
        new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.POISON, 100));
    ItemStack result =
        FluidModule.MILK_BUCKET
            .get()
            .finishUsingItem(new ItemStack(FluidModule.MILK_BUCKET.get()), h.getLevel(), player);
    h.assertFalse(
        player.hasEffect(net.minecraft.world.effect.MobEffects.POISON),
        "milk retains real cure effect");
    h.assertTrue(result.is(Items.BUCKET), "drinking returns bucket");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void full32BitMenuTransportPreservesMaximumReservoirAndThresholds(
      GameTestHelper h) {
    var tank = tank(h, P);
    for (Direction d : Direction.values()) tank.apply(d, FluidModule.Glyph.RESERVOIR);
    fluid(tank).fill(new FluidStack(Fluids.WATER, 112000), IFluidHandler.FluidAction.EXECUTE);
    h.assertValueEqual(tank.capacity(), 112000, "six reservoirs compose");
    var logical = new net.minecraft.world.inventory.SimpleContainerData(3);
    var client = new net.minecraft.world.inventory.SimpleContainerData(3);
    logical.set(0, tank.settings[6]);
    logical.set(1, Integer.MAX_VALUE);
    logical.set(2, -112000);
    var outgoing = com.sanhiruzu.autoturgy.common.MenuIntTransport.wire(logical);
    var incoming = com.sanhiruzu.autoturgy.common.MenuIntTransport.wire(client);
    for (int i = 0; i < outgoing.getCount(); i++) {
      var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
      var packet =
          new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(
              7, i, outgoing.get(i));
      net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC.encode(
          buf, packet);
      var received =
          net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC.decode(
              buf);
      incoming.set(received.getId(), received.getValue());
      buf.release();
    }
    for (int i = 0; i < 3; i++)
      h.assertValueEqual(
          client.get(i),
          logical.get(i),
          "actual vanilla menu packet preserves full signed32 value");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void vishroomFoodUsesActualHostTemporaryWarpAndBowl(GameTestHelper h) {
    var player = new ProgressionGameTests.TestPlayer(h.getLevel());
    player.setGameMode(GameType.SURVIVAL);
    int before =
        com.leclowndu93150.thaumaturge.api.warp.WarpHelper.getWarp(player)
            .get(com.leclowndu93150.thaumaturge.api.warp.WarpType.TEMPORARY);
    ItemStack result =
        FluidModule.VISHROOM
            .bowl
            .get()
            .finishUsingItem(new ItemStack(FluidModule.VISHROOM.bowl.get()), h.getLevel(), player);
    h.assertTrue(result.is(Items.BOWL), "actual stew consumption returns bowl");
    h.assertTrue(
        player.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION)
            && player.hasEffect(net.minecraft.world.effect.MobEffects.HUNGER),
        "stew meaningful side effects");
    h.assertTrue(
        com.leclowndu93150.thaumaturge.api.warp.WarpHelper.getWarp(player)
                .get(com.leclowndu93150.thaumaturge.api.warp.WarpType.TEMPORARY)
            > before,
        "actual host warp increases");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void sourceNeighborCallbackIntakeRefusalRetainsPaidFluidCustody(GameTestHelper h) {
    var tank = tank(h, P);
    BlockPos source = P.east();
    h.setBlock(source, Blocks.LAVA);
    java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent>
        callback =
            event -> {
              if (event.getLevel() == h.getLevel()
                  && event.getPos().equals(h.absolutePos(source))
                  && h.getLevel().getFluidState(h.absolutePos(source)).isEmpty())
                tank.apply(Direction.UP, FluidModule.Glyph.TEMPERANCE);
            };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(callback);
    try {
      h.assertTrue(tank.absorb(), "source pickup commits before callback changes intake");
    } finally {
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(callback);
    }
    var saved = tank.saveWithFullMetadata(h.getLevel().registryAccess());
    h.assertTrue(
        tank.count(FluidModule.Glyph.TEMPERANCE) == 1,
        "actual source neighbor callback changed tank intake");
    h.assertValueEqual(
        tank.tank.getFluidAmount(), 0, "reentrant intake gate refused immediate storage");
    h.assertValueEqual(
        FluidStack.parseOptional(h.getLevel().registryAccess(), saved.getCompound("custody"))
            .getAmount(),
        1000,
        "consumed source retained as actual paid fluid custody");
    tank.loadWithComponents(saved, h.getLevel().registryAccess());
    tank.serverTick();
    h.assertValueEqual(
        tank.tank.getFluidAmount(),
        1000,
        "reloaded custody restored safely without a second source");
    h.assertBlockPresent(Blocks.AIR, source);
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void guzzlerAcceleratesBoundedConnectedSourceCollection(GameTestHelper h) {
    FluidModule.TankEntity normal = tank(h, P), fast = tank(h, P.south(4));
    h.setBlock(P.east(), Blocks.WATER);
    h.setBlock(P.south(4).east(), Blocks.WATER);
    fast.apply(Direction.UP, FluidModule.Glyph.GUZZLER);
    h.runAfterDelay(
        41,
        () -> {
          int ordinary = normal.tank.getFluidAmount(), accelerated = fast.tank.getFluidAmount();
          h.assertTrue(
              ordinary >= 2000 && ordinary <= 3000,
              "ordinary collection bounded independently of world-time phase");
          h.assertTrue(
              accelerated >= 4000 && accelerated <= 5000,
              "guzzler collection bounded independently of world-time phase");
          h.assertTrue(accelerated > ordinary, "physical source collection accelerates");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void actualVishroomSourceGrowthAndExposureAreBounded(GameTestHelper h) {
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++) h.setBlock(P.offset(x, -1, z), Blocks.MYCELIUM);
    h.setBlock(P, FluidModule.VISHROOM.block.get());
    var state = h.getLevel().getBlockState(h.absolutePos(P));
    h.assertTrue(state.isRandomlyTicking(), "source is eligible for natural random block ticks");
    var chunk = h.getLevel().getChunkAt(h.absolutePos(P));
    h.assertTrue(
        chunk.getSection(chunk.getSectionIndex(h.absolutePos(P).getY())).isRandomlyTickingBlocks(),
        "owning section is scheduled for natural block ticks");
    var player = new ProgressionGameTests.TestPlayer(h.getLevel());
    var warp = com.leclowndu93150.thaumaturge.api.warp.WarpHelper.getWarp(player);
    int before = warp.get(com.leclowndu93150.thaumaturge.api.warp.WarpType.TEMPORARY);
    for (int i = 0; i < 10; i++) state.entityInside(h.getLevel(), h.absolutePos(P), player);
    h.assertValueEqual(
        warp.get(com.leclowndu93150.thaumaturge.api.warp.WarpType.TEMPORARY),
        before + 1,
        "repeated immersion callbacks share exposure cooldown");
    for (int i = 0; i < 200; i++)
      state.randomTick(h.getLevel(), h.absolutePos(P), net.minecraft.util.RandomSource.create(i));
    int fungi = 0;
    for (BlockPos p : BlockPos.betweenClosed(P.offset(-2, 0, -2), P.offset(2, 0, 2)))
      if (h.getLevel().getBlockState(h.absolutePos(p)).is(LogisticsGameTests.host("vishroom")))
        fungi++;
    h.assertTrue(
        fungi > 0 && fungi <= 24, "source grows actual host fungi within bounded neighborhood");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void milkConversionRecipeNeverCreatesEmptyBucketRemainders(GameTestHelper h) {
    var holder =
        h.getLevel().getRecipeManager().byKey(Content.id("milk_bucket_conversion")).orElseThrow();
    var recipe = (com.sanhiruzu.autoturgy.progression.MilkBucketConversionRecipe) holder.value();
    ItemStack current = new ItemStack(Items.MILK_BUCKET);
    for (int i = 0; i < 6; i++) {
      var input =
          net.minecraft.world.item.crafting.CraftingInput.of(1, 1, java.util.List.of(current));
      h.assertTrue(recipe.matches(input, h.getLevel()), "both physical milk buckets convert");
      h.assertTrue(
          recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
          "the bucket remains in output, never as a second container");
      current = recipe.assemble(input, h.getLevel().registryAccess());
      h.assertValueEqual(current.getCount(), 1, "one filled bucket remains");
      h.assertTrue(
          FluidUtil.getFluidContained(current).orElseThrow().is(NeoForgeMod.MILK.get()),
          "same standard milk survives each conversion");
    }
    h.assertTrue(
        current.is(Items.MILK_BUCKET), "round trips retain original fluid and one container");
    h.succeed();
  }
}
