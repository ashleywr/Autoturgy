/* Validates both loaded ends of a real host mirror pair before any host call.
 * The host direct method queues output locally, so injection belongs to the paired end. */
package com.sanhiruzu.autoturgy.transport;

import com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirror;
import com.sanhiruzu.autoturgy.common.LoadedTargets;
import java.util.Optional;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

public final class MirrorRoutes {
  public record Route(BlockEntityMirror entrance, BlockEntityMirror exit) {}

  public static Optional<Route> resolve(MinecraftServer server, LoadedTargets.Target target) {
    if (target == null) return Optional.empty();
    var be = LoadedTargets.blockEntity(server, target).orElse(null);
    if (!(be instanceof BlockEntityMirror entrance)
        || entrance.isRemoved()
        || !entrance.linked
        || entrance.link == null) return Optional.empty();
    var endpoint =
        new LoadedTargets.Target(entrance.link.dimension(), entrance.link.pos(), Direction.UP);
    var other = LoadedTargets.blockEntity(server, endpoint).orElse(null);
    if (!(other instanceof BlockEntityMirror exit)
        || exit == entrance
        || exit.isRemoved()
        || !exit.linked
        || exit.link == null) return Optional.empty();
    if (!exit.link.dimension().equals(target.dimension()) || !exit.link.pos().equals(target.pos()))
      return Optional.empty();
    return Optional.of(new Route(entrance, exit));
  }

  public static ItemStack insert(
      MinecraftServer server, LoadedTargets.Target target, ItemStack stack, boolean simulate) {
    if (stack.isEmpty()) return ItemStack.EMPTY;
    Route route = resolve(server, target).orElse(null);
    if (route == null) return stack.copy();
    if (simulate) return ItemStack.EMPTY;
    return route.exit().transportDirect(stack.copy()) ? ItemStack.EMPTY : stack.copy();
  }

  public static ItemStack deliver(
      MinecraftServer server, LoadedTargets.Target destination, ItemStack stack, boolean simulate) {
    if (destination == null || stack.isEmpty()) return stack.copy();
    var be = LoadedTargets.blockEntity(server, destination).orElse(null);
    if (be instanceof BlockEntityMirror mirror)
      return simulate || mirror.transportDirect(stack.copy()) ? ItemStack.EMPTY : stack.copy();
    if (be instanceof MirrorModule.MirrorEntity adapter && !adapter.dispersing())
      return adapter.input.insertItem(0, stack, simulate);
    return stack.copy();
  }

  public static ItemStack input(
      MinecraftServer server, LoadedTargets.Target entrance, ItemStack stack, boolean simulate) {
    if (entrance == null) return stack.copy();
    var be = LoadedTargets.blockEntity(server, entrance).orElse(null);
    if (be instanceof MirrorModule.MirrorEntity adapter && !adapter.dispersing())
      return adapter.input.insertItem(0, stack, simulate);
    return insert(server, entrance, stack, simulate);
  }

  private MirrorRoutes() {}
}
