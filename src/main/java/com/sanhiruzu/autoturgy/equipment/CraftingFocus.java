/* Portable focus menus borrow actual socketed focus contents and charged host wands.
 * The menu owns its real inputs until close; transposing swaps whole physical stacks. */
package com.sanhiruzu.autoturgy.equipment;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.recipe.*;
import com.leclowndu93150.thaumaturge.api.transaction.*;
import com.leclowndu93150.thaumaturge.api.wands.WandAccess;
import com.leclowndu93150.thaumaturge.content.casters.FocusPouchItem;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneCraftingInput;
import com.leclowndu93150.thaumaturge.content.spell.item.FocusItem;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import com.sanhiruzu.autoturgy.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.*;

public final class CraftingFocus {
  public static DeferredItem<Item> FOCUS;
  public static DeferredHolder<MenuType<?>, MenuType<FocusMenu>> TYPE;

  public static void register(IEventBus bus) {
    FocusUpgradeRecipe.register();
    FOCUS = Content.item("crafting_focus", () -> new FocusItem(new Item.Properties().stacksTo(1)));
    TYPE =
        Content.MENUS.register("crafting_focus", () -> IMenuTypeExtension.create(FocusMenu::new));
    bus.addListener(CraftingFocus::network);
    bus.addListener(
        (RegisterWorkbenchAuraSourcesEvent event) -> event.register(CraftingFocus::supplyAura));
    NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem e) -> {
          if (e.getEntity() instanceof ServerPlayer p
              && e.getItemStack().getItem() instanceof ItemWand w
              && w.socketedFocus(e.getItemStack()).is(FOCUS.get())) {
            open(p, p.getInventory().selected);
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
          }
        });
  }

  public static int supplyAura(
      ArcaneWorkbenchContext context,
      ServerPlayer player,
      IArcaneWorkbench input,
      int need,
      TransactionContext tx) {
    if (need <= 0
        || context == null
        || !(player.containerMenu instanceof FocusMenu menu)
        || !menu.contextId.equals(context.hostIdentity())
        || context.level() != player.serverLevel()
        || !player.getUUID().equals(context.actingPlayer())
        || context.kind() != ArcaneWorkbenchContext.Kind.VIRTUAL
        || !menu.stillValid(player)
        || !ItemStack.isSameItemSameComponents(
            input.wandStack(), menu.inventory.getItem(menu.wandSlot))) return 0;
    var level = player.serverLevel();
    BlockPos pos = player.blockPosition();
    if (!level.hasChunkAt(pos)
        || com.leclowndu93150.thaumaturge.api.aura.AuraHelper.shouldPreserveAura(
            level, player, pos)) return 0;
    int offered =
        Math.min(
            need,
            (int)
                Math.floor(com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getVis(level, pos)));
    if (offered <= 0) return 0;
    return (int)
        Math.floor(
            com.leclowndu93150.thaumaturge.api.aura.AuraHelper.drainVis(level, pos, offered, tx));
  }

  public record Call() implements CustomPacketPayload {
    public static final Type<Call> TYPE = new Type<>(Content.id("call_crafting_focus"));

    public Type<Call> type() {
      return TYPE;
    }
  }

  private static void network(RegisterPayloadHandlersEvent e) {
    e.registrar("1")
        .playToServer(
            Call.TYPE,
            StreamCodec.unit(new Call()),
            (msg, context) ->
                context.enqueueWork(
                    () -> {
                      if (context.player() instanceof ServerPlayer p
                          && p.containerMenu == p.inventoryMenu) call(p);
                    }));
  }

  public static CompoundTag flags(ItemStack f) {
    return f.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
  }

  public static boolean upgrade(ItemStack f, String key) {
    return flags(f).getBoolean(key);
  }

  public static void call(ServerPlayer p) {
    for (int i = 0; i < 9; i++)
      if (p.getInventory().getItem(i).getItem() instanceof ItemWand w
          && w.socketedFocus(p.getInventory().getItem(i)).is(FOCUS.get())) {
        open(p, i);
        return;
      }
    int wandSlot = p.getInventory().selected;
    ItemStack wand = p.getInventory().getItem(wandSlot);
    if (!(wand.getItem() instanceof ItemWand w)) return;
    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
      ItemStack item = p.getInventory().getItem(i);
      if (item.is(FOCUS.get()) && upgrade(item, "Transposing")) {
        ItemStack previous = w.socketedFocus(wand).copy();
        w.socketFocus(wand, item.copy());
        p.getInventory().setItem(i, previous);
        open(p, wandSlot);
        return;
      }
      if (item.getItem() instanceof FocusPouchItem && transposePouch(p, item, wand, w, wandSlot))
        return;
    }
    var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p);
    if (curios.isPresent()) {
      var all = curios.get().getEquippedCurios();
      for (int i = 0; i < all.getSlots(); i++) {
        ItemStack item = all.getStackInSlot(i);
        if (item.getItem() instanceof FocusPouchItem && transposePouch(p, item, wand, w, wandSlot))
          return;
      }
    }
  }

  private static boolean transposePouch(
      ServerPlayer p, ItemStack pouch, ItemStack wand, ItemWand w, int slot) {
    var contents = FocusPouchItem.getInventory(pouch);
    for (int j = 0; j < contents.size(); j++)
      if (contents.get(j).is(FOCUS.get()) && upgrade(contents.get(j), "Transposing")) {
        ItemStack prior = w.socketedFocus(wand).copy();
        w.socketFocus(wand, contents.get(j).copy());
        contents.set(j, prior);
        FocusPouchItem.setInventory(pouch, contents);
        open(p, slot);
        return true;
      }
    return false;
  }

  public static boolean open(ServerPlayer p, int slot) {
    if (slot < 0 || slot >= 9 || p.containerMenu != p.inventoryMenu) return false;
    ItemStack wand = p.getInventory().getItem(slot);
    if (!(wand.getItem() instanceof ItemWand w)) return false;
    ItemStack focus = w.socketedFocus(wand).copy();
    if (!focus.is(FOCUS.get())) return false;
    if (!ResearchGate.passes(
        p, new ResearchGate(Content.id("portable_crafting"), Optional.empty(), false))) {
      p.displayClientMessage(
          Component.translatable("message.autoturgy.focus.research_locked"), true);
      return false;
    }
    if (!upgrade(focus, "Gratis")) {
      if (WandAccess.getVis(wand, TTAspects.ORDO) < 25) {
        p.displayClientMessage(Component.translatable("message.autoturgy.focus.opening_vis"), true);
        return false;
      }
      wand = WandAccess.withVis(wand, TTAspects.ORDO, WandAccess.getVis(wand, TTAspects.ORDO) - 25);
      p.getInventory().setItem(slot, wand);
    }
    CompoundTag flags = flags(focus);
    if (!flags.hasUUID("FocusIdentity")) flags.putUUID("FocusIdentity", UUID.randomUUID());
    focus.set(DataComponents.CUSTOM_DATA, CustomData.of(flags));
    w.socketFocus(wand, focus);
    p.openMenu(
        new SimpleMenuProvider(
            (id, inv, pl) -> new FocusMenu(id, inv, slot),
            Component.translatable("menu.autoturgy.portable_crafting")),
        buf -> buf.writeInt(slot));
    return true;
  }

  public static final class FocusMenu extends AbstractContainerMenu {
    public final SimpleContainer inputs = new SimpleContainer(15);
    public final SimpleContainer display = new SimpleContainer(1);
    public final ContainerData status = new SimpleContainerData(16);
    private final Inventory inventory;
    private final int wandSlot;
    private final UUID identity;
    private final ItemStack physicalFocus;
    private final boolean storage, gratis;
    private final UUID contextId = UUID.randomUUID();
    private boolean closed;
    public String failure = "";

    public FocusMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
      this(id, inv, buf.readInt());
    }

    public FocusMenu(int id, Inventory inv, int slot) {
      super(TYPE.get(), id);
      inventory = inv;
      wandSlot = slot;
      ItemStack wand = slot >= 0 && slot < 9 ? inv.getItem(slot) : ItemStack.EMPTY;
      physicalFocus =
          wand.getItem() instanceof ItemWand w ? w.socketedFocus(wand).copy() : ItemStack.EMPTY;
      var flags = flags(physicalFocus);
      identity = flags.hasUUID("FocusIdentity") ? flags.getUUID("FocusIdentity") : null;
      storage = upgrade(physicalFocus, "Storage");
      gratis = upgrade(physicalFocus, "Gratis");
      if (!inv.player.level().isClientSide) {
        physicalFocus
            .getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
            .copyInto(inputs.getItems());
        physicalFocus.remove(DataComponents.CONTAINER);
        if (wand.getItem() instanceof ItemWand w) w.socketFocus(wand, physicalFocus.copy());
      }
      for (int r = 0; r < 3; r++)
        for (int c = 0; c < 3; c++) addSlot(new Slot(inputs, c + r * 3, 28 + c * 18, 27 + r * 18));
      for (int i = 0; i < 6; i++) {
        final int primalSlot = i;
        addSlot(
            new Slot(inputs, 9 + i, 10 + i * 25, 114) {
              public boolean mayPlace(ItemStack s) {
                var crystal =
                    s.get(
                        com.leclowndu93150.thaumaturge.registry.TTDataComponents.CRYSTAL_ASPECT
                            .get());
                return crystal != null && crystal.aspect().is(TTAspects.PRIMALS.get(primalSlot));
              }
            });
      }
      addSlot(
          new Slot(display, 0, 132, 45) {
            public boolean mayPlace(ItemStack s) {
              return false;
            }

            public boolean mayPickup(Player p) {
              return false;
            }
          });
      for (int r = 0; r < 3; r++)
        for (int c = 0; c < 9; c++) addPlayerSlot(inv, c + r * 9 + 9, 8 + c * 18, 162 + r * 18);
      for (int c = 0; c < 9; c++) addPlayerSlot(inv, c, 8 + c * 18, 220);
      addDataSlots(com.sanhiruzu.autoturgy.common.MenuIntTransport.wire(status));
    }

    private void addPlayerSlot(Inventory inv, int i, int x, int y) {
      addSlot(
          new Slot(inv, i, x, y) {
            public boolean mayPickup(Player p) {
              return i != wandSlot;
            }

            public boolean mayPlace(ItemStack s) {
              return i != wandSlot;
            }
          });
    }

    public boolean stillValid(Player p) {
      if (p.level().isClientSide) return true;
      if (!p.isAlive() || closed || wandSlot < 0 || wandSlot >= 9) return false;
      ItemStack wand = inventory.getItem(wandSlot);
      if (!(wand.getItem() instanceof ItemWand w)) return false;
      ItemStack f = w.socketedFocus(wand);
      var tag = flags(f);
      return f.is(FOCUS.get())
          && identity != null
          && tag.hasUUID("FocusIdentity")
          && identity.equals(tag.getUUID("FocusIdentity"));
    }

    private List<ItemStack> all() {
      List<ItemStack> list = new ArrayList<>();
      for (int i = 0; i < 15; i++) list.add(inputs.getItem(i).copy());
      list.add(inventory.getItem(wandSlot).copy());
      return list;
    }

    private ArcaneCraftingInput arcane() {
      return ArcaneCraftingInput.of(3, 3, all()).withPlayer(inventory.player);
    }

    private CraftingInput mundane() {
      return CraftingInput.of(
          3, 3, inputs.getItems().subList(0, 9).stream().map(ItemStack::copy).toList());
    }

    private ArcaneWorkbenchContext context() {
      return ArcaneWorkbenchContext.virtual(
          (ServerPlayer) inventory.player, contextId, inventory.player.getUUID());
    }

    private boolean staff() {
      ItemStack s = inventory.getItem(wandSlot);
      return !(s.getItem() instanceof ItemWand w) || w.isStaff(s);
    }

    public void broadcastChanges() {
      if (!inventory.player.level().isClientSide && stillValid(inventory.player)) {
        ItemStack result = ItemStack.EMPTY;
        if (status.get(0) == 0) {
          var match =
              inventory
                  .player
                  .level()
                  .getRecipeManager()
                  .getRecipeFor(RecipeType.CRAFTING, mundane(), inventory.player.level());
          if (match.isPresent())
            result = match.get().value().assemble(mundane(), inventory.player.registryAccess());
        } else if (!staff()) {
          var preview =
              ArcaneCraftingTransaction.preview(
                  context(), (ServerPlayer) inventory.player, arcane());
          result = preview.output();
          status.set(8, preview.cost() == null ? 0 : Math.max(0, preview.cost().auraVis()));
          status.set(15, preview.failure().ordinal());
          for (int i = 0; i < 6; i++)
            status.set(
                9 + i,
                preview.cost() == null
                    ? 0
                    : Math.max(
                        0,
                        preview
                            .cost()
                            .crystalsNeeded()
                            .amountOf(
                                Aspects.resolve(
                                    inventory.player.level(), TTAspects.PRIMALS.get(i)))));
          for (int i = 0; i < 6; i++)
            status.set(
                2 + i,
                preview.cost() == null
                    ? 0
                    : (int)
                        Math.clamp(
                            (long)
                                    preview
                                        .cost()
                                        .wandCentivis()
                                        .getOrDefault(TTAspects.PRIMALS.get(i), 0)
                                + (TTAspects.PRIMALS.get(i).equals(TTAspects.ORDO) && !gratis
                                    ? 25
                                    : 0),
                            0,
                            Integer.MAX_VALUE));
        }
        if (status.get(0) == 0 || staff()) for (int i = 2; i < 16; i++) status.set(i, 0);
        display.setItem(0, result);
        status.set(1, staff() ? 1 : 0);
      }
      super.broadcastChanges();
    }

    public boolean clickMenuButton(Player p, int button) {
      if (!stillValid(p)) return false;
      if (button == 0) {
        status.set(0, 1 - status.get(0));
        broadcastChanges();
        return true;
      }
      if (button == 1) return craft();
      return false;
    }

    public boolean craft() {
      Player p = inventory.player;
      if (!stillValid(p) || p.level().isClientSide) return false;
      if (status.get(0) == 0) {
        var input = mundane();
        var match =
            p.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, p.level());
        if (match.isEmpty()) return fail("no_recipe");
        var recipe = match.get().value();
        ItemStack output = recipe.assemble(input, p.registryAccess());
        if (output.isEmpty()) return fail("no_recipe");
        var remaining = recipe.getRemainingItems(input);
        for (int i = 0; i < 9; i++) inputs.getItem(i).shrink(1);
        for (ItemStack remainder : remaining) give(remainder.copy());
        output.onCraftedBy(p.level(), p, output.getCount());
        give(output);
        broadcastChanges();
        return true;
      }
      if (staff()) return fail("staff");
      ItemStack wand = inventory.getItem(wandSlot);
      if (!gratis && WandAccess.getVis(wand, TTAspects.ORDO) < 25) return fail("opening_vis");
      var journal =
          new SnapshotJournal<List<ItemStack>>() {
            protected List<ItemStack> createSnapshot() {
              return all();
            }

            protected void revertToSnapshot(List<ItemStack> old) {
              for (int i = 0; i < 15; i++) inputs.setItem(i, old.get(i));
              inventory.setItem(wandSlot, old.getLast());
            }
          };
      ItemStack result;
      try (Transaction tx = Transaction.openRoot()) {
        var crafted =
            ArcaneCraftingTransaction.craft(
                context(),
                (ServerPlayer) p,
                arcane(),
                (consumption, transaction) -> {
                  if (!stillValid(p)) return false;
                  var current = arcane();
                  if (current.width() * current.height() != consumption.grid().size()) return false;
                  for (int i = 0; i < consumption.grid().size(); i++)
                    if (!ItemStack.matches(current.getItem(i), consumption.grid().get(i)))
                      return false;
                  journal.updateSnapshots(transaction);
                  // The host supplies a trimmed consumed grid; all occupied recipe cells consume
                  // one.
                  for (int i = 0; i < 9; i++) inputs.getItem(i).shrink(1);
                  for (var aspect : consumption.crystals().sortedByTag()) {
                    int needed = aspect.amount();
                    for (int i = 9; i < 15 && needed > 0; i++) {
                      var crystal =
                          inputs
                              .getItem(i)
                              .get(
                                  com.leclowndu93150.thaumaturge.registry.TTDataComponents
                                      .CRYSTAL_ASPECT
                                      .get());
                      if (crystal != null && crystal.aspect().equals(aspect.aspect())) {
                        int n = Math.min(needed, inputs.getItem(i).getCount());
                        inputs.getItem(i).shrink(n);
                        needed -= n;
                      }
                    }
                    if (needed > 0) return false;
                  }
                  inventory.setItem(wandSlot, consumption.wand().copy());
                  return true;
                },
                tx);
        if (!crafted.successful()) {
          return fail(crafted.failure().name().toLowerCase(Locale.ROOT));
        }
        wand = inventory.getItem(wandSlot);
        if (!gratis) {
          int vis = WandAccess.getVis(wand, TTAspects.ORDO);
          if (vis < 25) return fail("opening_vis");
          inventory.setItem(wandSlot, WandAccess.withVis(wand, TTAspects.ORDO, vis - 25));
        }
        tx.commit();
        result = crafted.output().copy();
        // Host remainders use the trimmed grid; returning them avoids positional assumptions.
        for (ItemStack remainder : crafted.remainders()) give(remainder.copy());
      }
      give(result);
      broadcastChanges();
      return true;
    }

    private boolean fail(String reason) {
      failure = reason;
      inventory.player.displayClientMessage(
          Component.translatable("message.autoturgy.focus." + reason), true);
      return false;
    }

    private void give(ItemStack s) {
      if (!s.isEmpty() && !inventory.player.addItem(s)) inventory.player.drop(s, false);
    }

    public void removed(Player p) {
      if (!p.level().isClientSide && !closed) {
        if (storage && stillValid(p)) {
          ItemStack wand = inventory.getItem(wandSlot);
          ItemWand w = (ItemWand) wand.getItem();
          ItemStack f = w.socketedFocus(wand).copy();
          f.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inputs.getItems()));
          w.socketFocus(wand, f);
          for (int i = 0; i < 15; i++) inputs.setItem(i, ItemStack.EMPTY);
        } else clearContainer(p, inputs);
        closed = true;
      }
      super.removed(p);
    }

    public ItemStack quickMoveStack(Player p, int index) {
      if (!stillValid(p) || index < 0 || index >= slots.size() || index == 15)
        return ItemStack.EMPTY;
      Slot slot = slots.get(index);
      if (!slot.hasItem() || !slot.mayPickup(p)) return ItemStack.EMPTY;
      ItemStack s = slot.getItem(), copy = s.copy();
      if (index < 15) {
        if (!moveItemStackTo(s, 16, 52, true)) return ItemStack.EMPTY;
      } else if (s.has(
          com.leclowndu93150.thaumaturge.registry.TTDataComponents.CRYSTAL_ASPECT.get())) {
        if (!moveItemStackTo(s, 9, 15, false)) return ItemStack.EMPTY;
      } else if (!moveItemStackTo(s, 0, 9, false)) return ItemStack.EMPTY;
      if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
      else slot.setChanged();
      return copy;
    }
  }

  private CraftingFocus() {}
}
