package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCrystalAccess;
import com.leclowndu93150.thaumaturge.api.wands.WandAccess;
import com.leclowndu93150.thaumaturge.content.essentia.jar.*;
import com.leclowndu93150.thaumaturge.content.research.ResearchManager;
import com.leclowndu93150.thaumaturge.content.wands.*;
import com.sanhiruzu.autoturgy.equipment.*;
import com.sanhiruzu.autoturgy.experience.*;
import com.sanhiruzu.autoturgy.experience.ExperienceModule.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class ExperienceEquipmentGameTests {
  static final BlockPos POS = new BlockPos(4, 3, 4);

  static XpEntity xp(GameTestHelper h, Kind kind, BlockPos pos) {
    h.setBlock(pos, ExperienceModule.BLOCKS.get(kind).get());
    return h.getBlockEntity(pos);
  }

  static ProgressionGameTests.TestPlayer player(GameTestHelper h) {
    var p = new ProgressionGameTests.TestPlayer(h.getLevel());
    p.setGameMode(GameType.SURVIVAL);
    p.moveTo(h.absolutePos(POS).getCenter());
    return p;
  }

  static Item hostItem(String name) {
    return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("thaumaturge", name));
  }

  static ItemStack wand(GameTestHelper h, int vis) {
    var parts = WandParts.starter();
    ItemStack w =
        ItemWand.create(
            hostItem("wand"),
            parts.cap(),
            vis > parts.maxCentivis()
                ? h
                    .getLevel()
                    .registryAccess()
                    .registryOrThrow(com.leclowndu93150.thaumaturge.api.wands.WandRod.REGISTRY_KEY)
                    .stream()
                    .filter(r -> !r.staff() && r.capacity() * 100 >= vis)
                    .min(
                        java.util.Comparator.comparingInt(
                            com.leclowndu93150.thaumaturge.api.wands.WandRod::capacity))
                    .orElseThrow()
                : parts.rod(),
            false);
    for (var a : TTAspects.PRIMALS) w = WandAccess.withVis(w, a, vis);
    h.assertTrue(
        ((ItemWand) w.getItem()).getParts(w).maxCentivis() >= vis,
        "charged wand uses actual obtainable sufficient-capacity registered parts");
    return w;
  }

  static void use(GameTestHelper h, net.minecraft.server.level.ServerPlayer p, BlockPos pos) {
    p.gameMode.useItemOn(
        p,
        h.getLevel(),
        p.getMainHandItem(),
        InteractionHand.MAIN_HAND,
        new BlockHitResult(
            h.absolutePos(pos).getCenter(), Direction.UP, h.absolutePos(pos), false));
  }

  static long orbs(GameTestHelper h, BlockPos at) {
    return h
        .getLevel()
        .getEntitiesOfClass(ExperienceOrb.class, new AABB(h.absolutePos(at)).inflate(2))
        .stream()
        .mapToLong(ExperienceOrb::getValue)
        .sum();
  }

  static int count(net.minecraft.world.entity.player.Player p, Item i) {
    int n = 0;
    for (ItemStack s : p.getInventory().items) if (s.is(i)) n += s.getCount();
    return n;
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void playerLevelDrainPreservesFractionAndIgnoresLifetimeCounter(GameTestHelper h) {
    XpEntity x = xp(h, Kind.AMNESIAC, POS);
    var p = player(h);
    ExperienceModule.setPoints(p, ExperiencePoints.atLevel(32) + 17);
    p.totalExperience = 0;
    x.click(p);
    h.assertValueEqual(
        ExperienceModule.points(p),
        ExperiencePoints.atLevel(31) + 15,
        "fractional percentage retained with integer floor");
    h.assertValueEqual(x.xp, 123, "one actual level with scaled fraction transferred");
    p.setShiftKeyDown(true);
    x.click(p);
    h.assertValueEqual(
        ExperienceModule.points(p) + x.xp,
        ExperiencePoints.atLevel(32) + 17,
        "five-level conservation");
    x.xp = x.capacity() - 3;
    long before = ExperienceModule.points(p);
    x.click(p);
    h.assertValueEqual(
        before - ExperienceModule.points(p), 3L, "capacity takes only accepted points");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void hugePlayerBalanceRepeatedOnePointTransfersRemainExact(GameTestHelper h) {
    var p = player(h);
    long total = ExperiencePoints.atLevel(100000000) + 666666L;
    ExperienceModule.setPoints(p, total);
    for (int i = 0; i < 1000; i++)
      h.assertValueEqual(ExperienceModule.withdraw(p, 1), 1, "one physical point");
    h.assertValueEqual(
        ExperienceModule.points(p),
        total - 1000,
        "float progress cannot round-trip manufacture points");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void brainAndAssemblyComparatorsHaveSeparateFiniteBalances(GameTestHelper h) {
    XpEntity a = xp(h, Kind.AMNESIAC, POS);
    XpEntity s = xp(h, Kind.SOUL, POS.east());
    h.setBlock(POS.above(), LogisticsGameTests.host("jar_brain"));
    BlockEntityJarBrain b = h.getBlockEntity(POS.above());
    b.setXp(100);
    s.add(1000);
    a.add(200);
    h.assertTrue(a.comparator() > 0, "assembly comparator counts physical balances");
    h.assertValueEqual(s.comparator(), 2, "soul comparator only own capacity");
    int before = b.xp() + s.xp + a.xp;
    s.transfer(false);
    h.assertValueEqual(b.xp() + s.xp + a.xp, before, "brain exchange conserved with solid floor");
    var p = player(h);
    p.setShiftKeyDown(true);
    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    use(h, p, POS.above());
    h.assertValueEqual(b.xp(), 0, "sneak brain releases complete balance");
    h.assertTrue(orbs(h, POS.above()) > 0, "real XP orbs emitted");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void thrownExperienceBottleIsInterceptedOnce(GameTestHelper h) {
    XpEntity x = xp(h, Kind.AMNESIAC, POS);
    h.setBlock(POS.west(), LogisticsGameTests.host("jar_brain"));
    var bottle = new ThrownExperienceBottle(EntityType.EXPERIENCE_BOTTLE, h.getLevel());
    bottle.setPos(h.absolutePos(POS).getCenter().add(0, 1.8, 0));
    bottle.setDeltaMovement(0, -.5, 0);
    h.getLevel().addFreshEntity(bottle);
    h.runAfterDelay(
        5,
        () -> {
          BlockEntityJarBrain brain = h.getBlockEntity(POS.west());
          h.assertValueEqual(
              x.xp + brain.xp() + orbs(h, POS),
              9L,
              "one fixed bottle reward without vanilla impact");
          h.assertTrue(bottle.isRemoved(), "projectile consumed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void concentratePhialsBottleAndBreakPlacementConserve(GameTestHelper h) {
    XpEntity jar = xp(h, Kind.CONCENTRATE, POS);
    jar.add(1000);
    var p = player(h);
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ExperienceModule.PHIAL.get()));
    use(h, p, POS);
    h.assertValueEqual(jar.xp, 900, "real phial withdrew100");
    h.assertTrue(
        p.getMainHandItem().is(ExperienceModule.FILLED_PHIAL.get()), "filled physical phial");
    use(h, p, POS);
    h.assertValueEqual(jar.xp, 1000, "same phial refills");
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
    use(h, p, POS);
    h.assertValueEqual(jar.xp, 980, "glass unstable conversion costs20");
    h.assertTrue(
        p.getMainHandItem().is(Items.EXPERIENCE_BOTTLE), "actual ordinary enchantment bottle");
    h.getLevel().destroyBlock(h.absolutePos(POS), true, p);
    var drops =
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(POS)).inflate(1));
    ItemStack drop =
        drops.stream()
            .map(ItemEntity::getItem)
            .filter(s -> s.is(ExperienceModule.BLOCKS.get(Kind.CONCENTRATE).get().asItem()))
            .findFirst()
            .orElseThrow()
            .copy();
    h.assertValueEqual(
        drop.get(DataComponents.BLOCK_ENTITY_DATA).copyTag().getInt("xp"),
        980,
        "physical jar balance persisted");

    var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    var encodedDrop = ItemStack.CODEC.encodeStart(ops, drop).getOrThrow();
    drop = ItemStack.CODEC.parse(ops, encodedDrop).getOrThrow();
    h.assertValueEqual(
        drop.get(DataComponents.BLOCK_ENTITY_DATA).copyTag().getInt("xp"),
        980,
        "actual item save codec retains sole physical jar balance before placement");
    h.setBlock(POS.below(), Blocks.STONE);
    p.setItemInHand(InteractionHand.MAIN_HAND, drop);
    use(h, p, POS.below());
    XpEntity placed = h.getBlockEntity(POS);
    h.assertValueEqual(placed.xp, 980, "physical placed jar restored");
    h.assertValueEqual(orbs(h, POS), 0L, "no preserved jar plus orbs");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void soulCondensesOnlyIntoEmptyUnlabelledRealWardedJar(GameTestHelper h) {
    XpEntity s = xp(h, Kind.SOUL, POS);
    s.add(100);
    h.setBlock(POS.below(), LogisticsGameTests.host("jar_normal"));
    s.transfer(true);
    XpEntity jar = h.getBlockEntity(POS.below());
    h.assertValueEqual(jar.xp, 10, "real warded jar converted");
    h.assertValueEqual(s.xp, 90, "paid owned points");
    var saved = s.saveWithFullMetadata(h.getLevel().registryAccess());
    s.add(-90);
    s.loadWithComponents(saved, h.getLevel().registryAccess());
    h.assertValueEqual(s.xp, 90, "reload keeps balance");
    h.getLevel().destroyBlock(h.absolutePos(POS), true);
    h.assertValueEqual(orbs(h, POS), 45L, "single half remainder break");
    h.assertValueEqual(jar.xp, 10, "separate jar retained");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void blockedAndFullConcentrateNeverDiscardsXp(GameTestHelper h) {
    XpEntity s = xp(h, Kind.SOUL, POS);
    XpEntity jar = xp(h, Kind.CONCENTRATE, POS.below());
    s.add(100);
    jar.add(jar.capacity());
    s.transfer(true);
    h.assertValueEqual(s.xp, 100, "full jar stalls");
    h.setBlock(POS.below(), Blocks.STONE);
    s.transfer(true);
    h.assertValueEqual(s.xp, 100, "solid stops drip");
    h.setBlock(POS.below(), Blocks.AIR);
    h.setBlock(POS.below(2), Blocks.AIR);
    s.transfer(true);
    h.assertValueEqual(s.xp, 99, "documented finite air loss");
    h.succeed();
  }

  static CraftingFocus.FocusMenu focus(
      GameTestHelper h, ProgressionGameTests.TestPlayer p, boolean storage) {
    ItemStack w = wand(h, 2500), f = new ItemStack(CraftingFocus.FOCUS.get());
    CompoundTag flags = new CompoundTag();
    flags.putBoolean("Storage", storage);
    flags.putBoolean("Gratis", storage);
    f.set(DataComponents.CUSTOM_DATA, CustomData.of(flags));
    ((ItemWand) w.getItem()).socketFocus(w, f);
    p.getInventory().selected = 0;
    p.getInventory().setItem(0, w);
    ResearchManager.complete(p, Content.id("portable_crafting"));
    h.assertTrue(CraftingFocus.open(p, 0), "real focus opens");
    return (CraftingFocus.FocusMenu) p.containerMenu;
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void actualPortableOrdinaryCraftUsesRealInputAndReturnsRemainders(
      GameTestHelper h) {
    var p = player(h);
    var menu = focus(h, p, false);
    menu.inputs.setItem(0, new ItemStack(Items.OAK_LOG, 2));
    h.assertTrue(menu.craft(), "ordinary registered recipe crafts");
    h.assertValueEqual(menu.inputs.getItem(0).getCount(), 1, "one actual log consumed");
    h.assertValueEqual(count(p, Items.OAK_PLANKS), 4, "four physical planks");
    h.assertValueEqual(
        WandAccess.getVis(p.getMainHandItem(), TTAspects.ORDO),
        2475,
        "opening paid once; mundane no extra fee");
    menu.removed(p);
    h.assertValueEqual(count(p, Items.OAK_LOG), 1, "close returns actual leftover");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void actualPortableArcaneHostTransactionGatesResearchAndPays(GameTestHelper h) {
    var p = player(h);
    var menu = focus(h, p, false);
    menu.inputs.setItem(
        0, EssentiaCrystalAccess.create(Aspects.resolve(h.getLevel(), TTAspects.AER), 1));
    for (int i = 0; i < 6; i++)
      menu.inputs.setItem(
          9 + i,
          EssentiaCrystalAccess.create(Aspects.resolve(h.getLevel(), TTAspects.PRIMALS.get(i)), 4));
    menu.status.set(0, 1);
    h.assertFalse(menu.craft(), "actual recipe research locked");
    h.assertValueEqual(menu.inputs.getItem(0).getCount(), 1, "denial preserves ingredient");
    ResearchManager.complete(p, Content.id("foundations"));
    com.leclowndu93150.thaumaturge.api.aura.AuraHelper.addVis(h.getLevel(), p.blockPosition(), 100);
    float auraBefore =
        com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getVis(h.getLevel(), p.blockPosition());
    h.assertTrue(menu.craft(), "real arcane transaction succeeds: " + menu.failure);
    h.assertTrue(menu.inputs.getItem(0).isEmpty(), "host paid recipe ingredient");
    h.assertTrue(
        com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getVis(h.getLevel(), p.blockPosition())
            < auraBefore,
        "generic recipe cost debits actual local aura");
    h.assertValueEqual(
        count(
            p,
            com.sanhiruzu.autoturgy.progression.ProgressionModule.INGREDIENTS
                .get("aer_sliver")
                .get()),
        4,
        "actual registered output");
    h.assertTrue(
        WandAccess.getVis(p.getMainHandItem(), TTAspects.AER) < 2500, "normal recipe vis paid");
    h.assertTrue(
        WandAccess.getVis(p.getMainHandItem(), TTAspects.ORDO) <= 2450,
        "opening plus extra arcane fee paid");
    h.assertFalse(menu.craft(), "no repeat free output");
    menu.removed(p);
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void storageFocusRetainsOnlyPhysicalLeftoversAndInvalidWandReturns(
      GameTestHelper h) {
    var p = player(h);
    var menu = focus(h, p, true);
    menu.inputs.setItem(3, new ItemStack(Items.DIAMOND, 5));
    menu.removed(p);
    ItemStack wand = p.getMainHandItem();
    ItemStack f = ((ItemWand) wand.getItem()).socketedFocus(wand);
    h.assertValueEqual(
        f.get(DataComponents.CONTAINER).getStackInSlot(3).getCount(), 5, "stored physical inputs");
    p.containerMenu = p.inventoryMenu;
    h.assertTrue(CraftingFocus.open(p, 0), "reopens stored focus");
    var next = (CraftingFocus.FocusMenu) p.containerMenu;
    h.assertValueEqual(next.inputs.getItem(3).getCount(), 5, "restores once");
    p.getInventory().setItem(0, ItemStack.EMPTY);
    h.assertFalse(next.craft(), "changed wand refuses craft");
    next.removed(p);
    h.assertValueEqual(count(p, Items.DIAMOND), 5, "invalid wand closure returns actual inputs");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void extremeInvalidPlayerStatesRefuseWithoutCreatingXp(GameTestHelper h) {
    var p = player(h);
    p.experienceLevel = Integer.MAX_VALUE;
    p.experienceProgress = .5F;
    h.assertValueEqual(ExperienceModule.points(p), 0L, "unsupported level is safely refused");
    h.assertValueEqual(ExperienceModule.withdraw(p, 10), 0, "no free withdrawal");
    h.assertValueEqual(
        p.experienceLevel, Integer.MAX_VALUE, "refusal does not rewrite player state");
    p.experienceLevel = 32;
    p.experienceProgress = Float.NaN;
    h.assertValueEqual(ExperienceModule.withdraw(p, 100), 0, "NaN progress refused");
    p.experienceProgress = -.1F;
    h.assertValueEqual(ExperienceModule.withdraw(p, 100), 0, "negative progress refused");
    ExperienceModule.setPoints(p, Long.MAX_VALUE);
    h.assertTrue(
        Float.isFinite(p.experienceProgress) && p.experienceProgress < 1,
        "max reconstructed progress canonical");
    h.assertTrue(
        p.getXpNeededForNextLevel() > 0, "canonical maximum retains positive native XP width");
    h.assertValueEqual(
        ExperienceModule.points(p),
        ExperienceModule.MAX_PLAYER_POINTS,
        "explicit maximum saturates documented support range");
    h.assertValueEqual(ExperienceModule.withdraw(p, -100), 0, "negative withdrawal refused");
    h.assertValueEqual(ExperienceModule.withdraw(p, 1), 1, "supported maximum one-point debit");
    h.assertValueEqual(
        ExperienceModule.points(p),
        ExperienceModule.MAX_PLAYER_POINTS - 1,
        "no rounding manufacture");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 50)
  public static void realPlayerStandingOnSolidStoneDrainsThroughNativeMovement(GameTestHelper h) {
    XpEntity x = xp(h, Kind.AMNESIAC, POS);
    var p = player(h);
    h.getLevel().addNewPlayer(p);
    ExperienceModule.setPoints(p, 200);
    p.setPos(
        h.absolutePos(POS).getX() + .5,
        h.absolutePos(POS).getY() + 1,
        h.absolutePos(POS).getZ() + .5);
    p.setDeltaMovement(Vec3.ZERO);
    h.setBlock(POS.west(), LogisticsGameTests.host("jar_brain"));
    h.onEachTick(
        () -> {
          if (p.isAlive()) p.travel(Vec3.ZERO);
        });
    h.runAfterDelay(
        25,
        () -> {
          BlockEntityJarBrain b = h.getBlockEntity(POS.west());
          h.assertTrue(
              ExperienceModule.points(p) < 200,
              "actual grounded player lost XP without fixture entityInside calls");
          h.assertTrue(
              x.xp + b.xp() + orbs(h, POS) > 0, "drained XP exists in actual physical balances");
          p.discard();
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void physicalPhialDrinkingReturnsContainerAndExactXp(GameTestHelper h) {
    var p = player(h);
    ExperienceModule.setPoints(p, ExperiencePoints.atLevel(17) + 13);
    ItemStack phial = new ItemStack(ExperienceModule.FILLED_PHIAL.get());
    ItemStack empty = phial.finishUsingItem(h.getLevel(), p);
    h.assertTrue(empty.is(ExperienceModule.PHIAL.get()), "physical phial returned");
    h.assertValueEqual(
        ExperienceModule.points(p),
        ExperiencePoints.atLevel(17) + 113,
        "exact100 points from physical dose");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void portableCraftFullInventoryDropsRealCakeAndAllBucketRemainders(
      GameTestHelper h) {
    var p = player(h);
    var menu = focus(h, p, true);
    for (int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    Item[] grid = {
      Items.MILK_BUCKET,
      Items.MILK_BUCKET,
      Items.MILK_BUCKET,
      Items.SUGAR,
      Items.EGG,
      Items.SUGAR,
      Items.WHEAT,
      Items.WHEAT,
      Items.WHEAT
    };
    for (int i = 0; i < 9; i++) menu.inputs.setItem(i, new ItemStack(grid[i]));
    h.assertTrue(menu.craft(), "registered cake recipe succeeds with physical inputs");
    var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(2));
    h.assertValueEqual(
        drops.stream()
            .filter(e -> e.getItem().is(Items.BUCKET))
            .mapToInt(e -> e.getItem().getCount())
            .sum(),
        3,
        "all three milk remainders safe");
    h.assertValueEqual(
        drops.stream()
            .filter(e -> e.getItem().is(Items.CAKE))
            .mapToInt(e -> e.getItem().getCount())
            .sum(),
        1,
        "unaccepted real output safely drops");
    menu.removed(p);
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void actualStaffAllowsOrdinaryButRefusesArcaneWithoutPayment(GameTestHelper h) {
    var p = player(h);
    ItemStack w = wand(h, 2500);
    var rod =
        h
            .getLevel()
            .registryAccess()
            .registryOrThrow(com.leclowndu93150.thaumaturge.api.wands.WandRod.REGISTRY_KEY)
            .stream()
            .filter(com.leclowndu93150.thaumaturge.api.wands.WandRod::staff)
            .findFirst()
            .orElseThrow();
    w.set(
        com.leclowndu93150.thaumaturge.registry.TTDataComponents.WAND_PARTS.get(),
        new WandParts(WandParts.starter().cap(), rod, false));
    ((ItemWand) w.getItem()).socketFocus(w, new ItemStack(CraftingFocus.FOCUS.get()));
    p.getInventory().selected = 0;
    p.getInventory().setItem(0, w);
    ResearchManager.complete(p, Content.id("portable_crafting"));
    h.assertTrue(CraftingFocus.open(p, 0), "actual staff focus opens");
    var menu = (CraftingFocus.FocusMenu) p.containerMenu;
    menu.inputs.setItem(0, new ItemStack(Items.OAK_LOG));
    h.assertTrue(menu.craft(), "staff ordinary recipe works");
    menu.status.set(0, 1);
    menu.inputs.setItem(
        0, EssentiaCrystalAccess.create(Aspects.resolve(h.getLevel(), TTAspects.AER), 1));
    int before = WandAccess.getVis(p.getMainHandItem(), TTAspects.AER);
    h.assertFalse(menu.craft(), "staff arcane denied");
    h.assertValueEqual(
        WandAccess.getVis(p.getMainHandItem(), TTAspects.AER), before, "staff denial no vis loss");
    menu.removed(p);
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void actualFocusPouchTransposesWholeStoredFocusAndPreservesPrevious(
      GameTestHelper h) {
    var p = player(h);
    ItemStack w = wand(h, 2500),
        prior = new ItemStack(hostItem("focus")),
        f = new ItemStack(CraftingFocus.FOCUS.get());
    CompoundTag flags = new CompoundTag();
    flags.putBoolean("Transposing", true);
    flags.putBoolean("Storage", true);
    flags.putBoolean("Gratis", true);
    f.set(DataComponents.CUSTOM_DATA, CustomData.of(flags));
    var stored = NonNullList.withSize(15, ItemStack.EMPTY);
    stored.set(0, new ItemStack(Items.DIAMOND, 3));
    f.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stored));
    ((ItemWand) w.getItem()).socketFocus(w, prior);
    p.getInventory().selected = 0;
    p.getInventory().setItem(0, w);
    ItemStack pouch = new ItemStack(hostItem("focus_pouch"));
    var contents =
        com.leclowndu93150.thaumaturge.content.casters.FocusPouchItem.getInventory(pouch);
    contents.set(0, f);
    com.leclowndu93150.thaumaturge.content.casters.FocusPouchItem.setInventory(pouch, contents);
    p.getInventory().setItem(1, pouch);
    ResearchManager.complete(p, Content.id("portable_crafting"));
    CraftingFocus.call(p);
    h.assertTrue(
        p.containerMenu instanceof CraftingFocus.FocusMenu,
        "real pouch focus installed and opened");
    var menu = (CraftingFocus.FocusMenu) p.containerMenu;
    h.assertValueEqual(
        menu.inputs.getItem(0).getCount(), 3, "stored real diamonds move to menu once");
    h.assertTrue(
        ItemStack.matches(
            com.leclowndu93150.thaumaturge.content.casters.FocusPouchItem.getInventory(
                    p.getInventory().getItem(1))
                .get(0),
            prior),
        "previous physical focus returned to same pouch slot");
    menu.removed(p);
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void portableAuraIsRealScopedAndRollsBackLateIngredientRefusal(GameTestHelper h) {
    var p = player(h);
    var menu = focus(h, p, true);
    ResearchManager.complete(p, Content.id("foundations"));
    menu.status.set(0, 1);
    menu.inputs.setItem(
        0, EssentiaCrystalAccess.create(Aspects.resolve(h.getLevel(), TTAspects.AER), 1));
    for (int i = 0; i < 6; i++)
      menu.inputs.setItem(
          9 + i,
          EssentiaCrystalAccess.create(Aspects.resolve(h.getLevel(), TTAspects.PRIMALS.get(i)), 4));
    var pos = p.blockPosition();
    com.leclowndu93150.thaumaturge.api.aura.AuraHelper.drainVis(
        h.getLevel(), pos, Float.MAX_VALUE, false);
    h.assertFalse(menu.craft(), "no generic aura creates no output");
    h.assertValueEqual(menu.inputs.getItem(0).getCount(), 1, "low aura preserves ingredient");
    com.leclowndu93150.thaumaturge.api.aura.AuraHelper.addVis(h.getLevel(), pos, 100);
    float before = com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getVis(h.getLevel(), pos);
    int wandBefore = WandAccess.getVis(p.getMainHandItem(), TTAspects.AER);
    java.util.function.Consumer<com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCostEvent>
        race =
            e -> {
              if (e.getPlayer() == p) {
                p.addItem(menu.inputs.removeItemNoUpdate(0));
              }
            };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(race);
    try {
      h.assertFalse(menu.craft(), "late physical-grid change refuses host commit");
    } finally {
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(race);
    }
    h.assertValueEqual(
        com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getVis(h.getLevel(), pos),
        before,
        "late refusal rolls back real aura");
    h.assertValueEqual(
        WandAccess.getVis(p.getMainHandItem(), TTAspects.AER),
        wandBefore,
        "late refusal rolls back wand");
    List<ItemStack> all = new ArrayList<>();
    for (int i = 0; i < 15; i++) all.add(menu.inputs.getItem(i));
    all.add(p.getMainHandItem());
    var input =
        com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneCraftingInput.of(3, 3, all)
            .withPlayer(p);
    try (var tx = com.leclowndu93150.thaumaturge.api.transaction.Transaction.openRoot()) {
      h.assertValueEqual(
          CraftingFocus.supplyAura(
              com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext.virtual(
                  p, UUID.randomUUID(), p.getUUID()),
              p,
              input,
              1,
              tx),
          0,
          "unrelated virtual context cannot use source");
      tx.commit();
    }
    h.assertValueEqual(
        com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getVis(h.getLevel(), pos),
        before,
        "unrelated context leaves aura");
    menu.removed(p);
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void canonicalPhase4cCommandsAreGeneratedAndParsedByActualCodecs(GameTestHelper h)
      throws Exception {
    var p = player(h);
    ItemStack w = wand(h, 10000), f = new ItemStack(CraftingFocus.FOCUS.get());
    CompoundTag flags = new CompoundTag();
    flags.putBoolean("Gratis", true);
    flags.putBoolean("Storage", true);
    flags.putBoolean("Transposing", true);
    f.set(DataComponents.CUSTOM_DATA, CustomData.of(flags));
    ((ItemWand) w.getItem()).socketFocus(w, f);
    List<ItemStack> items =
        new ArrayList<>(
            List.of(
                w,
                wand(h, 10000),
                new ItemStack(EquipmentModule.RING.get()),
                new ItemStack(CollectingPearls.MISER.get()),
                new ItemStack(ExperienceModule.FILLED_PHIAL.get())));
    for (var aspect : TTAspects.PRIMALS)
      items.add(EssentiaCrystalAccess.create(Aspects.resolve(h.getLevel(), aspect), 16));
    ItemStack jar = new ItemStack(ExperienceModule.BLOCKS.get(Kind.CONCENTRATE).get());
    CompoundTag jarData = new CompoundTag();
    jarData.putInt("xp", 1000);
    jarData.putString("id", "autoturgy:experience");
    jar.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(jarData));
    items.add(jar);
    List<String> commands = new ArrayList<>();
    for (ItemStack stack : items) {
      String spec =
          new net.minecraft.commands.arguments.item.ItemInput(
                  stack.getItemHolder(), stack.getComponentsPatch())
              .serialize(h.getLevel().registryAccess());
      var parsed =
          new net.minecraft.commands.arguments.item.ItemParser(h.getLevel().registryAccess())
              .parse(new com.mojang.brigadier.StringReader(spec));
      ItemStack round =
          new net.minecraft.commands.arguments.item.ItemInput(parsed.item(), parsed.components())
              .createItemStack(stack.getCount(), true);
      if (stack.getItem() instanceof ItemWand caster) {
        h.assertTrue(
            ItemStack.matches(caster.socketedFocus(stack), caster.socketedFocus(round)),
            "canonical socketed focus preserves actual contents");
        for (var a : TTAspects.PRIMALS)
          h.assertValueEqual(
              WandAccess.getVis(round, a),
              WandAccess.getVis(stack, a),
              "canonical charged primal value");
      } else
        h.assertTrue(
            ItemStack.matches(stack, round),
            "actual item component command parsed without guessing: "
                + spec
                + " original="
                + stack.getComponentsPatch()
                + " parsed="
                + round.getComponentsPatch());
      commands.add("give @s " + spec + " " + stack.getCount());
    }
    var directory = java.nio.file.Path.of(System.getProperty("autoturgy.testReport")).getParent();
    java.nio.file.Files.createDirectories(directory);
    java.nio.file.Files.write(directory.resolve("client-equipment.mcfunction"), commands);
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void actualOpenItemGratePermitsDripAndClosedGrateBlocks(GameTestHelper h) {
    XpEntity stone = xp(h, Kind.SOUL, POS), jar = xp(h, Kind.CONCENTRATE, POS.below(2));
    stone.add(100);
    var grate = LogisticsGameTests.host("item_grate").defaultBlockState();
    h.setBlock(
        POS.below(),
        grate.setValue(com.leclowndu93150.thaumaturge.content.device.BlockItemGrate.OPEN, false));
    stone.transfer(true);
    h.assertValueEqual(stone.xp, 100, "closed real grate blocks all drip");
    h.setBlock(
        POS.below(),
        grate.setValue(com.leclowndu93150.thaumaturge.content.device.BlockItemGrate.OPEN, true));
    stone.transfer(true);
    h.assertValueEqual(stone.xp, 90, "open real grate allows owned points through");
    h.assertValueEqual(jar.xp, 10, "actual lower physical jar receives points");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void physicalFocusDeathClosureDropsOnlyRealStoredInputs(GameTestHelper h) {
    var p = player(h);
    var menu = focus(h, p, true);
    menu.inputs.setItem(0, new ItemStack(Items.DIAMOND, 4));
    p.setHealth(0);
    menu.removed(p);
    menu.removed(p);
    h.assertValueEqual(
        h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(2)).stream()
            .filter(e -> e.getItem().is(Items.DIAMOND))
            .mapToInt(e -> e.getItem().getCount())
            .sum(),
        4,
        "death closes storage focus exactly once");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void levelClicksActuallyRemoveOneOrFiveAtAlmostFullCurveBoundaries(
      GameTestHelper h) {
    var p = player(h);
    XpEntity stone = xp(h, Kind.AMNESIAC, POS);
    for (int level : new int[] {16, 17, 31, 32})
      for (int count : new int[] {1, 5}) {
        long total = ExperiencePoints.atLevel(level) + ExperiencePoints.next(level) - 1;
        ExperienceModule.setPoints(p, total);
        stone.xp = 0;
        p.setShiftKeyDown(count == 5);
        stone.click(p);
        h.assertValueEqual(
            p.experienceLevel, level - count, "actual level drop at almost-full boundary");
        h.assertValueEqual(
            ExperienceModule.points(p) + stone.xp,
            total,
            "every fractional adjustment remains conserved");
      }
    p.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void explosionReleasesSingleIntendedStoneRemainderAndNoFilledDrop(
      GameTestHelper h) {
    XpEntity stone = xp(h, Kind.SOUL, POS);
    stone.add(200);
    Vec3 at = h.absolutePos(POS).getCenter();
    h.getLevel().explode(null, at.x, at.y, at.z, 4, Level.ExplosionInteraction.TNT);
    h.assertFalse(
        h.getLevel().getBlockEntity(h.absolutePos(POS)) instanceof XpEntity,
        "actual explosion removed stone");
    h.assertValueEqual(orbs(h, POS), 100L, "actual explosion emits intended half once");
    for (var e :
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(POS)).inflate(4)))
      if (e.getItem().is(ExperienceModule.BLOCKS.get(Kind.SOUL).get().asItem()))
        h.assertFalse(
            e.getItem().has(DataComponents.BLOCK_ENTITY_DATA),
            "stone item never duplicates preserved balance");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void portablePreviewDisplaysAndPaysNormalCrystalAndAuraRequirements(
      GameTestHelper h) {
    var p = player(h);
    var menu = focus(h, p, false);
    ResearchManager.complete(p, Content.id("foundations"));
    p.getInventory().setItem(0, WandAccess.withVis(p.getMainHandItem(), TTAspects.AER, 0));
    menu.inputs.setItem(
        0, EssentiaCrystalAccess.create(Aspects.resolve(h.getLevel(), TTAspects.AER), 1));
    for (int i = 0; i < 6; i++)
      menu.inputs.setItem(
          9 + i,
          EssentiaCrystalAccess.create(Aspects.resolve(h.getLevel(), TTAspects.PRIMALS.get(i)), 4));
    com.leclowndu93150.thaumaturge.api.aura.AuraHelper.addVis(h.getLevel(), p.blockPosition(), 100);
    menu.status.set(0, 1);
    menu.broadcastChanges();
    int aer = TTAspects.PRIMALS.indexOf(TTAspects.AER);
    h.assertValueEqual(
        menu.status.get(9 + aer),
        1,
        "preview shows one real Aer crystal rather than hiding requirement");
    h.assertTrue(menu.status.get(8) > 0, "preview shows ordinary generic aura cost");
    h.assertValueEqual(
        menu.status.get(2 + TTAspects.PRIMALS.indexOf(TTAspects.ORDO)),
        25,
        "preview includes extra Ordo operation cost");
    h.assertTrue(menu.craft(), "physical crystal fallback and actual local aura craft succeeds");
    h.assertValueEqual(menu.inputs.getItem(9 + aer).getCount(), 3, "one actual crystal consumed");
    h.assertValueEqual(
        WandAccess.getVis(p.getMainHandItem(), TTAspects.AER), 0, "no invented Aer wand vis");
    menu.removed(p);
    p.discard();
    h.succeed();
  }
}
