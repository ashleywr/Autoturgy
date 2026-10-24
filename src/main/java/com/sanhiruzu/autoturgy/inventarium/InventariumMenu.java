/* Server-side queries page every indexed identity without materializing stock.
 * Revision-checked requests and ghost samples separate display from real input custody. */
package com.sanhiruzu.autoturgy.inventarium;

import com.sanhiruzu.autoturgy.Content;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class InventariumMenu extends AbstractContainerMenu {
  public static DeferredHolder<MenuType<?>, MenuType<InventariumMenu>> TYPE;
  public static final int ROWS = 3, META = ROWS * 12;
  public final SimpleContainer catalogue = new SimpleContainer(ROWS),
      sample = new SimpleContainer(1);
  public final ContainerData counts;
  public final boolean requests, scribeView;
  private final InventariumModule.CoreEntity core;
  private final InventariumModule.TomeEntity tome;
  private final InventariumModule.ScribeEntity scribe;
  private String query = "";
  private boolean byCount, local;
  private int page, revision, totalRows;
  private long lastRefresh = Long.MIN_VALUE, lastQuery = Long.MIN_VALUE;
  private InventoryIndex.Snapshot displayed = InventoryIndex.Snapshot.empty();

  public static void register() {
    TYPE =
        Content.MENUS.register(
            "inventarium", () -> IMenuTypeExtension.create(InventariumMenu::new));
  }

  public static void writeOpening(RegistryFriendlyByteBuf buf, boolean requests, boolean scribe) {
    buf.writeBoolean(requests);
    buf.writeBoolean(scribe);
  }

  public InventariumMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
    super(TYPE.get(), id);
    requests = buf.readBoolean();
    scribeView = buf.readBoolean();
    core = null;
    tome = null;
    scribe = null;
    counts = new SimpleContainerData(META + 13);
    initialize(inv, new net.neoforged.neoforge.items.ItemStackHandler(1));
  }

  public InventariumMenu(
      int id,
      Inventory inv,
      InventariumModule.CoreEntity core,
      InventariumModule.TomeEntity tome,
      InventariumModule.ScribeEntity scribe) {
    super(TYPE.get(), id);
    this.core = core;
    this.tome = tome;
    this.scribe = scribe;
    requests = tome != null;
    scribeView = scribe != null;
    local = scribeView;
    counts =
        new ContainerData() {
          public int getCount() {
            return META + 13;
          }

          public void set(int i, int v) {}

          public int get(int i) {
            if (i == META) return core.structure.capacity();
            if (i == META + 1) return core.index.slots();
            if (i == META + 2) return core.settings[0];
            if (i == META + 3) return page;
            if (i == META + 4) return totalRows;
            if (i == META + 5) return revision;
            if (i == META + 6) return local ? 1 : 0;
            if (i == META + 7) return tome == null ? 0 : tome.settings[0];
            if (i == META + 8) return core.memoryUsed;
            if (i == META + 9) return core.memoryMaximum;
            if (i == META + 10) return core.scribes.size();
            if (i == META + 12)
              return core.orders.orders().stream().anyMatch(o -> o.remaining() > 0) ? 1 : 0;
            if (i == META + 11) return 1 + core.structure.clusters();
            ItemStack template = catalogue.getItem(i / 12);
            int part = i % 12;
            long value =
                part < 4
                    ? displayed.count(template)
                    : part < 8 ? core.orders.reserved(template) : core.orders.demand(template);
            return (int) (value >> (part % 4 * 16)) & 65535;
          }
        };
    initialize(
        inv, tome == null ? new net.neoforged.neoforge.items.ItemStackHandler(1) : tome.input);
    refresh();
  }

  private void initialize(Inventory inv, net.neoforged.neoforge.items.IItemHandler input) {
    for (int i = 0; i < ROWS; i++)
      addSlot(
          new Slot(catalogue, i, -1000, -1000) {
            @Override
            public boolean mayPickup(Player p) {
              return false;
            }

            @Override
            public boolean mayPlace(ItemStack s) {
              return false;
            }
          });
    if (requests) {
      addSlot(
          new Slot(sample, 0, 10, 132) {
            @Override
            public boolean mayPickup(Player p) {
              return false;
            }

            @Override
            public boolean mayPlace(ItemStack s) {
              return false;
            }
          });
      addSlot(new SlotItemHandler(input, 0, 282, 132));
      for (int row = 0; row < 3; row++)
        for (int col = 0; col < 9; col++)
          addSlot(new Slot(inv, col + row * 9 + 9, 74 + col * 18, 154 + row * 18));
      for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 74 + col * 18, 212));
    }
    addDataSlots(com.sanhiruzu.autoturgy.common.MenuIntTransport.wire(counts));
  }

  private InventoryIndex.Snapshot view() {
    if (!local || scribe == null) return core.index;
    Set<com.sanhiruzu.autoturgy.common.LoadedTargets.Target> allowed =
        new HashSet<>(scribe.candidates());
    List<InventoryIndex.Source> sources =
        core.index.sources().stream().filter(s -> allowed.contains(s.target())).toList();
    List<InventoryIndex.Entry> entries = new ArrayList<>();
    for (var source : sources) {
      int match = -1;
      for (int i = 0; i < entries.size(); i++)
        if (ItemStack.isSameItemSameComponents(entries.get(i).template(), source.template())) {
          match = i;
          break;
        }
      if (match < 0) entries.add(new InventoryIndex.Entry(source.template(), source.count()));
      else {
        var old = entries.get(match);
        entries.set(match, new InventoryIndex.Entry(old.template(), old.count() + source.count()));
      }
    }
    return new InventoryIndex.Snapshot(sources, entries, core.index.slots(), 0);
  }

  public void refresh() {
    if (core == null) return;
    InventoryIndex.Snapshot snapshot = view();
    displayed = snapshot;
    List<InventoryIndex.Entry> entries = new ArrayList<>(snapshot.entries());
    for (var order : core.orders.orders())
      if (entries.stream()
          .noneMatch(e -> ItemStack.isSameItemSameComponents(e.template(), order.template())))
        entries.add(new InventoryIndex.Entry(order.template(), 0));
    List<InventoryIndex.Entry> found =
        InventoryIndex.search(
            new InventoryIndex.Snapshot(List.of(), entries, 0, 0), query, byCount);
    totalRows = found.size();
    page = Math.clamp(page, 0, Math.max(0, (totalRows - 1) / ROWS));
    boolean changed = false;
    for (int row = 0; row < ROWS; row++) {
      int index = page * ROWS + row;
      ItemStack next = index < found.size() ? found.get(index).template() : ItemStack.EMPTY;
      if (!ItemStack.isSameItemSameComponents(next, catalogue.getItem(row))) changed = true;
      catalogue.setItem(row, next);
    }
    if (changed) revision = (revision + 1) & 32767;
    lastRefresh = core.getLevel().getGameTime();
  }

  public long value(int row, int metric) {
    long result = 0;
    for (int word = 0; word < 4; word++)
      result |= (counts.get(row * 12 + metric * 4 + word) & 65535L) << (word * 16);
    return result;
  }

  public boolean hasDemand() {
    return counts.get(META + 12) != 0;
  }

  public int revision() {
    return counts.get(META + 5);
  }

  @Override
  public boolean stillValid(Player player) {
    if (core == null) return true;
    if (core.isRemoved()
        || core.getLevel() == null
        || !core.getLevel().hasChunkAt(core.getBlockPos())
        || core.getLevel().getBlockEntity(core.getBlockPos()) != core) return false;
    return tome != null
        ? tome.usable(player) && tome.operational()
        : scribe != null ? scribe.usable(player) && scribe.core() == core : core.usable(player);
  }

  public boolean intent(
      Player player, int expected, int action, String query, int requestedPage, boolean sort) {
    if (core == null || !stillValid(player)) return false;
    if (action == 0) {
      long now = core.getLevel().getGameTime();
      if (query.length() > 64 || lastQuery != Long.MIN_VALUE && now - lastQuery < 2) return false;
      lastQuery = now;
      this.query = query;
      page = Math.clamp(requestedPage, 0, 8192);
      byCount = sort;
      refresh();
      broadcastChanges();
      return true;
    }
    if (action == 1 && tome == null) {
      core.settings[0] ^= 1;
      core.changed();
      return true;
    }
    if (action == 2 && scribe != null) {
      local = !local;
      page = 0;
      refresh();
      return true;
    }
    if (action == 4 && scribe != null) {
      scribe.resetType();
      page = 0;
      refresh();
      return true;
    }
    if (action == 3 && tome != null) {
      tome.settings[0] = tome.settings[0] == 1 ? 2 : 1;
      tome.changed();
      return true;
    }
    if (!requests
        || expected != revision
        || action < 100
        || action >= 100 + (ROWS + 1) * 16
        || !core.structure.valid()) return false;
    int row = (action - 100) / 16, mode = (action - 100) % 16;
    ItemStack template = row == ROWS ? sample.getItem(0) : catalogue.getItem(row);
    if (template.isEmpty()) return false;
    int quantity = (mode & 2) != 0 ? template.getMaxStackSize() : 1;
    if ((mode & 4) != 0) quantity *= 10;
    if ((mode & 1) == 0) quantity = -quantity;
    if (quantity > 0 && row < ROWS && (mode & 8) == 0) {
      long own =
          core.orders.orders().stream()
              .filter(
                  o ->
                      o.owner.equals(player.getUUID())
                          && o.tome.equals(tome.identity)
                          && ItemStack.isSameItemSameComponents(template, o.template()))
              .mapToLong(Requisitions.Order::remaining)
              .sum();
      quantity = (int) Math.min(quantity, Math.max(0, core.index.count(template) - own));
    }
    if (quantity != 0)
      core.orders.request(player.getUUID(), tome.identity, template, quantity, tome.destination);
    core.orders.reconcile(core.index);
    core.changed();
    refresh();
    broadcastChanges();
    return true;
  }

  @Override
  public void broadcastChanges() {
    if (core != null && core.getLevel().getGameTime() - lastRefresh >= 20) refresh();
    super.broadcastChanges();
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (slot >= 0 && slot < ROWS) return;
    if (requests && slot == ROWS) {
      if (core != null && stillValid(player)) {
        ItemStack carried = getCarried();
        sample.setItem(0, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
      }
      return;
    }
    super.clicked(slot, button, type, player);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (!requests || index < ROWS + 1 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    ItemStack stack = slot.getItem();
    if (stack.isEmpty()) return ItemStack.EMPTY;
    if (index > ROWS + 1) {
      sample.setItem(0, stack.copyWithCount(1));
      return ItemStack.EMPTY;
    }
    ItemStack original = stack.copy();
    if (!moveItemStackTo(stack, ROWS + 2, slots.size(), false)) return ItemStack.EMPTY;
    slot.setByPlayer(stack);
    return original;
  }
}
