/* Ghost editor persists bounded filter settings into the held item.
 * Inventory movement never transfers an item into any sample slot. */
package com.sanhiruzu.autoturgy.sensing;

import com.sanhiruzu.autoturgy.Content;
import com.sanhiruzu.autoturgy.common.StackFilters;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class FilterMenu extends AbstractContainerMenu {
  public static DeferredHolder<MenuType<?>, MenuType<FilterMenu>> TYPE;
  private final Inventory inventory;
  private final int heldSlot;
  private final ItemStack held;
  private final SimpleContainer ghosts =
      new SimpleContainer(9) {
        @Override
        public int getMaxStackSize() {
          return 4096;
        }
      };
  public final ContainerData flags = new SimpleContainerData(5);

  public static void register() {
    TYPE = Content.MENUS.register("filter", () -> IMenuTypeExtension.create(FilterMenu::new));
  }

  public FilterMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
    this(id, inv, Math.clamp(buf.readVarInt(), 0, 40));
  }

  public FilterMenu(int id, Inventory inv, int slot) {
    super(TYPE.get(), id);
    inventory = inv;
    heldSlot = slot;
    held = inv.getItem(slot);
    StackFilters.Filter filter =
        StackFilters.load(SensingModule.itemData(held), inv.player.level().registryAccess());
    for (int i = 0; i < filter.samples().size(); i++)
      ghosts.setItem(
          i, filter.samples().get(i).template().copyWithCount(filter.samples().get(i).quantity()));
    flags.set(0, filter.blacklist() ? 1 : 0);
    flags.set(1, filter.options().components() ? 1 : 0);
    flags.set(2, filter.options().damage() ? 1 : 0);
    flags.set(3, filter.options().tags() ? 1 : 0);
    flags.set(4, filter.options().namespace() ? 1 : 0);
    for (int i = 0; i < 9; i++)
      addSlot(
          new Slot(ghosts, i, 17 + i * 18, 87) {
            @Override
            public boolean mayPickup(Player p) {
              return false;
            }

            @Override
            public boolean mayPlace(ItemStack s) {
              return false;
            }
          });
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, col + row * 9 + 9, 17 + col * 18, 125 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 17 + col * 18, 183));
    addDataSlots(flags);
  }

  private void save() {
    List<StackFilters.Sample> samples = new ArrayList<>();
    for (int i = 0; i < 9; i++)
      if (!ghosts.getItem(i).isEmpty())
        samples.add(new StackFilters.Sample(ghosts.getItem(i), ghosts.getItem(i).getCount()));
    StackFilters.Filter filter =
        new StackFilters.Filter(
            samples,
            flags.get(0) != 0,
            new StackFilters.MatchOptions(
                flags.get(1) != 0, flags.get(2) != 0, flags.get(3) != 0, flags.get(4) != 0));
    SensingModule.itemData(
        held, StackFilters.save(filter, inventory.player.level().registryAccess()));
    inventory.setChanged();
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (!stillValid(player)) return;
    if (slot >= 0 && slot < 9) {
      if (!player.level().isClientSide) {
        ItemStack cursor = getCarried();
        ItemStack previous = ghosts.getItem(slot);
        if (type == ClickType.THROW || cursor.isEmpty() && button == 0)
          ghosts.setItem(slot, ItemStack.EMPTY);
        else if (!cursor.isEmpty())
          ghosts.setItem(slot, cursor.copyWithCount(button == 1 ? 1 : cursor.getCount()));
        else if (!previous.isEmpty() && button == 1)
          ghosts.setItem(slot, previous.copyWithCount(Math.min(4096, previous.getCount() + 1)));
        save();
        broadcastChanges();
      }
      return;
    }
    super.clicked(slot, button, type, player);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (!player.level().isClientSide && stillValid(player) && index >= 9 && index < slots.size()) {
      ItemStack source = slots.get(index).getItem();
      if (!source.isEmpty())
        for (int i = 0; i < 9; i++)
          if (ghosts.getItem(i).isEmpty()) {
            ghosts.setItem(i, source.copy());
            save();
            break;
          }
    }
    return ItemStack.EMPTY;
  }

  @Override
  public boolean clickMenuButton(Player player, int button) {
    if (!stillValid(player) || button < 0 || button >= 5) return false;
    flags.set(button, 1 - flags.get(button));
    save();
    return true;
  }

  @Override
  public boolean stillValid(Player player) {
    return inventory.getItem(heldSlot) == held && held.getItem() instanceof FilterItem;
  }
}
