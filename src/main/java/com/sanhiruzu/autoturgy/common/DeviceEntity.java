/* Persistent controls and loaded-only links shared by small devices.
 * Subclasses own ticking and semantics; this base owns validation and synchronization. */
package com.sanhiruzu.autoturgy.common;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

public abstract class DeviceEntity extends BlockEntity implements MenuProvider {
  protected int output;
  protected int progress;
  public final int[] settings = new int[12];
  public LoadedTargets.Target target;
  public StackFilters.Filter filter = StackFilters.Filter.all();
  public String entityType = "";
  public UUID playerIdentity;
  public final ContainerData data =
      new ContainerData() {
        public int get(int i) {
          return i == 0 ? output : i == 1 ? progress : settings[i - 2];
        }

        public void set(int i, int v) {
          if (i == 0) output = v;
          else if (i == 1) progress = v;
          else settings[i - 2] = v;
        }

        public int getCount() {
          return 14;
        }
      };

  protected DeviceEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }

  public abstract String kind();

  public abstract int controlCount();

  public abstract void configure(int index, int delta);

  public void activate() {}

  public void cancel() {}

  public int selectionControl() {
    return -1;
  }

  public String selectionKey() {
    return "";
  }

  public List<String> selectionOptions() {
    return List.of();
  }

  public void selectKey(String key) {}

  public int signal() {
    return Math.clamp(output, 0, 15);
  }

  public int internalSignal() {
    return output;
  }

  public int comparator() {
    return Math.clamp(progress, 0, 15);
  }

  protected void output(int value) {
    value = Math.clamp(value, 0, 255);
    if (output != value) {
      output = value;
      setChanged();
      level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }
  }

  public void changed() {
    setChanged();
    if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
  }

  public void bounded(int index, int delta, int low, int high) {
    settings[index] = Math.clamp(settings[index] + delta, low, high);
    changed();
  }

  public void cycle(int index, int delta, int choices) {
    settings[index] = Math.floorMod(settings[index] + delta, choices);
    changed();
  }

  public boolean usable(Player player) {
    return !isRemoved()
        && level != null
        && level.getBlockEntity(worldPosition) == this
        && player.distanceToSqr(
                worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5)
            <= 64;
  }

  @Override
  public Component getDisplayName() {
    return getBlockState().getBlock().getName();
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new ControlMenu(id, inventory, this);
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
    super.saveAdditional(tag, lookup);
    tag.putInt("output", output);
    tag.putInt("progress", progress);
    tag.putIntArray("settings", settings.clone());
    if (target != null) tag.put("target", target.save());
    tag.put("filter", StackFilters.save(filter, lookup));
    tag.putString("entityType", entityType);
    if (playerIdentity != null) tag.putUUID("playerIdentity", playerIdentity);
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
    super.loadAdditional(tag, lookup);
    output = Math.clamp(tag.getInt("output"), 0, 255);
    progress = Math.clamp(tag.getInt("progress"), 0, 15);
    int[] saved = tag.getIntArray("settings");
    System.arraycopy(saved, 0, settings, 0, Math.min(saved.length, settings.length));
    target = LoadedTargets.Target.load(tag.getCompound("target")).orElse(null);
    filter =
        tag.contains("filter")
            ? StackFilters.load(tag.getCompound("filter"), lookup)
            : StackFilters.Filter.all();
    entityType = tag.getString("entityType");
    playerIdentity = tag.hasUUID("playerIdentity") ? tag.getUUID("playerIdentity") : null;
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
    return saveWithoutMetadata(lookup);
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
