/* Persistent requests reserve references to stock, never item copies.
 * Worker leases name physical slots; only acknowledged real delivery reduces demand. */
package com.sanhiruzu.autoturgy.inventarium;

import com.sanhiruzu.autoturgy.common.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

public final class Requisitions {
  public static final int MAX_ORDERS = 256, MAX_QUANTITY = 1000000;

  public static final class Order {
    public final UUID id, owner, tome;
    private final ItemStack template;
    public LoadedTargets.Target destination;
    private int remaining, reserved, inFlight;

    Order(
        UUID id,
        UUID owner,
        UUID tome,
        ItemStack template,
        int remaining,
        LoadedTargets.Target destination) {
      this.id = id;
      this.owner = owner;
      this.tome = tome;
      this.template = template.copyWithCount(1);
      this.remaining = remaining;
      this.destination = destination;
    }

    public ItemStack template() {
      return template.copy();
    }

    public int remaining() {
      return remaining;
    }

    public int reserved() {
      return reserved;
    }

    public int inFlight() {
      return inFlight;
    }

    public String status() {
      return remaining == 0
          ? (inFlight > 0 ? "recovering" : "complete")
          : inFlight > 0 ? "in_flight" : reserved > 0 ? "pending_worker" : "waiting_stock";
    }
  }

  public record Allocation(UUID order, InventoryIndex.Source source, int quantity) {}

  public static final class Lease {
    public final UUID id, order, worker;
    public final InventoryIndex.Source source;
    private int quantity, carried, returnAmount;

    Lease(UUID id, UUID order, UUID worker, InventoryIndex.Source source, int quantity) {
      this.id = id;
      this.order = order;
      this.worker = worker;
      this.source = source;
      this.quantity = quantity;
    }

    public int quantity() {
      return quantity;
    }

    public int carried() {
      return carried;
    }

    public int returnAmount() {
      return returnAmount;
    }
  }

  private final LinkedHashMap<UUID, Order> orders = new LinkedHashMap<>();
  private final LinkedHashMap<UUID, Lease> leases = new LinkedHashMap<>();
  private List<Allocation> allocations = List.of();

  public Collection<Order> orders() {
    return List.copyOf(orders.values());
  }

  public Optional<Order> find(UUID id) {
    return Optional.ofNullable(orders.get(id));
  }

  public Collection<Lease> leases() {
    return List.copyOf(leases.values());
  }

  public Optional<Order> request(
      UUID owner, UUID tome, ItemStack template, int delta, LoadedTargets.Target destination) {
    if (template.isEmpty() || delta == 0) return Optional.empty();
    Order order =
        orders.values().stream()
            .filter(
                o ->
                    o.owner.equals(owner)
                        && o.tome.equals(tome)
                        && ItemStack.isSameItemSameComponents(o.template, template))
            .findFirst()
            .orElse(null);
    if (order == null) {
      if (delta < 0 || orders.size() >= MAX_ORDERS) return Optional.empty();
      order = new Order(UUID.randomUUID(), owner, tome, template, 0, destination);
      orders.put(order.id, order);
    }
    order.remaining = (int) Math.clamp((long) order.remaining + delta, 0, MAX_QUANTITY);
    order.destination = destination;
    order.reserved = Math.min(order.reserved, order.remaining);
    rebalance(order);
    cleanup(order);
    return Optional.of(order);
  }

  public boolean cancel(UUID id, UUID owner) {
    Order order = orders.get(id);
    if (order == null || !order.owner.equals(owner)) return false;
    order.remaining = 0;
    order.reserved = 0;
    rebalance(order);
    cleanup(order);
    allocations = allocations.stream().filter(a -> !a.order().equals(id)).toList();
    return true;
  }

  public void reconcile(InventoryIndex.Snapshot snapshot) {
    List<Allocation> result = new ArrayList<>();
    Map<InventoryIndex.Source, Integer> assigned = new HashMap<>();
    // A collected stack belongs to its real worker. Re-indexing cannot reserve it again.
    for (Lease lease : List.copyOf(leases.values())) {
      int uncollected = lease.quantity - lease.carried;
      InventoryIndex.Source current =
          snapshot.sources().stream()
              .filter(s -> sameSource(s, lease.source))
              .findFirst()
              .orElse(null);
      int held =
          current == null
              ? 0
              : Math.min(
                  uncollected, Math.max(0, current.count() - assigned.getOrDefault(current, 0)));
      lease.quantity = lease.carried + held;
      if (current != null) assigned.merge(current, held, Integer::sum);
      if (lease.quantity == 0) leases.remove(lease.id);
    }
    for (Order order : orders.values()) {
      rebalance(order);
      int claimed =
          leases.values().stream()
              .filter(l -> l.order.equals(order.id))
              .mapToInt(l -> l.quantity - l.carried)
              .sum();
      order.reserved = claimed;
      int required = Math.max(0, order.remaining - order.inFlight);
      for (InventoryIndex.Source source : snapshot.sources()) {
        if (order.reserved >= required) break;
        if (!ItemStack.isSameItemSameComponents(order.template, source.template())) continue;
        int amount =
            Math.min(required - order.reserved, source.count() - assigned.getOrDefault(source, 0));
        if (amount > 0) {
          result.add(new Allocation(order.id, source, amount));
          assigned.merge(source, amount, Integer::sum);
          order.reserved += amount;
        }
      }
    }
    allocations = List.copyOf(result);
  }

  public List<Allocation> allocations() {
    return allocations;
  }

  private static boolean sameSource(InventoryIndex.Source a, InventoryIndex.Source b) {
    return a.target().equals(b.target())
        && a.slot() == b.slot()
        && ItemStack.isSameItemSameComponents(a.template(), b.template());
  }

  public Optional<Lease> claim(
      UUID orderId, UUID worker, InventoryIndex.Source source, int quantity) {
    Order order = orders.get(orderId);
    if (order == null || quantity <= 0 || leases.size() >= 512) return Optional.empty();
    int available =
        allocations.stream()
            .filter(a -> a.order.equals(orderId) && sameSource(a.source, source))
            .mapToInt(Allocation::quantity)
            .sum();
    if (available <= 0) return Optional.empty();
    rebalance(order);
    int outstanding =
        Math.max(
            0,
            order.remaining
                - leases.values().stream()
                    .filter(l -> l.order.equals(orderId))
                    .mapToInt(Lease::quantity)
                    .sum());
    int amount = Math.min(Math.min(Math.min(quantity, available), outstanding), 4096);
    if (amount <= 0) return Optional.empty();
    Lease lease = new Lease(UUID.randomUUID(), orderId, worker, source, amount);
    leases.put(lease.id, lease);
    int remove = amount;
    List<Allocation> result = new ArrayList<>();
    for (Allocation a : allocations) {
      if (a.order.equals(orderId) && sameSource(a.source, source) && remove > 0) {
        int take = Math.min(remove, a.quantity);
        remove -= take;
        if (a.quantity > take) result.add(new Allocation(a.order, a.source, a.quantity - take));
      } else result.add(a);
    }
    allocations = List.copyOf(result);
    return Optional.of(lease);
  }

  /** Called in the same server operation only after a real worker took custody. */
  public int collected(UUID leaseId, UUID worker, ItemStack actual, int quantity) {
    Lease lease = leases.get(leaseId);
    if (lease == null
        || !lease.worker.equals(worker)
        || !ItemStack.isSameItemSameComponents(lease.source.template(), actual)
        || quantity <= 0) return 0;
    int amount = Math.min(Math.min(quantity, actual.getCount()), lease.quantity - lease.carried);
    lease.carried += amount;
    Order order = orders.get(lease.order);
    if (order != null) rebalance(order);
    return amount;
  }

  public boolean releaseUncollected(UUID leaseId, UUID worker) {
    Lease lease = leases.get(leaseId);
    if (lease == null || !lease.worker.equals(worker)) return false;
    lease.quantity = lease.carried;
    if (lease.quantity == 0) leases.remove(leaseId);
    return true;
  }

  /** Host death drops its real hands. Retire metadata without satisfying the order. */
  public void workerDied(UUID worker) {
    Set<UUID> affected = new HashSet<>();
    for (Lease lease : List.copyOf(leases.values()))
      if (lease.worker.equals(worker)) {
        affected.add(lease.order);
        leases.remove(lease.id);
      }
    for (UUID id : affected) {
      Order order = orders.get(id);
      if (order != null) {
        rebalance(order);
        cleanup(order);
      }
    }
  }

  /** A physical return releases recovery metadata without pretending to fulfill demand. */
  public int returned(UUID leaseId, UUID worker, ItemStack actual, int quantity) {
    return finishLease(leaseId, worker, actual, quantity, false);
  }

  public int deliveredByWorker(UUID leaseId, UUID worker, ItemStack actual, int quantity) {
    return finishLease(leaseId, worker, actual, quantity, true);
  }

  private int finishLease(UUID id, UUID worker, ItemStack actual, int quantity, boolean delivered) {
    Lease lease = leases.get(id);
    if (lease == null
        || !lease.worker.equals(worker)
        || !ItemStack.isSameItemSameComponents(lease.source.template(), actual)
        || quantity <= 0) return 0;
    Order order = orders.get(lease.order);
    if (order == null) return 0;
    int available =
        delivered ? Math.min(order.remaining, lease.carried - lease.returnAmount) : lease.carried;
    int amount = Math.min(Math.min(quantity, actual.getCount()), available);
    lease.carried -= amount;
    lease.quantity -= amount;
    if (delivered) order.remaining -= amount;
    if (lease.quantity == 0) leases.remove(id);
    rebalance(order);
    cleanup(order);
    return amount;
  }

  private void rebalance(Order order) {
    List<Lease> active = leases.values().stream().filter(l -> l.order.equals(order.id)).toList();
    order.inFlight = active.stream().mapToInt(l -> l.carried).sum();
    int deliverable = order.remaining;
    for (Lease lease : active) {
      int keep = Math.min(deliverable, lease.carried);
      deliverable -= keep;
      lease.returnAmount = lease.carried - keep;
    }
    for (Lease lease : active) {
      int pending = Math.min(Math.max(0, deliverable), lease.quantity - lease.carried);
      deliverable -= pending;
      lease.quantity = lease.carried + pending;
      if (lease.quantity == 0) leases.remove(lease.id);
    }
  }

  private void cleanup(Order order) {
    if (order.remaining == 0 && leases.values().stream().noneMatch(l -> l.order.equals(order.id)))
      orders.remove(order.id);
  }

  public long demand(StackFilters.Filter filter) {
    return orders.values().stream()
        .filter(o -> StackFilters.accepts(filter, o.template))
        .mapToLong(Order::remaining)
        .sum();
  }

  public long reserved(ItemStack template) {
    return orders.values().stream()
        .filter(o -> ItemStack.isSameItemSameComponents(template, o.template))
        .mapToLong(Order::reserved)
        .sum();
  }

  public long demand(ItemStack template) {
    return orders.values().stream()
        .filter(o -> ItemStack.isSameItemSameComponents(template, o.template))
        .mapToLong(Order::remaining)
        .sum();
  }

  public CompoundTag save(HolderLookup.Provider lookup) {
    CompoundTag tag = new CompoundTag();
    ListTag list = new ListTag();
    for (Order o : orders.values()) {
      CompoundTag row = new CompoundTag();
      row.putUUID("id", o.id);
      row.putUUID("owner", o.owner);
      row.putUUID("tome", o.tome);
      row.put("item", o.template.save(lookup));
      row.putInt("remaining", o.remaining);
      if (o.destination != null) row.put("destination", o.destination.save());
      list.add(row);
    }
    tag.put("orders", list);
    ListTag savedLeases = new ListTag();
    for (Lease lease : leases.values()) {
      CompoundTag row = new CompoundTag();
      row.putUUID("id", lease.id);
      row.putUUID("order", lease.order);
      row.putUUID("worker", lease.worker);
      row.put("source", lease.source.target().save());
      row.putInt("slot", lease.source.slot());
      row.put("item", lease.source.template().save(lookup));
      row.putInt("quantity", lease.quantity);
      row.putInt("carried", lease.carried);
      savedLeases.add(row);
    }
    tag.put("leases", savedLeases);
    return tag;
  }

  public void load(CompoundTag tag, HolderLookup.Provider lookup) {
    orders.clear();
    leases.clear();
    allocations = List.of();
    ListTag list = tag.getList("orders", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(MAX_ORDERS, list.size()); i++) {
      CompoundTag row = list.getCompound(i);
      if (!row.hasUUID("id") || !row.hasUUID("owner") || !row.hasUUID("tome")) continue;
      ItemStack stack = ItemStack.parseOptional(lookup, row.getCompound("item"));
      int remaining = Math.clamp(row.getInt("remaining"), 0, MAX_QUANTITY);
      if (!stack.isEmpty()) {
        Order o =
            new Order(
                row.getUUID("id"),
                row.getUUID("owner"),
                row.getUUID("tome"),
                stack,
                remaining,
                LoadedTargets.Target.load(row.getCompound("destination")).orElse(null));
        orders.put(o.id, o);
      }
    }
    ListTag saved = tag.getList("leases", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(512, saved.size()); i++) {
      CompoundTag row = saved.getCompound(i);
      if (!row.hasUUID("id")
          || !row.hasUUID("order")
          || !row.hasUUID("worker")
          || !orders.containsKey(row.getUUID("order"))) continue;
      ItemStack item = ItemStack.parseOptional(lookup, row.getCompound("item"));
      var source = LoadedTargets.Target.load(row.getCompound("source")).orElse(null);
      int quantity = Math.clamp(row.getInt("quantity"), 0, 4096);
      if (source == null || item.isEmpty() || quantity == 0) continue;
      Lease lease =
          new Lease(
              row.getUUID("id"),
              row.getUUID("order"),
              row.getUUID("worker"),
              new InventoryIndex.Source(
                  source, Math.clamp(row.getInt("slot"), 0, 4095), item, quantity),
              quantity);
      lease.carried = Math.clamp(row.getInt("carried"), 0, quantity);
      leases.put(lease.id, lease);
    }
    for (Order o : List.copyOf(orders.values())) {
      rebalance(o);
      cleanup(o);
    }
  }
}
