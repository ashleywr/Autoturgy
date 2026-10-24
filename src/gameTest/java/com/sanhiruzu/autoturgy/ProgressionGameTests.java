package com.sanhiruzu.autoturgy;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.essentia.*;
import com.leclowndu93150.thaumaturge.api.recipe.*;
import com.leclowndu93150.thaumaturge.api.research.*;
import com.leclowndu93150.thaumaturge.api.wands.WandAccess;
import com.leclowndu93150.thaumaturge.content.research.ResearchManager;
import com.leclowndu93150.thaumaturge.content.wands.*;
import com.leclowndu93150.thaumaturge.content.workbench.*;
import com.sanhiruzu.autoturgy.inventarium.*;
import com.sanhiruzu.autoturgy.progression.ProgressionModule;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Autoturgy.ID)
@PrefixGameTestTemplate(false)
public final class ProgressionGameTests {
  // Headless player transport only. Production menus, research and crafting remain the real host.
  static final class TestPlayer extends net.neoforged.neoforge.common.util.FakePlayer {
    TestPlayer(net.minecraft.server.level.ServerLevel level) {
      super(
          level,
          new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "AutoturgyPhase3"));
    }

    @Override
    public java.util.OptionalInt openMenu(
        net.minecraft.world.MenuProvider provider,
        java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extra) {
      containerMenu = provider.createMenu(7, getInventory(), this);
      return java.util.OptionalInt.of(7);
    }
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void realHostResearchGraphAndMagicalRecipesLoaded(GameTestHelper h) {
    var registry = h.getLevel().registryAccess().registryOrThrow(IResearchEntry.REGISTRY_KEY);
    int count = 0;
    for (var entry : registry.entrySet())
      if (entry.getKey().location().getNamespace().equals("autoturgy")) {
        count++;
        h.assertValueEqual(entry.getValue().stages().size(), 2, "research has real unlock stages");
        for (var parent : entry.getValue().parents())
          h.assertTrue(registry.containsKey(parent.id()), "all parent references resolve");
      }
    h.assertValueEqual(count, 25, "all original research entries loaded");
    for (String name :
        new String[] {
          "redcrystal",
          "magic_hourglass",
          "crystalline_eye",
          "runic_filter",
          "inventarium_core",
          "melding_mirror",
          "thirsty_tank",
          "alchemical_boiler",
          "essentia_locus",
          "essentia_aggregator",
          "aer_sliver",
          "aer_crystal_reassembly",
          "aer_crystalline_brain"
        }) {
      var recipe = h.getLevel().getRecipeManager().byKey(Content.id(name)).orElseThrow();
      h.assertFalse(
          recipe.value().getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING,
          "magical device has no mundane bypass: " + name);
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void realArcaneWorkbenchRefusesLockedAndChargesActualWand(GameTestHelper h) {
    BlockPos p = new BlockPos(4, 3, 4);
    h.setBlock(p, LogisticsGameTests.host("arcane_workbench"));
    BlockEntityArcaneWorkbench table = h.getBlockEntity(p);
    var player = new TestPlayer(h.getLevel());
    player.setGameMode(GameType.SURVIVAL);
    player.moveTo(h.absolutePos(p).getCenter());
    var inv = table.getInventory();
    var aer = Aspects.resolve(h.getLevel(), TTAspects.AER);
    inv.setItem(0, EssentiaCrystalAccess.create(aer, 1));
    var parts = WandParts.starter();
    Item wandItem =
        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("thaumaturge", "wand"));
    ItemStack wand = ItemWand.create(wandItem, parts.cap(), parts.rod(), false);
    for (var primal : TTAspects.PRIMALS)
      wand = WandAccess.withVis(wand, primal, parts.maxCentivis());
    inv.setItem(15, wand);
    for (int i = 0; i < 6; i++)
      inv.setItem(
          9 + i,
          EssentiaCrystalAccess.create(Aspects.resolve(h.getLevel(), TTAspects.PRIMALS.get(i)), 4));
    var menu = new MenuArcaneWorkbench(7, player.getInventory(), table);
    player.containerMenu = menu;
    menu.slotsChanged(inv);
    h.assertFalse(
        ArcaneCraftingTransaction.preview(
                table.craftingContext(player), player, inv.asArcaneCraftInput().withPlayer(player))
            .successful(),
        "host refuses missing actual research");
    h.assertTrue(menu.getSlot(0).remove(1).isEmpty(), "locked host output cannot be taken");
    h.assertValueEqual(inv.getItem(0).getCount(), 1, "locked refusal preserves ingredient");
    ResearchManager.complete(player, Content.id("foundations"));
    menu.slotsChanged(inv);
    var preview =
        ArcaneCraftingTransaction.preview(
            table.craftingContext(player), player, inv.asArcaneCraftInput().withPlayer(player));
    h.assertTrue(preview.successful(), "unlocked host recipe affordable: " + preview.failure());
    int before =
        TTAspects.PRIMALS.stream()
            .mapToInt(a -> WandAccess.getAllVis(inv.wandStack()).amount(a))
            .sum();
    ItemStack result = menu.getSlot(0).remove(1);
    h.assertTrue(
        result.is(ProgressionModule.INGREDIENTS.get("aer_sliver").get()),
        "actual host slot crafts slivers");
    h.assertValueEqual(result.getCount(), 4, "registered paid output count");
    h.assertTrue(inv.getItem(0).isEmpty(), "real grid ingredient consumed");
    int after =
        TTAspects.PRIMALS.stream()
            .mapToInt(a -> WandAccess.getAllVis(inv.wandStack()).amount(a))
            .sum();
    h.assertTrue(after < before, "actual wand vis cost conserved");
    h.assertTrue(menu.getSlot(0).remove(1).isEmpty(), "second take cannot repeat consumed input");
    player.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void actualEmptyHandGameModeDispatchOpensPaidTomeMenu(GameTestHelper h) {
    LogisticsGameTests.structure(h);
    BlockPos p = LogisticsGameTests.B.west(2);
    h.setBlock(p, InventariumModule.TOME_BLOCK.get());
    InventariumModule.TomeEntity tome = h.getBlockEntity(p);
    tome.target = LogisticsGameTests.target(h, LogisticsGameTests.A);
    TomeGameTests.power(h);
    h.runAfterDelay(
        3,
        () -> {
          TomeGameTests.relay(h);
          var player = new TestPlayer(h.getLevel());
          player.setGameMode(GameType.SURVIVAL);
          player.moveTo(h.absolutePos(p).getCenter());
          player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
          BlockHitResult hit =
              new BlockHitResult(
                  h.absolutePos(p).getCenter(), Direction.NORTH, h.absolutePos(p), false);
          var result =
              player.gameMode.useItemOn(
                  player, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
          h.assertTrue(result.consumesAction(), "real game-mode dispatch accepts empty-hand use");
          h.assertTrue(
              player.containerMenu instanceof InventariumMenu,
              "empty item falls through to paid tome menu");
          player.closeContainer();
          player.discard();
          h.succeed();
        });
  }
}
