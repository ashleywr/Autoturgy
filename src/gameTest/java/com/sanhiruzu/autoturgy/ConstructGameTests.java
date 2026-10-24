package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.aura.*;
import com.leclowndu93150.thaumaturge.api.essentia.*;
import com.leclowndu93150.thaumaturge.api.nodes.NodeType;
import com.leclowndu93150.thaumaturge.api.wands.WandAccess;
import com.leclowndu93150.thaumaturge.content.aura.node.*;
import com.leclowndu93150.thaumaturge.content.aura.relay.BlockEntityVisRelay;
import com.leclowndu93150.thaumaturge.content.entity.WispEntity;
import com.leclowndu93150.thaumaturge.content.equipment.hover.ThaumostaticHarnessItem;
import com.leclowndu93150.thaumaturge.content.research.ResearchManager;
import com.leclowndu93150.thaumaturge.content.wands.*;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.constructs.*;
import com.sanhiruzu.autoturgy.constructs.NethermindModule.*;
import com.sanhiruzu.autoturgy.essentia.EssentiaModule;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class ConstructGameTests {
  @GameTest(template = "constructs", timeoutTicks = 40)
  public static void canonicalClientItemsRoundTripActualCommandParserAndEquippedHarness(
      GameTestHelper h) throws Exception {
    var s = spire(h);
    var p = owner(h, s);
    List<String> commands = new ArrayList<>();
    var wand = p.getMainHandItem();
    String wandSpec =
        new net.minecraft.commands.arguments.item.ItemInput(
                wand.getItemHolder(), wand.getComponentsPatch())
            .serialize(h.getLevel().registryAccess());
    commands.add("give @s " + wandSpec + " 1");
    var parsed =
        new net.minecraft.commands.arguments.item.ItemParser(h.getLevel().registryAccess())
            .parse(new com.mojang.brigadier.StringReader(wandSpec));
    ItemStack parsedWand =
        new net.minecraft.commands.arguments.item.ItemInput(parsed.item(), parsed.components())
            .createItemStack(1, true);
    h.assertValueEqual(
        WandAccess.getVis(parsedWand, TTAspects.IGNIS),
        WandAccess.getVis(wand, TTAspects.IGNIS),
        "canonical command preserves actual charged wand");
    for (var aspect :
        List.of(
            TTAspects.POTENTIA,
            TTAspects.AER,
            TTAspects.VOLATUS,
            TTAspects.IGNIS,
            TTAspects.TERRA,
            TTAspects.VITREUS,
            TTAspects.LUX,
            TTAspects.SPIRITUS,
            TTAspects.BESTIA,
            TTAspects.EXANIMIS,
            TTAspects.AURAM,
            TTAspects.ALIENIS,
            TTAspects.DESIDERIUM)) {
      ItemStack jar = new ItemStack(item("jar_normal"));
      jar =
          jar.getCapability(EssentiaCapabilities.ITEM_STORAGE)
              .insert(Aspects.resolve(h.getLevel(), aspect), 64)
              .resultingStack();
      String spec =
          new net.minecraft.commands.arguments.item.ItemInput(
                  jar.getItemHolder(), jar.getComponentsPatch())
              .serialize(h.getLevel().registryAccess());
      var value =
          new net.minecraft.commands.arguments.item.ItemParser(h.getLevel().registryAccess())
              .parse(new com.mojang.brigadier.StringReader(spec));
      var roundTrip =
          new net.minecraft.commands.arguments.item.ItemInput(value.item(), value.components())
              .createItemStack(1, true);
      h.assertValueEqual(
          roundTrip
              .getCapability(EssentiaCapabilities.ITEM_STORAGE)
              .contents()
              .amountOf(Aspects.resolve(h.getLevel(), aspect)),
          64,
          "actual filled jar command parsed: " + aspect.location());
      commands.add("give @s " + spec + " 1");
    }
    ItemStack harness = new ItemStack(item("thaumostatic_harness"));
    ItemStack seed = new ItemStack(item("jar_normal"));
    seed =
        seed.getCapability(EssentiaCapabilities.ITEM_STORAGE)
            .insert(Aspects.resolve(h.getLevel(), TTAspects.POTENTIA), 1)
            .resultingStack();
    ThaumostaticHarnessItem.setJar(harness, seed);
    String harnessSpec =
        new net.minecraft.commands.arguments.item.ItemInput(
                harness.getItemHolder(), harness.getComponentsPatch())
            .serialize(h.getLevel().registryAccess());
    String equip = "item replace entity @s armor.chest with " + harnessSpec + " 1";
    commands.add(equip);
    h.getLevel()
        .getServer()
        .getCommands()
        .performPrefixedCommand(p.createCommandSourceStack().withPermission(4), equip);
    h.assertValueEqual(
        ((ThaumostaticHarnessItem) p.getItemBySlot(EquipmentSlot.CHEST).getItem())
            .getHoverFuel(p.getItemBySlot(EquipmentSlot.CHEST)),
        1,
        "actual item-replace command equips real harness with one Potentia");
    var path =
        java.nio.file.Path.of(System.getProperty("autoturgy.testReport"))
            .getParent()
            .resolve("client-items.mcfunction");
    java.nio.file.Files.write(path, commands, java.nio.charset.StandardCharsets.UTF_8);
    p.discard();
    h.succeed();
  }

  static final BlockPos U = new BlockPos(8, 17, 8), L = U.below(8), S = new BlockPos(8, 5, 8);

  static Item item(String name) {
    return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("thaumaturge", name));
  }

  static ProgressionGameTests.TestPlayer owner(GameTestHelper h, ConstructEntity c) {
    var p = new ProgressionGameTests.TestPlayer(h.getLevel());
    p.setGameMode(GameType.SURVIVAL);
    p.moveTo(c.getBlockPos().getCenter());
    for (String research : List.of("spires", "nethermind", "dimensional_lures", "sirens"))
      ResearchManager.complete(p, Content.id(research));
    var parts = WandParts.starter();
    ItemStack wand = ItemWand.create(item("wand"), parts.cap(), parts.rod(), false);
    for (var a : TTAspects.PRIMALS) wand = WandAccess.withVis(wand, a, parts.maxCentivis());
    p.setItemInHand(InteractionHand.MAIN_HAND, wand);
    return p;
  }

  static BlockEntityVisRelay relay(GameTestHelper h, BlockPos controller) {
    BlockPos r = controller.west(3).above(2);
    h.setBlock(r.above(), EssentiaModule.host("node"));
    h.setBlock(r, EssentiaModule.host("vis_relay"));
    BlockEntityNode node = h.getBlockEntity(r.above());
    AspectList list = AspectList.EMPTY;
    for (var a : TTAspects.PRIMALS) list = list.add(Aspects.resolve(h.getLevel(), a), 100);
    node.applyNodeData(new NodeData(NodeType.NORMAL, Optional.empty(), list, list));
    node.setEnergized(true);
    node.serverTick(h.getLevel(), node.getBlockPos());
    BlockEntityVisRelay relay = h.getBlockEntity(r);
    relay.refreshLink(h.getLevel());
    return relay;
  }

  static void feed(GameTestHelper h, ConstructEntity c, ResourceKey<IAspect> aspect, int amount) {
    var p = owner(h, c);
    int selected = c.fuels().indexOf(aspect) + 1;
    h.assertTrue(selected > 0, "fuel is allowed by real controller");
    c.settings[Direction.NORTH.ordinal()] = selected;
    ItemStack jar = new ItemStack(item("jar_normal"));
    var initial = jar.getCapability(EssentiaCapabilities.ITEM_STORAGE);
    h.assertTrue(initial != null, "actual host item-jar capability exists");
    jar = initial.insert(Aspects.resolve(h.getLevel(), aspect), amount).resultingStack();
    p.setItemInHand(InteractionHand.MAIN_HAND, jar);
    int before = c.amount(aspect);
    for (int i = 0; i < 8 && c.amount(aspect) - before < amount; i++)
      c.jar(p, InteractionHand.MAIN_HAND, Direction.NORTH);
    h.assertValueEqual(
        c.amount(aspect) - before, amount, "actual returned item jar funds controller exactly");
    h.assertValueEqual(
        p.getMainHandItem()
            .getCapability(EssentiaCapabilities.ITEM_STORAGE)
            .contents()
            .totalAmount(),
        0,
        "source item jar actually emptied");
    p.discard();
  }

  static void start(GameTestHelper h, ConstructEntity c) {
    var p = owner(h, c);
    h.assertTrue(c.start(p, InteractionHand.MAIN_HAND), "real researched wand start: " + c.failure);
    p.discard();
  }

  static SpireModule.SpireEntity spire(GameTestHelper h) {
    h.setBlock(S.below(2), Blocks.CHISELED_QUARTZ_BLOCK);
    h.setBlock(S.below(), SpireModule.PYLON.get());
    h.setBlock(S, SpireModule.CONTROLLER.get());
    h.setBlock(S.above(), SpireModule.PYLON.get());
    h.setBlock(S.above(2), EssentiaModule.host("metal_thaumium"));
    var abs = h.absolutePos(S);
    var cap = abs.above(2);
    System.out.println(
        "AUTOTURGY_SPIRE column="
            + h.getBlockState(S.below(2))
            + ","
            + h.getBlockState(S.below())
            + ","
            + h.getBlockState(S)
            + ","
            + h.getBlockState(S.above())
            + ","
            + h.getBlockState(S.above(2))
            + " air="
            + h.getLevel().isEmptyBlock(cap.above())
            + " sky="
            + h.getLevel().canSeeSky(cap.above())
            + " capY="
            + cap.getY()
            + " height="
            + h.getLevel()
                .getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    cap.getX(),
                    cap.getZ()));
    return h.getBlockEntity(S);
  }

  static NethermindEntity platform(GameTestHelper h, Rune rune, int count) {
    h.setBlock(U, Blocks.AIR);
    h.setBlock(U, NethermindModule.CONTROLLER.get());
    int placed = 0;
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++)
        if (x != 0 || z != 0)
          h.setBlock(
              U.offset(x, 0, z),
              placed++ < count
                  ? NethermindModule.RUNES.get(rune).get()
                  : NethermindModule.OBSIDIAN.get());
    return h.getBlockEntity(U);
  }

  static DimensionalLure.LureEntity lower(GameTestHelper h) {
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++)
        if (Math.max(Math.abs(x), Math.abs(z)) == 2)
          h.setBlock(L.offset(x, 0, z), NethermindModule.OBSIDIAN.get());
    h.setBlock(L, Blocks.AIR);
    h.setBlock(L, DimensionalLure.BLOCK.get());
    return h.getBlockEntity(L);
  }

  @GameTest(template = "constructs", timeoutTicks = 40)
  public static void spireRequiresRealStructureSkyPylonBoundsAndPaidResearchWand(GameTestHelper h) {
    var s = spire(h);
    var p = owner(h, s);
    h.assertTrue(
        SpireModule.structure(h.getLevel(), s.getBlockPos()).valid(),
        "actual quartz-pylon-controller-thaumium column");
    s.activate();
    new ControlMenu(7, p.getInventory(), s).clickMenuButton(p, 80);
    h.assertFalse(s.active, "empty-hand and menu activation cannot bypass wand");
    int before = WandAccess.getVis(p.getMainHandItem(), TTAspects.AER);
    h.setBlock(S.above(4), Blocks.STONE);
    h.assertFalse(s.start(p, InteractionHand.MAIN_HAND), "blocked sky refuses start");
    h.assertValueEqual(
        WandAccess.getVis(p.getMainHandItem(), TTAspects.AER),
        before,
        "failed start costs nothing");
    h.setBlock(S.above(4), Blocks.AIR);
    ResearchManager.of(p).forget(Content.id("spires"));
    h.assertFalse(
        s.start(p, InteractionHand.MAIN_HAND), "actual missing host research refuses activation");
    ResearchManager.complete(p, Content.id("spires"));
    var hit =
        new BlockHitResult(s.getBlockPos().getCenter(), Direction.NORTH, s.getBlockPos(), false);
    p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    h.assertTrue(s.active, "ordinary wand/block game-mode dispatch starts actual construct");
    h.assertValueEqual(
        WandAccess.getVis(p.getMainHandItem(), TTAspects.AER),
        before - 100,
        "exact actual activation vis");
    s.cancel();
    h.setBlock(S.above(), Blocks.AIR);
    h.assertFalse(s.start(p, InteractionHand.MAIN_HAND), "one-pylon gap invalid");
    for (int i = 1; i <= 6; i++) h.setBlock(S.above(i), SpireModule.PYLON.get());
    h.setBlock(S.above(7), EssentiaModule.host("metal_thaumium"));
    h.assertFalse(
        SpireModule.structure(h.getLevel(), s.getBlockPos()).valid(), "seven pylons exceed cap");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "constructs", timeoutTicks = 40)
  public static void realHarnessReturnedJarAndRelayConserveExactPotentia(GameTestHelper h) {
    var s = spire(h);
    var p = owner(h, s);
    ItemStack harness = new ItemStack(item("thaumostatic_harness"));
    h.assertTrue(
        harness.getItem() instanceof ThaumostaticHarnessItem, "installed actual harness item");
    ThaumostaticHarnessItem.setJar(harness, new ItemStack(item("jar_normal")));
    p.setItemSlot(EquipmentSlot.CHEST, harness);
    feed(h, s, TTAspects.POTENTIA, 12);
    feed(h, s, TTAspects.AER, 2);
    feed(h, s, TTAspects.VOLATUS, 2);
    start(h, s);
    h.assertValueEqual(s.charge(p), 0, "no actual relay means no free charge");
    h.assertValueEqual(s.amount(TTAspects.POTENTIA), 12, "unpaid refusal preserves fed source");
    var r = relay(h, S);
    int before = r.resolveSource(h.getLevel()).source().availableCentivis(TTAspects.AER);
    p.moveTo(p.getX(), p.getY() + 12, p.getZ());
    h.assertValueEqual(s.charge(p), 4, "two pylons and real Volatus double four-unit batch");
    int after = r.resolveSource(h.getLevel()).source().availableCentivis(TTAspects.AER);
    h.assertValueEqual(before - after, 4, "exact actual relay Aer consumed");
    h.assertValueEqual(s.amount(TTAspects.POTENTIA), 8, "controller debits same four real units");
    var returned = ThaumostaticHarnessItem.getJar(p.getItemBySlot(EquipmentSlot.CHEST));
    h.assertValueEqual(
        returned
            .getCapability(EssentiaCapabilities.ITEM_STORAGE)
            .contents()
            .amountOf(Aspects.resolve(h.getLevel(), TTAspects.POTENTIA)),
        4,
        "returned updated jar installed in actual equipped harness");
    h.assertValueEqual(s.amount(TTAspects.AER), 1, "optional radius cost once");
    h.assertValueEqual(s.amount(TTAspects.VOLATUS), 1, "optional rate cost once");
    p.moveTo(p.getX() + 50, p.getY(), p.getZ());
    h.assertValueEqual(
        s.charge(p), 0, "outside horizontal cylinder refuses despite unlimited vertical axis");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "constructs", timeoutTicks = 45)
  public static void fullWrongHarnessAndClosedBracedPortsPreserveRealSources(GameTestHelper h) {
    var s = spire(h);
    var p = owner(h, s);
    feed(h, s, TTAspects.POTENTIA, 8);
    relay(h, S);
    start(h, s);
    h.assertValueEqual(s.charge(p), 0, "no equipped harness refuses");
    ItemStack harness = new ItemStack(item("thaumostatic_harness"));
    var full = new ItemStack(item("jar_normal"));
    var storage = full.getCapability(EssentiaCapabilities.ITEM_STORAGE);
    full =
        storage
            .insert(
                Aspects.resolve(h.getLevel(), TTAspects.POTENTIA),
                storage.capacity(Aspects.resolve(h.getLevel(), TTAspects.POTENTIA)))
            .resultingStack();
    ThaumostaticHarnessItem.setJar(harness, full);
    p.setItemSlot(EquipmentSlot.CHEST, harness);
    h.assertValueEqual(s.charge(p), 0, "actual full harness jar refuses");
    h.assertValueEqual(
        s.amount(TTAspects.POTENTIA), 8, "no loss on unsuitable or full actual item");
    // Separate actual jar output uses its real upward port, including its brace permission.
    BlockPos q = new BlockPos(3, 10, 3);
    h.setBlock(q, SpireModule.CONTROLLER.get());
    SpireModule.SpireEntity pipe = h.getBlockEntity(q);
    h.setBlock(q.below(), EssentiaModule.host("jar_normal"));
    com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar source =
        h.getBlockEntity(q.below());
    source.setStorageContents(TTAspects.POTENTIA, 3);
    source.setBraced(true);
    h.runAfterDelay(
        10,
        () -> {
          h.assertValueEqual(source.amount(), 3, "braced output remains physical");
          h.assertValueEqual(pipe.amount(TTAspects.POTENTIA), 0, "braced jar cannot feed");
          source.setBraced(false);
        });
    h.runAfterDelay(
        30,
        () -> {
          h.assertValueEqual(
              source.amount() + pipe.amount(TTAspects.POTENTIA),
              3,
              "actual block-jar to controller conservation");
          h.assertTrue(
              pipe.amount(TTAspects.POTENTIA) > 0,
              "real released jar feeds selected bottom host port");
          p.discard();
          h.succeed();
        });
  }

  @GameTest(template = "constructs", timeoutTicks = 40)
  public static void controllerDropAndReloadPreserveOnlyPhysicalFuelOnce(GameTestHelper h) {
    var s = spire(h);
    feed(h, s, TTAspects.POTENTIA, 17);
    start(h, s);
    CompoundTag saved = s.saveWithFullMetadata(h.getLevel().registryAccess());
    var reload = new SpireModule.SpireEntity(s.getBlockPos(), s.getBlockState());
    reload.setLevel(h.getLevel());
    reload.loadWithComponents(saved, h.getLevel().registryAccess());
    h.assertValueEqual(
        reload.amount(TTAspects.POTENTIA), 17, "genuine host-codec save retains owned fuel");
    var drops = Block.getDrops(s.getBlockState(), h.getLevel(), s.getBlockPos(), s);
    h.assertValueEqual(drops.size(), 1, "one controller drop");
    var tag =
        drops
            .getFirst()
            .get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
            .copyTag();
    h.assertFalse(tag.getBoolean("active"), "dropped controller starts inactive");
    var returned = new SpireModule.SpireEntity(s.getBlockPos(), s.getBlockState());
    returned.setLevel(h.getLevel());
    returned.loadWithComponents(tag, h.getLevel().registryAccess());
    h.assertValueEqual(
        returned.amount(TTAspects.POTENTIA), 17, "only controller item owns preserved17");
    var closed = s.port(Direction.UP);
    s.settings[Direction.UP.ordinal()] = 0;
    h.assertValueEqual(
        closed.insert(Aspects.resolve(h.getLevel(), TTAspects.POTENTIA), 1, false),
        0,
        "closed side cannot insert");
    h.succeed();
  }

  @GameTest(template = "constructs", timeoutTicks = 40)
  public static void fourTotalRunesRestrictWeightsAndCelerityCostsCap(GameTestHelper h) {
    var n = platform(h, Rune.CRYSTAL, 4);
    var s = NethermindModule.structure(h.getLevel(), n.getBlockPos());
    h.assertTrue(
        NethermindModule.weighted(s).stream().allMatch(m -> m.rune() == Rune.CRYSTAL),
        "four identical TOTAL runes restrict to only quartz");
    var m = NethermindModule.materials().get(2);
    h.assertValueEqual(
        NethermindModule.cost(s, m).get(TTAspects.VITREUS),
        3,
        "duplicate runes reduce cost only once");
    var one = new Structure(true, Map.of(Rune.CRYSTAL, 1));
    h.assertValueEqual(
        NethermindModule.cost(one, m).get(TTAspects.VITREUS), 3, "one matching rune same discount");
    var many = new Structure(true, Map.of(Rune.CELERITY, 24, Rune.CRYSTAL, 4));
    h.assertValueEqual(many.celerity(), 12, "speed capped12");
    h.assertValueEqual(many.interval(), 20, "one second minimum");
    h.assertValueEqual(many.multiplier(), 5, "only first four multiply consumption");
    h.setBlock(U.offset(2, 0, 2), NethermindModule.RUNES.get(Rune.WISP).get());
    h.assertFalse(
        NethermindModule.structure(h.getLevel(), n.getBlockPos()).valid(),
        "creature and block runes never mix");
    h.succeed();
  }

  @GameTest(template = "constructs", timeoutTicks = 40)
  public static void actualNetherBlockOnlyLegalAndPaidWithExactCosts(GameTestHelper h) {
    var n = platform(h, Rune.PEBBLE, 4);
    feed(h, n, TTAspects.IGNIS, 8);
    feed(h, n, TTAspects.TERRA, 8);
    start(h, n);
    h.assertFalse(n.operate(), "no actual Ignis relay refuses block effect");
    h.assertValueEqual(n.operations, 0, "unpaid generates nothing");
    h.assertValueEqual(n.amount(TTAspects.TERRA), 8, "unpaid conserves real essentia");
    var r = relay(h, U);
    int before = r.resolveSource(h.getLevel()).source().availableCentivis(TTAspects.IGNIS);
    h.assertTrue(n.operate(), "actual paid block placed outside Nether");
    int found = 0;
    for (BlockPos p : BlockPos.betweenClosed(U.offset(-4, -10, -4), U.offset(4, -2, 4)))
      if (h.getBlockState(p).is(Blocks.GRAVEL)) found++;
    h.assertValueEqual(found, 1, "actual useful modern Pebble gravel only");
    h.assertValueEqual(n.amount(TTAspects.TERRA), 7, "matching material cost1");
    h.assertValueEqual(n.amount(TTAspects.IGNIS), 7, "continuing Ignis essentia1");
    h.assertValueEqual(
        before - r.resolveSource(h.getLevel()).source().availableCentivis(TTAspects.IGNIS),
        5,
        "actual relay paid5centivis");
    for (BlockPos p : BlockPos.betweenClosed(U.offset(-4, -10, -4), U.offset(4, -2, 4)))
      h.setBlock(p, Blocks.STONE);
    h.assertFalse(n.operate(), "full legal volume refuses");
    h.assertValueEqual(n.amount(TTAspects.TERRA), 7, "full area preserves cost");
    h.setBlock(U.below(3), Blocks.BARREL);
    h.assertFalse(
        NethermindModule.legal(h.getLevel(), h.absolutePos(U.below(3))),
        "real container never overwritten");
    BlockPos far = new BlockPos(20_000_000, 80, 20_000_000);
    h.assertFalse(NethermindModule.legal(h.getLevel(), far), "unloaded target refused");
    h.assertFalse(h.getLevel().hasChunkAt(far), "no target chunk loaded");
    h.succeed();
  }

  @GameTest(template = "constructs", timeoutTicks = 40)
  public static void lowerMustStartFirstAndBothEndsPayActualHostWisp(GameTestHelper h) {
    var n = platform(h, Rune.WISP, 4);
    var low = lower(h);
    feed(h, n, TTAspects.IGNIS, 10);
    feed(h, n, TTAspects.AURAM, 10);
    feed(h, low, TTAspects.ALIENIS, 10);
    feed(h, low, TTAspects.POTENTIA, 10);
    var p = owner(h, n);
    int before = WandAccess.getVis(p.getMainHandItem(), TTAspects.IGNIS);
    h.assertFalse(n.start(p, InteractionHand.MAIN_HAND), "upper refuses inactive lower");
    h.assertValueEqual(
        WandAccess.getVis(p.getMainHandItem(), TTAspects.IGNIS),
        before,
        "failed upper start leaves wand intact");
    start(h, low);
    start(h, n);
    var topRelay = relay(h, U);
    var bottomRelay = relay(h, L);
    int a = topRelay.resolveSource(h.getLevel()).source().availableCentivis(TTAspects.IGNIS),
        b = bottomRelay.resolveSource(h.getLevel()).source().availableCentivis(TTAspects.PERDITIO);
    h.assertTrue(n.operate(), "genuine two-ended paid spawn");
    var wisps =
        h.getLevel().getEntitiesOfClass(WispEntity.class, new AABB(low.getBlockPos()).inflate(5));
    h.assertValueEqual(wisps.size(), 1, "actual installed host WispEntity spawned");
    h.assertTrue(
        wisps.getFirst().aspect().is(TTAspects.IGNIS), "actual host wisp explicitly Ignis");
    h.assertValueEqual(n.amount(TTAspects.IGNIS), 8, "upper Ignis2");
    h.assertValueEqual(n.amount(TTAspects.AURAM), 7, "upper Auram3");
    h.assertValueEqual(low.amount(TTAspects.ALIENIS), 8, "lower Alienis2");
    h.assertValueEqual(low.amount(TTAspects.POTENTIA), 9, "lower Potentia1");
    h.assertValueEqual(
        a - topRelay.resolveSource(h.getLevel()).source().availableCentivis(TTAspects.IGNIS),
        5,
        "upper actual relay5");
    h.assertValueEqual(
        b - bottomRelay.resolveSource(h.getLevel()).source().availableCentivis(TTAspects.PERDITIO),
        5,
        "lower actual relay5");
    p.discard();
    h.succeed();
  }

  @GameTest(template = "constructs", timeoutTicks = 40)
  public static void everyRepresentedCreatureUsesActualVanillaOrHostEntity(GameTestHelper h) {
    for (var c : DimensionalLure.creatures()) {
      var n = platform(h, c.rune(), 4);
      var low = lower(h);
      feed(h, n, TTAspects.IGNIS, 16);
      if (c.aspect() != TTAspects.IGNIS) feed(h, n, c.aspect(), 12);
      feed(h, low, TTAspects.ALIENIS, 12);
      feed(h, low, TTAspects.POTENTIA, 12);
      relay(h, U);
      relay(h, L);
      start(h, low);
      start(h, n);
      h.assertTrue(
          n.operate(), "actual represented creature produced: " + c.rune() + " " + n.failure);
      var mobs = h.getLevel().getEntitiesOfClass(Mob.class, new AABB(low.getBlockPos()).inflate(8));
      h.assertTrue(
          mobs.stream().anyMatch(m -> m.getType() == c.type()),
          "genuine registered mob type: " + c.type());
      for (var mob : mobs) {
        h.assertTrue(
            DimensionalLure.space(h.getLevel(), mob.getBoundingBox()),
            "entity fits dry loaded block space");
        mob.discard();
      }
      n.cancel();
      low.cancel();
    }
    h.succeed();
  }

  @GameTest(template = "constructs", timeoutTicks = 40)
  public static void wrongDistanceSolidLineCollisionAndMissingLowerVisKeepCosts(GameTestHelper h) {
    var n = platform(h, Rune.GHAST, 4);
    var low = lower(h);
    feed(h, n, TTAspects.IGNIS, 8);
    feed(h, n, TTAspects.VOLATUS, 8);
    feed(h, low, TTAspects.ALIENIS, 8);
    feed(h, low, TTAspects.POTENTIA, 8);
    start(h, low);
    start(h, n);
    relay(h, U);
    h.assertFalse(n.operate(), "missing lower relay refuses");
    h.assertValueEqual(n.amount(TTAspects.IGNIS), 8, "upper transaction and essence unchanged");
    h.assertValueEqual(low.amount(TTAspects.ALIENIS), 8, "lower unchanged");
    relay(h, L);
    h.setBlock(L.above(2), Blocks.STONE);
    h.assertFalse(n.operate(), "solid between endpoints refuses");
    h.setBlock(L.above(2), Blocks.AIR);
    h.setBlock(L.above().east(), Blocks.WATER);
    h.assertFalse(n.operate(), "actual ghast collision/liquid volume refuses");
    h.assertValueEqual(n.amount(TTAspects.IGNIS), 8, "failed spawns keep costs");
    h.setBlock(L.above().east(), Blocks.AIR);
    h.assertTrue(
        DimensionalLure.lower(n, NethermindModule.structure(h.getLevel(), n.getBlockPos())) == low,
        "eight-block ghast pair valid");
    h.setBlock(L, Blocks.AIR);
    h.setBlock(U.below(6), DimensionalLure.BLOCK.get());
    h.assertTrue(
        DimensionalLure.lower(n, NethermindModule.structure(h.getLevel(), n.getBlockPos())) == null,
        "six-block ghast distance refuses");
    h.succeed();
  }

  @GameTest(template = "constructs", timeoutTicks = 40)
  public static void totemsNitorMitigateInstabilityAndThresholdStopsBoth(GameTestHelper h) {
    var n = platform(h, Rune.BLAZE, 4);
    var low = lower(h);
    for (int x : new int[] {-3, 3})
      for (int z : new int[] {-3, 3}) {
        for (int y = 0; y < 4; y++)
          h.setBlock(L.offset(x, y, z), EssentiaModule.host("obsidian_totem"));
        h.setBlock(L.offset(x, 4, z), EssentiaModule.host("nitor_orange"));
      }
    h.assertValueEqual(
        DimensionalLure.stability(h.getLevel(), low.getBlockPos(), n.getBlockPos()),
        28,
        "actual equal four-high pillars and four nitor");
    h.setBlock(L.offset(3, 3, 3), Blocks.AIR);
    h.assertTrue(
        DimensionalLure.stability(h.getLevel(), low.getBlockPos(), n.getBlockPos()) < 28,
        "unequal missing totem loses real stability");
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++)
        if ((x != 0 || z != 0)
            && !(h.getBlockState(U.offset(x, 0, z))
                .is(NethermindModule.RUNES.get(Rune.BLAZE).get())))
          h.setBlock(U.offset(x, 0, z), NethermindModule.RUNES.get(Rune.CELERITY).get());
    for (int x : new int[] {-3, 3})
      for (int z : new int[] {-3, 3})
        for (int y = 0; y < 5; y++) h.setBlock(L.offset(x, y, z), Blocks.AIR);
    feed(h, n, TTAspects.IGNIS, 64);
    feed(h, low, TTAspects.ALIENIS, 64);
    feed(h, low, TTAspects.POTENTIA, 64);
    relay(h, U);
    relay(h, L);
    start(h, low);
    start(h, n);
    low.instability = 79;
    h.assertTrue(n.operate(), "last operation pays before finite safety stop");
    h.assertFalse(n.active || low.active, "both ends stopped at instability threshold");
    h.assertTrue(low.instability <= 100, "bounded instability");
    var mobs = h.getLevel().getEntitiesOfClass(Mob.class, new AABB(low.getBlockPos()).inflate(6));
    h.assertTrue(
        mobs.size() >= 1 && mobs.size() <= 2,
        "at most one optional extra blaze per paid operation");
    h.succeed();
  }

  @GameTest(template = "constructs", timeoutTicks = 40)
  public static void validationNeverFinalizesAndCancelledActualSpawnCostsNothing(GameTestHelper h) {
    var n = platform(h, Rune.WISP, 4);
    var low = lower(h);
    feed(h, n, TTAspects.IGNIS, 8);
    feed(h, n, TTAspects.AURAM, 8);
    feed(h, low, TTAspects.ALIENIS, 8);
    feed(h, low, TTAspects.POTENTIA, 8);
    var a = relay(h, U);
    var b = relay(h, L);
    start(h, low);
    int[] finalizations = {0};
    java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent>
        reject =
            e -> {
              if (e.getEntity() instanceof WispEntity
                  && e.getEntity().distanceToSqr(low.getBlockPos().getCenter()) < 25) {
                finalizations[0]++;
                e.setSpawnCancelled(true);
              }
            };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(reject);
    try {
      start(h, n);
      h.assertTrue(n.validStart(), "legal validation succeeds with a pure candidate");
      h.assertValueEqual(finalizations[0], 0, "validation performs no world spawn initialization");
      int va = a.resolveSource(h.getLevel()).source().availableCentivis(TTAspects.IGNIS),
          vb = b.resolveSource(h.getLevel()).source().availableCentivis(TTAspects.PERDITIO);
      h.assertFalse(n.operate(), "ordinary NeoForge cancelled finalization refuses real effect");
      h.assertValueEqual(finalizations[0], 1, "selected actual spawn initialized exactly once");
      h.assertValueEqual(
          n.amount(TTAspects.IGNIS) + low.amount(TTAspects.ALIENIS),
          16,
          "both actual owned sources unchanged");
      h.assertValueEqual(
          a.resolveSource(h.getLevel()).source().availableCentivis(TTAspects.IGNIS),
          va,
          "upper actual relay transaction rolled back");
      h.assertValueEqual(
          b.resolveSource(h.getLevel()).source().availableCentivis(TTAspects.PERDITIO),
          vb,
          "lower actual relay transaction rolled back");
      h.assertTrue(
          h.getLevel()
              .getEntitiesOfClass(Mob.class, new AABB(low.getBlockPos()).inflate(5))
              .isEmpty(),
          "rejected candidate creates no stray real entity");
    } finally {
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(reject);
    }
    h.succeed();
  }
}
