/* Drives actual host navigation and hands through Inventarium leases and bench work.
 * Persistent custody contains references only; physical stacks stay in host hands. */
package com.sanhiruzu.autoturgy.golems;

import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.inventarium.*;
import com.sanhiruzu.autoturgy.transport.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.*;

public final class GolemWorkGoal extends Goal {
  private final EntityThaumaturgeGolem golem;

  GolemWorkGoal(EntityThaumaturgeGolem golem) {
    this.golem = golem;
    setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
  }

  public boolean canUse() {
    return golem.isAlive()
        && !golem.isNoAi()
        && !golem.isInCombat()
        && !golem.isFollowingOwner()
        && !golem.isFettered()
        && !GolemModule.inhibited(golem)
        && (golem.getPersistentData().contains(GolemModule.CUSTODY)
            || GolemModule.linked(golem) instanceof GolemWorkbench.BenchEntity
            || GolemModule.linked(golem) != null && GolemModule.linked(golem).taskmaster());
  }

  public boolean canContinueToUse() {
    return canUse();
  }

  public boolean requiresUpdateEveryTick() {
    return true;
  }

  public void stop() {
    golem.getNavigation().stop();
  }

  public void tick() {
    if (!(golem.level() instanceof ServerLevel level)) return;
    var device = GolemModule.linked(golem);
    CompoundTag custody = golem.getPersistentData().getCompound(GolemModule.CUSTODY);
    var savedCore = LoadedTargets.Target.load(custody).orElse(null);
    InventariumModule.CoreEntity core = null;
    if (savedCore != null) {
      if (LoadedTargets.level(level.getServer(), savedCore).isEmpty()) return;
      var be = LoadedTargets.blockEntity(level.getServer(), savedCore).orElse(null);
      if (be instanceof InventariumModule.CoreEntity c
          && custody.hasUUID("core")
          && cIdentity(c).equals(custody.getUUID("core"))) core = c;
      else {
        dropHands();
        golem.getPersistentData().remove(GolemModule.CUSTODY);
        return;
      }
    } else if (device != null && device.taskmaster()) core = device.core();
    if (core == null) {
      if (device instanceof GolemWorkbench.BenchEntity bench) benchWork(bench);
      return;
    }
    var lease =
        core.orders.leases().stream()
            .filter(l -> l.worker.equals(golem.getUUID()))
            .findFirst()
            .orElse(null);
    if (lease == null) {
      golem.getPersistentData().remove(GolemModule.CUSTODY);
      if (device == null || !device.compatible(golem) || device.core() != core || !emptyHands())
        return;
      for (var a : core.orders.allocations()) {
        var order = core.orders.find(a.order()).orElse(null);
        if (order == null
            || !Objects.equals(order.owner, golem.getOwnerUUID())
            || !permitted(core, a.source().target())) continue;
        int room =
            Math.min(
                a.source().template().getMaxStackSize(), golem.hands().room(a.source().template()));
        lease = core.orders.claim(a.order(), golem.getUUID(), a.source(), room).orElse(null);
        if (lease != null) {
          CompoundTag tag =
              new LoadedTargets.Target(level.dimension(), core.getBlockPos(), Direction.UP).save();
          tag.putUUID("core", cIdentity(core));
          golem.getPersistentData().put(GolemModule.CUSTODY, tag);
          core.changed();
          break;
        }
      }
      if (lease == null) {
        approachHome(device.getBlockPos());
        return;
      }
    }
    boolean active =
        device != null && device.taskmaster() && device.compatible(golem) && device.core() == core;
    if (lease.carried() == 0) {
      if (!active || !permitted(core, lease.source.target())) {
        core.orders.releaseUncollected(lease.id, golem.getUUID());
        core.changed();
        return;
      }
      if (!arrived(lease.source.target().pos())) return;
      IItemHandler source = handler(lease.source.target());
      var order = core.orders.find(lease.order).orElse(null);
      if (source == null || order == null || lease.source.slot() >= source.getSlots()) {
        release(core, lease);
        return;
      }
      int count = Math.min(lease.quantity(), golem.hands().room(lease.source.template()));
      ItemStack preview = source.extractItem(lease.source.slot(), count, true);
      if (preview.isEmpty()
          || !ItemStack.isSameItemSameComponents(preview, lease.source.template())) {
        release(core, lease);
        return;
      }
      ItemStack actual =
          source.extractItem(lease.source.slot(), Math.min(count, preview.getCount()), false);
      if (actual.isEmpty()) {
        release(core, lease);
        return;
      }
      if (!ItemStack.isSameItemSameComponents(actual, lease.source.template())) {
        keepOrDrop(ItemHandlerHelper.insertItemStacked(source, actual, false));
        release(core, lease);
        return;
      }
      ItemStack original = actual.copy();
      int taken = actual.getCount();
      ItemStack rejected = golem.hands().hold(actual);
      int held = taken - rejected.getCount();
      if (!rejected.isEmpty())
        keepOrDrop(ItemHandlerHelper.insertItemStacked(source, rejected, false));
      core.orders.collected(lease.id, golem.getUUID(), original, held);
      core.orders.releaseUncollected(lease.id, golem.getUUID());
      core.changed();
      golem.swingArm();
      return;
    }
    var order = core.orders.find(lease.order).orElse(null);
    boolean recover = !active || lease.returnAmount() > 0 || order == null;
    int amount =
        recover
            ? (active ? lease.returnAmount() : lease.carried())
            : lease.carried() - lease.returnAmount();
    if (amount <= 0) return;
    LoadedTargets.Target destination = recover ? lease.source.target() : deliveryAt(core, order);
    if (destination == null
        || !level.dimension().equals(destination.dimension())
        || !level.hasChunkAt(destination.pos())) return;
    if (!recover && !validDestination(core, order)) return;
    if (!arrived(destination.pos())) return;
    ItemStack actual = golem.hands().release(lease.source.template().copyWithCount(amount));
    if (actual.isEmpty()) return;
    ItemStack evidence = actual.copy();
    ItemStack remainder;
    if (recover) {
      IItemHandler source = handler(destination);
      remainder =
          source == null ? actual : ItemHandlerHelper.insertItemStacked(source, actual, false);
      int returned = evidence.getCount() - remainder.getCount();
      if (returned > 0) core.orders.returned(lease.id, golem.getUUID(), evidence, returned);
      // A removed/full source cannot trap a cancelled order forever. Dropping is physical recovery.
      if (!remainder.isEmpty()) {
        int count = remainder.getCount();
        golem.spawnAtLocation(remainder);
        core.orders.returned(lease.id, golem.getUUID(), evidence, count);
      }
    } else {
      var be = level.getBlockEntity(destination.pos());
      if (be instanceof MirrorModule.MirrorEntity mirror && mirror.dispersing()) {
        remainder =
            mirror.accept(
                new MirrorModule.DeliveryToken(lease.id, golem.getUUID(), order.owner),
                actual,
                false);
      } else {
        IItemHandler output = handler(destination);
        remainder =
            output != null
                ? ItemHandlerHelper.insertItemStacked(output, actual, false)
                : MirrorRoutes.deliver(level.getServer(), destination, actual, false);
        int delivered = evidence.getCount() - remainder.getCount();
        if (delivered > 0)
          core.orders.deliveredByWorker(lease.id, golem.getUUID(), evidence, delivered);
      }
      keepOrDrop(remainder);
    }
    core.changed();
    golem.swingArm();
  }

  // Core placement has a stable block-entity identity, also used to reject replacement custody.
  static UUID cIdentity(InventariumModule.CoreEntity core) {
    return core.identity;
  }

  private void release(InventariumModule.CoreEntity core, Requisitions.Lease lease) {
    core.orders.releaseUncollected(lease.id, golem.getUUID());
    core.changed();
  }

  private boolean permitted(InventariumModule.CoreEntity core, LoadedTargets.Target at) {
    return LoadedTargets.local(golem.level(), core.getBlockPos(), at, core.linkRange() + 16)
        && core.index.sources().stream().anyMatch(s -> s.target().equals(at));
  }

  private boolean validDestination(InventariumModule.CoreEntity core, Requisitions.Order order) {
    if (!Objects.equals(order.owner, golem.getOwnerUUID())) return false;
    var be = core.device(order.tome);
    return be instanceof InventariumModule.TomeEntity tome
        && tome.core() == core
        && tome.operational()
        && tome.destination != null
        && tome.destination.equals(order.destination)
        && LoadedTargets.local(tome.getLevel(), tome.getBlockPos(), tome.destination, 16);
  }

  private LoadedTargets.Target deliveryAt(
      InventariumModule.CoreEntity core, Requisitions.Order order) {
    for (var memory : InventariumModule.memories(golem.level(), core.getBlockPos()))
      if (memory.device != null
          && core.device(memory.device) instanceof MirrorModule.MirrorEntity mirror
          && mirror.dispersing()
          && mirror.core() == core)
        return new LoadedTargets.Target(
            golem.level().dimension(), mirror.getBlockPos(), Direction.UP);
    return order.destination;
  }

  private IItemHandler handler(LoadedTargets.Target at) {
    if (!LoadedTargets.local(golem.level(), golem.blockPosition(), at, 64)) return null;
    return golem.level().getCapability(Capabilities.ItemHandler.BLOCK, at.pos(), at.side());
  }

  private boolean arrived(BlockPos pos) {
    if (GolemModule.withinWorkReach(golem, pos)) {
      golem.getNavigation().stop();
      return true;
    }
    if (golem.tickCount % 5 == 0 || golem.getNavigation().isDone()) {
      net.minecraft.world.level.pathfinder.Path best = null;
      double distance = Double.MAX_VALUE;
      for (Direction d : Direction.Plane.HORIZONTAL) {
        BlockPos p = pos.relative(d);
        if (golem.level().hasChunkAt(p)
            && golem.level().getBlockState(p).getCollisionShape(golem.level(), p).isEmpty()
            && golem
                .level()
                .getBlockState(p.above())
                .getCollisionShape(golem.level(), p.above())
                .isEmpty()) {
          var path = golem.getNavigation().createPath(p, 0);
          double next = golem.distanceToSqr(p.getCenter());
          if (path != null && path.canReach() && next < distance) {
            distance = next;
            best = path;
          }
        }
      }
      // Accuracy zero reaches the standing node instead of stopping one node before it.
      if (best != null) golem.getNavigation().moveTo(best, golem.getGolemMoveSpeed());
    }
    return false;
  }

  private void approachHome(BlockPos p) {
    if (golem.distanceToSqr(p.getCenter()) > 9) arrived(p);
  }

  private boolean emptyHands() {
    return golem.hands().contents().stream().allMatch(ItemStack::isEmpty);
  }

  private void keepOrDrop(ItemStack remainder) {
    if (!remainder.isEmpty()) {
      ItemStack left = golem.hands().hold(remainder);
      if (!left.isEmpty()) golem.spawnAtLocation(left);
    }
  }

  private void dropHands() {
    for (ItemStack s : golem.hands().contents().stream().map(ItemStack::copy).toList())
      if (!s.isEmpty()) golem.spawnAtLocation(golem.hands().release(s));
  }

  private void benchWork(GolemWorkbench.BenchEntity bench) {
    if (!bench.compatible(golem) || !arrived(bench.getBlockPos())) return;
    bench.work(golem);
  }

  static void releaseOnDeath(EntityThaumaturgeGolem g) {
    CompoundTag tag = g.getPersistentData().getCompound(GolemModule.CUSTODY);
    var at = LoadedTargets.Target.load(tag).orElse(null);
    var be = at == null ? null : LoadedTargets.blockEntity(g.getServer(), at).orElse(null);
    if (be instanceof InventariumModule.CoreEntity core) {
      core.orders.workerDied(g.getUUID());
      core.changed();
    } else if (at != null && tag.hasUUID("core"))
      LinkRemovals.get(g.getServer()).deadWorker(tag.getUUID("core"), g.getUUID(), at);
    g.getPersistentData().remove(GolemModule.CUSTODY);
  }

  static void dropCrossDimensionCustody(EntityThaumaturgeGolem g) {
    new GolemWorkGoal(g).dropHands();
    releaseOnDeath(g);
  }
}
