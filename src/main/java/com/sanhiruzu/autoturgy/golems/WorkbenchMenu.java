/* Server-owned workbench editor separates non-consuming patterns from real inventories.
 * Page selection and every ghost click revalidate distance, ownership and Fire capacity. */
package com.sanhiruzu.autoturgy.golems;

import com.sanhiruzu.autoturgy.Content;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class WorkbenchMenu extends AbstractContainerMenu {
  public static DeferredHolder<MenuType<?>, MenuType<WorkbenchMenu>> TYPE;
  private final GolemWorkbench.BenchEntity bench;
  private final SimpleContainer ghosts = new SimpleContainer(9);
  public final ContainerData data = new SimpleContainerData(8);

  static void register() {
    TYPE =
        Content.MENUS.register(
            "golem_workbench", () -> IMenuTypeExtension.create(WorkbenchMenu::new));
  }

  public WorkbenchMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
    this(
        id,
        inv,
        inv.player.level().getBlockEntity(buf.readBlockPos())
                instanceof GolemWorkbench.BenchEntity b
            ? b
            : null);
  }

  public WorkbenchMenu(int id, Inventory inv, GolemWorkbench.BenchEntity bench) {
    super(TYPE.get(), id);
    this.bench = bench;
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 3; col++)
        addSlot(
            new Slot(ghosts, col + row * 3, 17 + col * 18, 43 + row * 18) {
              public boolean mayPlace(ItemStack s) {
                return false;
              }

              public boolean mayPickup(Player p) {
                return false;
              }
            });
    IItemHandler ingredients = bench == null ? new ItemStackHandler(18) : bench.ingredients;
    IItemHandler output = bench == null ? new ItemStackHandler(9) : bench.output;
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 6; col++)
        addSlot(new SlotItemHandler(ingredients, col + row * 6, 97 + col * 18, 43 + row * 18));
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 3; col++)
        addSlot(
            new SlotItemHandler(output, col + row * 3, 229 + col * 18, 43 + row * 18) {
              public boolean mayPlace(ItemStack s) {
                return false;
              }
            });
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, col + row * 9 + 9, 70 + col * 18, 139 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 70 + col * 18, 197));
    addDataSlots(data);
    refresh();
  }

  private void refresh() {
    if (bench == null) return;
    data.set(1, bench.patternCount());
    data.set(2, Math.min(32767, bench.crafts));
    data.set(3, bench.paused() ? 1 : 0);
    int n = 4;
    for (var side :
        new net.minecraft.core.Direction[] {
          net.minecraft.core.Direction.NORTH,
          net.minecraft.core.Direction.EAST,
          net.minecraft.core.Direction.SOUTH,
          net.minecraft.core.Direction.WEST
        }) {
      var u = bench.upgrades.get(side);
      data.set(n++, u == null ? 0 : u.ordinal() + 1);
    }
    if (data.get(0) >= bench.patternCount()) data.set(0, 0);
    for (int i = 0; i < 9; i++) ghosts.setItem(i, bench.patterns.get(data.get(0) * 9 + i).copy());
  }

  public void broadcastChanges() {
    if (bench != null && !bench.getLevel().isClientSide) refresh();
    super.broadcastChanges();
  }

  public boolean stillValid(Player p) {
    return bench == null ? p.level().isClientSide : bench.usable(p);
  }

  public boolean clickMenuButton(Player p, int button) {
    if (!stillValid(p) || bench == null || button < 0 || button >= bench.patternCount())
      return false;
    data.set(0, button);
    refresh();
    broadcastChanges();
    return true;
  }

  public void clicked(int slot, int button, ClickType type, Player p) {
    if (!stillValid(p)) return;
    if (slot >= 0 && slot < 9) {
      if (!p.level().isClientSide && bench != null) {
        bench.pattern(data.get(0), slot, type == ClickType.THROW ? ItemStack.EMPTY : getCarried());
        refresh();
        broadcastChanges();
      }
      return;
    }
    super.clicked(slot, button, type, p);
  }

  public ItemStack quickMoveStack(Player p, int index) {
    if (!stillValid(p) || index < 9 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack stack = slot.getItem(), copy = stack.copy();
    if (index < 36) {
      if (!moveItemStackTo(stack, 36, 72, true)) return ItemStack.EMPTY;
    } else if (!moveItemStackTo(stack, 9, 27, false)) return ItemStack.EMPTY;
    if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
    else slot.setChanged();
    return copy;
  }
}
