package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.essentia.*;
import com.leclowndu93150.thaumaturge.api.recipe.*;
import com.leclowndu93150.thaumaturge.content.aura.node.*;
import com.leclowndu93150.thaumaturge.content.research.ResearchManager;
import com.leclowndu93150.thaumaturge.content.warding.WardHandler;
import com.leclowndu93150.thaumaturge.registry.*;
import com.sanhiruzu.autoturgy.equipment.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.gametest.*;
import top.theillusivec4.curios.api.CuriosApi;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class EquipmentIntegrationGameTests {
  static final BlockPos P = new BlockPos(4, 3, 4);

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void miserRefusesUnpaidManualThrowAndPaysActualWand(GameTestHelper h) {
    var p = ExperienceEquipmentGameTests.player(h);
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CollectingPearls.MISER.get()));
    var unpaid = p.getMainHandItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
    h.assertTrue(unpaid.getResult() == InteractionResult.FAIL, "unpaid launch refused");
    h.assertValueEqual(p.getMainHandItem().getCount(), 1, "unpaid pearl stays physical");
    p.getInventory().setItem(1, ExperienceEquipmentGameTests.wand(h, 100));
    p.getMainHandItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
    h.assertValueEqual(
        p.getInventory()
            .getItem(1)
            .get(com.leclowndu93150.thaumaturge.registry.TTDataComponents.WAND_VIS.get())
            .amount(TTAspects.ORDO),
        75,
        "paid Ordo25");
    h.assertTrue(p.getMainHandItem().isEmpty(), "one pearl in flight");
    h.assertValueEqual(
        h.getLevel()
            .getEntitiesOfClass(CollectingPearls.Pearl.class, new AABB(h.absolutePos(P)).inflate(4))
            .size(),
        1,
        "actual projectile spawned");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void realThrownMiserCollectsTenStacksAndReturnsOnePearl(GameTestHelper h) {
    var p = ExperienceEquipmentGameTests.player(h);
    var pearl = new CollectingPearls.Pearl(CollectingPearls.TYPE.get(), h.getLevel());
    pearl.setOwner(p);
    ItemStack named = new ItemStack(CollectingPearls.MISER.get());
    named.set(DataComponents.CUSTOM_NAME, Component.literal("Kept Pearl"));
    var marker = new CompoundTag();
    marker.putInt("Marker", 37);
    named.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
    pearl.setItem(named);
    pearl.setPos(h.absolutePos(P).getCenter());
    pearl.setNoGravity(true);
    h.getLevel().addFreshEntity(pearl);
    for (int i = 0; i < 11; i++) {
      var item =
          new ItemEntity(
              h.getLevel(),
              pearl.getX() + .2,
              pearl.getY(),
              pearl.getZ(),
              new ItemStack(Items.DIAMOND));
      h.getLevel().addFreshEntity(item);
    }
    pearl.tick();
    h.assertValueEqual(pearl.stacks, 10, "ten-stack cap");
    h.assertValueEqual(
        ExperienceEquipmentGameTests.count(p, Items.DIAMOND), 10, "actual loose items delivered");
    pearl.finish();
    pearl.finish();
    h.assertValueEqual(
        ExperienceEquipmentGameTests.count(p, CollectingPearls.MISER.get()),
        1,
        "once-only physical pearl return");
    h.assertValueEqual(p.getHealth(), 20F, "safe Miser no harm");
    ItemStack returned =
        p.getInventory().items.stream()
            .filter(item -> item.is(CollectingPearls.MISER.get()))
            .findFirst()
            .orElseThrow();
    h.assertTrue(
        ItemStack.matches(returned, named),
        "surviving pearl returns all actual name and data components");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void fullPearlDestinationLeavesAllLooseRemainders(GameTestHelper h) {
    var p = ExperienceEquipmentGameTests.player(h);
    for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    var pearl = new CollectingPearls.Pearl(CollectingPearls.TYPE.get(), h.getLevel());
    pearl.setOwner(p);
    pearl.setItem(new ItemStack(CollectingPearls.MISER.get()));
    pearl.setPos(h.absolutePos(P).getCenter());
    pearl.setNoGravity(true);
    h.getLevel().addFreshEntity(pearl);
    var item =
        new ItemEntity(
            h.getLevel(),
            pearl.getX() + .2,
            pearl.getY(),
            pearl.getZ(),
            new ItemStack(Items.DIAMOND, 7));
    h.getLevel().addFreshEntity(item);
    pearl.tick();
    h.assertValueEqual(item.getItem().getCount(), 7, "full inventory retains original loose stack");
    pearl.finish();
    h.assertValueEqual(
        h
            .getLevel()
            .getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(P)).inflate(4))
            .stream()
            .filter(e -> e.getItem().is(CollectingPearls.MISER.get()))
            .count(),
        1L,
        "unaccepted pearl safely drops once");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void curiosRingsUseBothEndpointsHalfStackingAndRealPerditio(GameTestHelper h) {
    var p = ExperienceEquipmentGameTests.player(h);
    h.getLevel().addNewPlayer(p);
    p.getInventory().setItem(0, ExperienceEquipmentGameTests.wand(h, 100));
    var inv = CuriosApi.getCuriosInventory(p).orElseThrow();
    inv.setEquippedCurio("ring", 0, new ItemStack(EquipmentModule.RING.get()));
    inv.setEquippedCurio("ring", 1, new ItemStack(EquipmentModule.RING.get()));
    EnderMan end = EntityType.ENDERMAN.create(h.getLevel());
    end.setPos(p.position().add(2, 0, 0));
    end.setNoAi(true);
    h.getLevel().addFreshEntity(end);
    NeoForge.EVENT_BUS.post(
        new EntityTeleportEvent.EnderEntity(end, p.getX() + 1, p.getY(), p.getZ()));
    h.assertValueEqual(end.getHealth(), 31F, "second full ring contributes half6: total9");
    h.assertValueEqual(
        com.leclowndu93150.thaumaturge.api.wands.WandAccess.getVis(
            p.getMainHandItem(), TTAspects.PERDITIO),
        75,
        "both endpoints charged once");
    end.invulnerableTime = 0;
    end.setPos(p.position().add(40, 0, 0));
    NeoForge.EVENT_BUS.post(
        new EntityTeleportEvent.EnderEntity(end, p.getX() + 1, p.getY(), p.getZ()));
    h.assertValueEqual(end.getHealth(), 22F, "arrival endpoint works");
    p.getInventory().setItem(0, ExperienceEquipmentGameTests.wand(h, 0));
    end.invulnerableTime = 0;
    NeoForge.EVENT_BUS.post(new EntityTeleportEvent.EnderEntity(end, p.getX(), p.getY(), p.getZ()));
    h.assertValueEqual(end.getHealth(), 22F, "no free damage without vis");
    h.assertTrue(
        com.leclowndu93150.thaumaturge.content.infusion.InfusionRunicAugmentRecipe.isShieldable(
            new ItemStack(EquipmentModule.RING.get())),
        "real runic augmentation accepts ring");

    var augmentation =
        (com.leclowndu93150.thaumaturge.content.infusion.InfusionRunicAugmentRecipe)
            h.getLevel()
                .getRecipeManager()
                .byKey(
                    ResourceLocation.fromNamespaceAndPath(
                        "thaumaturge", "runic_augment/runic_shielding"))
                .orElseThrow()
                .value();
    ItemStack shielded = augmentation.augmentedResult(new ItemStack(EquipmentModule.RING.get()));
    inv.setEquippedCurio("ring", 0, shielded);
    h.assertValueEqual(
        com.leclowndu93150.thaumaturge.content.equipment.runic.RunicShielding.worn(p),
        1,
        "actual host shielding recognizes augmented equipped addon ring");
    p.discard();
    end.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void mercatusHasActualHostPechValueAndPhysicalCollection(GameTestHelper h) {
    var item =
        new ItemStack(
            com.sanhiruzu.autoturgy.progression.ProgressionModule.INGREDIENTS
                .get("mercatus_amalgam")
                .get(),
            3);
    var type =
        BuiltInRegistries.ENTITY_TYPE.get(
            ResourceLocation.fromNamespaceAndPath("thaumaturge", "pech"));
    var pech = (com.leclowndu93150.thaumaturge.content.entity.EntityPech) type.create(h.getLevel());
    h.assertValueEqual(pech.getValue(item), 24, "actual Desiderium gives Pech24");
    h.assertTrue(pech.isPrizedItem(item), "actual Pech prizes amalgam");
    ItemStack left = pech.collect(item.copy());
    h.assertTrue(pech.isDomesticated(), "actual Pech consumes valued gift to domesticate");
    h.assertValueEqual(left.getCount(), 2, "one real gift consumed");
    left = pech.collect(left);
    int stored = pech.loot.stream().mapToInt(ItemStack::getCount).sum();
    h.assertValueEqual(stored + left.getCount(), 2, "domesticated hoard conserves remaining two");
    h.succeed();
  }

  static BlockEntityNode structure(GameTestHelper h, ProgressionGameTests.TestPlayer p) {
    h.setBlock(P, LogisticsGameTests.host("node"));
    BlockEntityNode node = h.getBlockEntity(P);
    var current = AspectList.EMPTY.add(Aspects.resolve(h.getLevel(), TTAspects.AER), 7);
    var base = AspectList.EMPTY.add(Aspects.resolve(h.getLevel(), TTAspects.AER), 41);
    node.applyNodeData(
        new NodeData(
            com.leclowndu93150.thaumaturge.api.nodes.NodeType.PURE,
            Optional.of(com.leclowndu93150.thaumaturge.api.nodes.NodeModifier.BRIGHT),
            current,
            base));
    CompoundTag original = node.saveWithoutMetadata(h.getLevel().registryAccess());
    original.put(
        "AspectsBaseOriginal",
        AspectList.CODEC
            .encodeStart(
                h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),
                base.add(Aspects.resolve(h.getLevel(), TTAspects.ORDO), 13))
            .getOrThrow());
    node.loadWithComponents(original, h.getLevel().registryAccess());
    for (BlockPos shell : NodeJarRitual.structurePositions(h.absolutePos(P))) {
      boolean lid = shell.getY() == h.absolutePos(P).getY() + 2;
      h.getLevel()
          .setBlockAndUpdate(
              shell,
              LogisticsGameTests.host(lid ? "log_silverwood" : "warded_glass").defaultBlockState());
      if (lid)
        h.assertTrue(WardHandler.ward(h.getLevel(), shell, p.getUUID()), "actual ward placed");
    }
    ResearchManager.complete(p, Content.id("advanced_jarring"));
    p.setItemInHand(InteractionHand.MAIN_HAND, ExperienceEquipmentGameTests.wand(h, 10000));
    return node;
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void actualWardedCaptureDropPlaceReleasePreservesAllIntrinsicFields(
      GameTestHelper h) {
    var p = ExperienceEquipmentGameTests.player(h);
    var node = structure(h, p);
    CompoundTag expected = NodeJarring.intrinsic(node);
    h.assertTrue(
        NodeJarring.capture(h.getLevel(), h.absolutePos(P), p, InteractionHand.MAIN_HAND),
        "actual warded structure captures");
    h.assertValueEqual(
        com.leclowndu93150.thaumaturge.api.wands.WandAccess.getVis(
            p.getMainHandItem(), TTAspects.AER),
        3000,
        "70 real Aer vis paid");
    BlockEntityJarNode jar = h.getBlockEntity(P);
    h.assertTrue(NodeJarring.advanced(jar), "actual jar marked for preserving lifecycle");
    h.assertTrue(
        expected.equals(NodeJarring.intrinsic(jar)), "capture preserves exact intrinsic data");
    h.getLevel().destroyBlock(h.absolutePos(P), true, p);
    ItemStack drop =
        h
            .getLevel()
            .getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(P)).inflate(1))
            .stream()
            .map(ItemEntity::getItem)
            .filter(s -> s.is(TTBlocks.JAR_NODE.get().asItem()))
            .findFirst()
            .orElseThrow()
            .copy();
    h.assertFalse(
        drop.get(DataComponents.BLOCK_ENTITY_DATA).copyTag().contains("Relay"),
        "sanitized data excludes spatial state");
    h.setBlock(P.below(), Blocks.STONE);
    p.setItemInHand(InteractionHand.MAIN_HAND, drop);
    ExperienceEquipmentGameTests.use(h, p, P.below());
    jar = h.getBlockEntity(P);
    h.assertTrue(NodeJarring.advanced(jar), "placed actual jar retains lifecycle marker");
    h.assertTrue(
        expected.equals(NodeJarring.intrinsic(jar)),
        "placed jar restores original/current/base/type/modifier");
    p.setItemInHand(InteractionHand.MAIN_HAND, ExperienceEquipmentGameTests.wand(h, 100));
    ExperienceEquipmentGameTests.use(h, p, P);
    BlockEntityNode released = h.getBlockEntity(P);
    h.assertFalse(released instanceof BlockEntityJarNode, "actual node released");
    h.assertTrue(
        expected.equals(NodeJarring.intrinsic(released)),
        "real release preserves complete intrinsic attributes");
    h.assertTrue(
        released.getNodeType() == com.leclowndu93150.thaumaturge.api.nodes.NodeType.PURE,
        "independent getter retains Pure type");
    h.assertTrue(
        released.getNodeModifier() == com.leclowndu93150.thaumaturge.api.nodes.NodeModifier.BRIGHT,
        "independent getter retains Bright modifier");
    var aer = Aspects.resolve(h.getLevel(), TTAspects.AER);
    var ordo = Aspects.resolve(h.getLevel(), TTAspects.ORDO);
    h.assertValueEqual(released.getAspects().amountOf(aer), 7, "independent current Aer7");
    h.assertValueEqual(released.getAspectsBase().amountOf(aer), 41, "independent base Aer41");
    h.assertValueEqual(
        released.getAspectsBaseOriginal().amountOf(aer), 41, "independent original Aer41");
    h.assertValueEqual(
        released.getAspectsBaseOriginal().amountOf(ordo), 13, "independent original Ordo13");
    h.assertFalse(released.isEnergized(), "independent nonenergized release");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void invalidUnpaidAndEnergizedCapturePreserveVisAndWorld(GameTestHelper h) {
    var p = ExperienceEquipmentGameTests.player(h);
    var node = structure(h, p);
    BlockPos lid = h.absolutePos(P).offset(1, 2, 1);
    WardHandler.unward(h.getLevel(), lid, p.getUUID());
    h.assertFalse(
        NodeJarring.capture(h.getLevel(), h.absolutePos(P), p, InteractionHand.MAIN_HAND),
        "unwarded lid refuses");
    WardHandler.ward(h.getLevel(), lid, p.getUUID());
    p.setItemInHand(InteractionHand.MAIN_HAND, ExperienceEquipmentGameTests.wand(h, 6999));
    h.assertFalse(
        NodeJarring.capture(h.getLevel(), h.absolutePos(P), p, InteractionHand.MAIN_HAND),
        "insufficient vis refuses");
    p.setItemInHand(InteractionHand.MAIN_HAND, ExperienceEquipmentGameTests.wand(h, 10000));
    node.setEnergized(true);
    h.assertFalse(
        NodeJarring.capture(h.getLevel(), h.absolutePos(P), p, InteractionHand.MAIN_HAND),
        "energized explicitly refuses");
    h.assertValueEqual(
        com.leclowndu93150.thaumaturge.api.wands.WandAccess.getVis(
            p.getMainHandItem(), TTAspects.AER),
        10000,
        "all refusals leave vis");
    h.assertTrue(h.getBlockEntity(P) == node, "no world mutation");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void loadedFishingInfusionPreservesNameWearAndRejectsExistingEnchantments(
      GameTestHelper h) {
    var retained =
        com.sanhiruzu.autoturgy.fluids.FluidModule.VISHROOM
            .bowl
            .get()
            .builtInRegistryHolder()
            .getData(AspectDataMaps.BASE_ASPECTS);
    h.assertValueEqual(
        retained.amountOf(Aspects.resolve(h.getLevel(), TTAspects.VICTUS)),
        5,
        "retained vishroom mapping coexists with Mercatus");

    var recipe =
        (com.leclowndu93150.thaumaturge.content.infusion.InfusionJobRecipe)
            h.getLevel().getRecipeManager().byKey(Content.id("fishing_luck")).orElseThrow().value();
    ItemStack rod = new ItemStack(Items.FISHING_ROD);
    rod.setDamageValue(11);
    rod.set(DataComponents.CUSTOM_NAME, Component.literal("Patient Angler"));
    var input =
        new com.leclowndu93150.thaumaturge.content.infusion.InfusionInput(
            rod,
            List.of(
                new ItemStack(Items.NAUTILUS_SHELL),
                new ItemStack(
                    com.sanhiruzu.autoturgy.progression.ProgressionModule.INGREDIENTS
                        .get("aqua_sliver")
                        .get()),
                new ItemStack(Items.LAPIS_LAZULI)));
    h.assertTrue(
        recipe.matches(input, h.getLevel()), "actual loaded infusion matches physical ingredients");
    ItemStack output = recipe.jobResult(input, h.getLevel().random, h.getLevel().registryAccess());
    h.assertValueEqual(output.getDamageValue(), 11, "wear retained");
    h.assertTrue(
        output.getHoverName().getString().equals("Patient Angler"), "custom name retained");
    var ench =
        h.getLevel()
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.LUCK_OF_THE_SEA);
    h.assertValueEqual(output.getEnchantmentLevel(ench), 3, "real Luck of the SeaIII");
    h.assertFalse(
        recipe.matches(
            new com.leclowndu93150.thaumaturge.content.infusion.InfusionInput(
                output, input.components()),
            h.getLevel()),
        "existing enchantments cannot be replaced");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void actualPoweredDispenserCollectsAndReturnsItsPhysicalMiser(GameTestHelper h) {
    h.setBlock(
        P, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
    net.minecraft.world.level.block.entity.DispenserBlockEntity dispenser = h.getBlockEntity(P);
    dispenser.setItem(0, new ItemStack(CollectingPearls.MISER.get()));
    Vec3 at = h.absolutePos(P.east(2)).getCenter();
    var diamond = new ItemEntity(h.getLevel(), at.x, at.y, at.z, new ItemStack(Items.DIAMOND, 3));
    diamond.setNoGravity(true);
    h.getLevel().addFreshEntity(diamond);
    for (int y = 0; y < 3; y++) h.setBlock(P.east(5).offset(0, y - 1, 0), Blocks.STONE);
    h.setBlock(P.below(), Blocks.REDSTONE_BLOCK);
    h.runAfterDelay(
        95,
        () -> {
          int pearls = 0, diamonds = 0;
          for (int i = 0; i < dispenser.getContainerSize(); i++) {
            ItemStack item = dispenser.getItem(i);
            if (item.is(CollectingPearls.MISER.get())) pearls += item.getCount();
            if (item.is(Items.DIAMOND)) diamonds += item.getCount();
          }
          h.assertValueEqual(pearls, 1, "real dispenser returns one launched physical pearl");
          h.assertValueEqual(diamonds, 3, "real dispenser owns actual collected diamonds");
          h.assertTrue(diamond.isRemoved(), "loose source consumed only after real insertion");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void loadedFocusInfusionUpgradePreservesStoredPhysicalContents(GameTestHelper h) {
    var recipe =
        (com.leclowndu93150.thaumaturge.content.infusion.InfusionJobRecipe)
            h.getLevel()
                .getRecipeManager()
                .byKey(Content.id("crafting_focus_transposing"))
                .orElseThrow()
                .value();
    ItemStack focus = new ItemStack(CraftingFocus.FOCUS.get());
    var real = NonNullList.withSize(15, ItemStack.EMPTY);
    real.set(2, new ItemStack(Items.DIAMOND, 6));
    focus.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(real));
    var input =
        new com.leclowndu93150.thaumaturge.content.infusion.InfusionInput(
            focus,
            List.of(
                new ItemStack(Items.ENDER_PEARL),
                new ItemStack(
                    com.sanhiruzu.autoturgy.progression.ProgressionModule.INGREDIENTS
                        .get("conflux_disc")
                        .get()),
                new ItemStack(Items.AMETHYST_SHARD)));
    h.assertTrue(recipe.matches(input, h.getLevel()), "real host upgrade infusion matches");
    var researcher = ExperienceEquipmentGameTests.player(h);
    ResearchManager.complete(researcher, Content.id("portable_crafting"));
    var match =
        com.leclowndu93150.thaumaturge.content.infusion.InfusionRecipeMatcher.find(
            h.getLevel(), researcher, input);
    h.assertTrue(
        match != null && match.recipe() instanceof FocusUpgradeRecipe,
        "actual host matcher discovers addon upgrade through normal infusion type");
    researcher.discard();
    ItemStack output = recipe.jobResult(input, h.getLevel().random, h.getLevel().registryAccess());
    h.assertTrue(
        CraftingFocus.upgrade(output, "Transposing")
            && CraftingFocus.upgrade(output, "Storage")
            && CraftingFocus.upgrade(output, "Gratis"),
        "bounded cumulative upgrade flags");
    h.assertValueEqual(
        output.get(DataComponents.CONTAINER).getStackInSlot(2).getCount(),
        6,
        "host catalyst patch retains six physical diamonds");
    var lesser =
        (com.leclowndu93150.thaumaturge.content.infusion.InfusionJobRecipe)
            h.getLevel()
                .getRecipeManager()
                .byKey(Content.id("crafting_focus_gratis"))
                .orElseThrow()
                .value();
    var oldInput =
        new com.leclowndu93150.thaumaturge.content.infusion.InfusionInput(
            output,
            List.of(
                new ItemStack(
                    com.sanhiruzu.autoturgy.progression.ProgressionModule.INGREDIENTS
                        .get("ordo_crystalline_brain")
                        .get()),
                new ItemStack(
                    com.sanhiruzu.autoturgy.progression.ProgressionModule.INGREDIENTS
                        .get("conflux_disc")
                        .get()),
                new ItemStack(Items.AMETHYST_SHARD)));
    h.assertFalse(
        lesser.matches(oldInput, h.getLevel()),
        "lesser upgrade refuses already complete focus instead of downgrading");
    h.assertValueEqual(
        output.get(DataComponents.CONTAINER).getStackInSlot(2).getCount(),
        6,
        "rejected downgrade leaves physical contents untouched");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void pearlWithUnloadedMachineOriginReturnsAtCurrentLoadedPosition(
      GameTestHelper h) {
    BlockPos far = h.absolutePos(P).east(40960);
    h.assertFalse(h.getLevel().hasChunkAt(far), "origin chunk genuinely absent");
    var pearl = new CollectingPearls.Pearl(CollectingPearls.TYPE.get(), h.getLevel());
    pearl.machine = far;
    pearl.setItem(new ItemStack(CollectingPearls.MISER.get()));
    pearl.setPos(h.absolutePos(P).getCenter());
    h.getLevel().addFreshEntity(pearl);
    pearl.finish();
    h.assertFalse(h.getLevel().hasChunkAt(far), "return never loads missing machine chunk");
    h.assertValueEqual(
        h
            .getLevel()
            .getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(P)).inflate(1))
            .stream()
            .filter(e -> e.getItem().is(CollectingPearls.MISER.get()))
            .mapToInt(e -> e.getItem().getCount())
            .sum(),
        1,
        "single physical return remains in current loaded chunk");
    h.succeed();
  }
}
