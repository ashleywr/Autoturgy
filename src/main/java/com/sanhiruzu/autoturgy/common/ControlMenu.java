/* Vanilla menu-button transport bounds every server-side device mutation.
 * The client gets a fixed schema and synchronized numeric display, never authority. */
package com.sanhiruzu.autoturgy.common;

import com.sanhiruzu.autoturgy.Content;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ControlMenu extends AbstractContainerMenu {
  public static DeferredHolder<MenuType<?>, MenuType<ControlMenu>> TYPE;
  public final String kind;
  public final int controls;
  public final ContainerData data;
  private final DeviceEntity device;
  public final List<String> selections;
  public final int selectionControl;

  public static void writeOpening(RegistryFriendlyByteBuf buf, DeviceEntity device) {
    buf.writeBlockPos(device.getBlockPos());
    buf.writeUtf(device.kind());
    buf.writeVarInt(device.controlCount());
    buf.writeVarInt(device.selectionControl());
    List<String> options = device.selectionOptions();
    buf.writeVarInt(options.size());
    options.forEach(option -> buf.writeUtf(option, 128));
  }

  public static void register() {
    TYPE = Content.MENUS.register("device", () -> IMenuTypeExtension.create(ControlMenu::new));
  }

  public ControlMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
    super(TYPE.get(), id);
    buf.readBlockPos();
    kind = buf.readUtf(64);
    controls = Math.clamp(buf.readVarInt(), 0, 12);
    device = null;
    selectionControl = buf.readVarInt();
    int count = Math.clamp(buf.readVarInt(), 0, 66);
    List<String> options = new ArrayList<>();
    for (int i = 0; i < count; i++) options.add(buf.readUtf(128));
    selections = List.copyOf(options);
    data = new SimpleContainerData(14);
    addDataSlots(MenuIntTransport.wire(data));
  }

  public ControlMenu(int id, Inventory inv, DeviceEntity device) {
    super(TYPE.get(), id);
    this.device = device;
    kind = device.kind();
    controls = device.controlCount();
    selectionControl = device.selectionControl();
    selections = List.copyOf(device.selectionOptions());
    data =
        new ContainerData() {
          public int get(int index) {
            return index == selectionControl + 2 && selectionControl >= 0
                ? Math.max(0, selections.indexOf(device.selectionKey()))
                : device.data.get(index);
          }

          public void set(int index, int value) {}

          public int getCount() {
            return 14;
          }
        };
    addDataSlots(MenuIntTransport.wire(data));
  }

  @Override
  public boolean clickMenuButton(Player player, int id) {
    if (device == null || !device.usable(player)) return false;
    if (id == 80) {
      device.activate();
      return true;
    }
    if (id == 81) {
      device.cancel();
      return true;
    }
    if (id < 0 || id >= controls * 2) return false;
    if (id / 2 == selectionControl && !selections.isEmpty()) {
      int current = Math.max(0, selections.indexOf(device.selectionKey()));
      device.selectKey(
          selections.get(Math.floorMod(current + (id % 2 == 0 ? -1 : 1), selections.size())));
      return true;
    }
    device.configure(id / 2, id % 2 == 0 ? -1 : 1);
    return true;
  }

  @Override
  public boolean stillValid(Player player) {
    return device == null || device.usable(player);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }
}
