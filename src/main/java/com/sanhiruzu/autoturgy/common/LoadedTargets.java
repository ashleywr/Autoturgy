/* Resolves only already-loaded targets; reads must never cause chunk generation.
 * Eyes carry immutable dimension, position and queried-face coordinates. */
package com.sanhiruzu.autoturgy.common;

import java.util.Optional;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class LoadedTargets {
  public record Target(ResourceKey<Level> dimension, BlockPos pos, Direction side) {
    public Target {
      java.util.Objects.requireNonNull(dimension);
      java.util.Objects.requireNonNull(side);
      pos = java.util.Objects.requireNonNull(pos).immutable();
    }

    public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("dimension", dimension.location().toString());
      tag.putLong("position", pos.asLong());
      tag.putInt("side", side.ordinal());
      return tag;
    }

    public static Optional<Target> load(CompoundTag tag) {
      ResourceLocation id = ResourceLocation.tryParse(tag.getString("dimension"));
      if (id == null || !tag.contains("position")) return Optional.empty();
      return Optional.of(
          new Target(
              ResourceKey.create(Registries.DIMENSION, id),
              BlockPos.of(tag.getLong("position")),
              Direction.from3DDataValue(tag.getInt("side"))));
    }
  }

  public static Optional<ServerLevel> level(MinecraftServer server, Target target) {
    ServerLevel level = server.getLevel(target.dimension());
    return level != null && level.isInWorldBounds(target.pos()) && level.hasChunkAt(target.pos())
        ? Optional.of(level)
        : Optional.empty();
  }

  public static Optional<BlockEntity> blockEntity(MinecraftServer server, Target target) {
    return level(server, target).map(l -> l.getBlockEntity(target.pos()));
  }

  public static boolean withinRange(BlockPos a, BlockPos b, int range) {
    return a.distSqr(b) <= (double) range * range;
  }

  public static boolean local(Level level, BlockPos origin, Target target, int range) {
    return level.dimension().equals(target.dimension())
        && withinRange(origin, target.pos(), range)
        && level.hasChunkAt(target.pos());
  }

  private LoadedTargets() {}
}
