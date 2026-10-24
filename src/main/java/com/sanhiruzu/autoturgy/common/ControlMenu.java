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
  public final int visibleMask, editableMask;
  public final List<String> aspects;
  private final Map<String, com.sanhiruzu.autoturgy.host.ThaumaturgeIntegration.AspectReading>
      aspectDisplay = new HashMap<>();

  public static void writeOpening(RegistryFriendlyByteBuf buf, DeviceEntity device) {
    buf.writeBlockPos(device.getBlockPos());
    buf.writeUtf(device.kind());
    buf.writeVarInt(device.controlCount());
    buf.writeVarInt(device.selectionControl());
    List<String> options = device.selectionOptions();
    buf.writeVarInt(options.size());
    options.forEach(option -> buf.writeUtf(option, 128));
    int visible = 0, editable = 0;
    for (int i = 0; i < device.controlCount(); i++) {
      if (device.controlVisible(i)) visible |= 1 << i;
      if (device.controlEditable(i)) editable |= 1 << i;
    }
    buf.writeVarInt(visible);
    buf.writeVarInt(editable);
    var aspects = device.menuAspects().stream().limit(64).toList();
    buf.writeVarInt(aspects.size());
    aspects.forEach(a -> buf.writeUtf(a.id(), 128));
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
    visibleMask = buf.readVarInt();
    editableMask = buf.readVarInt();
    int aspectCount = Math.clamp(buf.readVarInt(), 0, 64);
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < aspectCount; i++) keys.add(buf.readUtf(128));
    aspects = List.copyOf(keys);
    data = new SimpleContainerData(14 + aspects.size() * 2);
    addDataSlots(MenuIntTransport.wire(data));
  }

  public ControlMenu(int id, Inventory inv, DeviceEntity device) {
    super(TYPE.get(), id);
    this.device = device;
    kind = device.kind();
    controls = device.controlCount();
    selectionControl = device.selectionControl();
    selections = List.copyOf(device.selectionOptions());
    int visible = 0, editable = 0;
    for (int i = 0; i < controls; i++) {
      if (device.controlVisible(i)) visible |= 1 << i;
      if (device.controlEditable(i)) editable |= 1 << i;
    }
    visibleMask = visible;
    editableMask = editable;
    aspects = device.menuAspects().stream().limit(64).map(a -> a.id()).toList();
    refreshAspects();
    data =
        new ContainerData() {
          public int get(int index) {
            if (index >= 14) {
              String key = aspects.get((index - 14) / 2);
              var reading = aspectDisplay.get(key);
              return reading == null ? 0 : (index % 2 == 0 ? reading.amount() : reading.capacity());
            }
            return index == selectionControl + 2 && selectionControl >= 0
                ? Math.max(0, selections.indexOf(device.selectionKey()))
                : device.data.get(index);
          }

          public void set(int index, int value) {}

          public int getCount() {
            return 14 + aspects.size() * 2;
          }
        };
    addDataSlots(MenuIntTransport.wire(data));
  }

  private void refreshAspects() {
    aspectDisplay.clear();
    if (device != null)
      device.menuAspects().stream().limit(64).forEach(a -> aspectDisplay.put(a.id(), a));
  }

  @Override
  public void broadcastChanges() {
    refreshAspects();
    super.broadcastChanges();
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
    if (id < 0 || id >= controls * 2 || !device.controlEditable(id / 2)) return false;
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
