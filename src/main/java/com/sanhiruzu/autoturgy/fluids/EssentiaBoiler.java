/* Paid whole-bucket distillation using the installed host's actual aspect index.
 * Pending output is physical paid essentia, persisted until an alembic accepts it. */
package com.sanhiruzu.autoturgy.fluids;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.essentia.*;
import com.leclowndu93150.thaumaturge.content.essentia.BellowsHelper;
import com.sanhiruzu.autoturgy.Content;
import com.sanhiruzu.autoturgy.common.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.*;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.*;

public final class EssentiaBoiler {
  public static DeferredBlock<BoilerBlock> BLOCK;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerEntity>> TYPE;

  public static void register(IEventBus bus) {
    BLOCK = Content.block("alchemical_boiler", BoilerBlock::new);
    TYPE =
        Content.ENTITIES.register(
            "alchemical_boiler",
            () -> BlockEntityType.Builder.of(BoilerEntity::new, BLOCK.get()).build(null));
    bus.addListener(EssentiaBoiler::capabilities);
  }

  static void capabilities(RegisterCapabilitiesEvent e) {
    e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TYPE.get(), (be, side) -> be.tank);
    e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TYPE.get(), (be, side) -> be.fuel);
  }

  public static AspectList yield(FluidStack fluid) {
    if (fluid.isEmpty()) return AspectList.EMPTY;
    Item full = fluid.getFluid().getBucket();
    if (full == Items.AIR) return AspectList.EMPTY;
    if (fluid.is(FluidModule.MUSHROOM.source.get())) return servings(Items.MUSHROOM_STEW);
    if (fluid.is(FluidModule.VISHROOM.source.get()))
      return servings(FluidModule.VISHROOM.bowl.get());
    AspectList list = AspectIndexAccess.of(new ItemStack(full));
    AspectList empty = AspectIndexAccess.of(new ItemStack(Items.BUCKET));
    AspectList net = AspectList.EMPTY;
    for (AspectInstance a : list.entries()) {
      int amount = Math.max(0, a.amount() - empty.amountOf(a.aspect()));
      if (amount > 0) net = net.add(a.aspect(), amount);
    }
    return net;
  }

  private static AspectList servings(Item food) {
    AspectList full = AspectIndexAccess.of(food),
        empty = AspectIndexAccess.of(Items.BOWL),
        net = AspectList.EMPTY;
    for (AspectInstance a : full.entries()) {
      int amount = Math.max(0, a.amount() - empty.amountOf(a.aspect()));
      if (amount > 0) net = net.add(a.aspect(), amount * 4);
    }
    return net;
  }

  public static final class BoilerBlock extends DeviceBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new BoilerEntity(p, s);
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState s,
        Level l,
        BlockPos p,
        Player player,
        InteractionHand hand,
        BlockHitResult hit) {
      if (l.getBlockEntity(p) instanceof BoilerEntity be) {
        if (FluidModule.serve(be.tank, stack, l, player, hand)
            || FluidUtil.interactWithFluidHandler(player, hand, l, p, hit.getDirection()))
          return ItemInteractionResult.sidedSuccess(l.isClientSide);
        if (stack.getBurnTime(null) > 0) {
          if (!l.isClientSide) {
            ItemStack left = be.fuel.insertItem(0, stack.copyWithCount(1), false);
            if (left.isEmpty() && !player.isCreative()) stack.shrink(1);
          }
          return ItemInteractionResult.sidedSuccess(l.isClientSide);
        }
      }
      return super.useItemOn(stack, s, l, p, player, hand, hit);
    }

    @Override
    protected void onRemove(BlockState s, Level l, BlockPos p, BlockState other, boolean moving) {
      if (!l.isClientSide
          && !s.is(other.getBlock())
          && l.getBlockEntity(p) instanceof BoilerEntity be) {
        net.minecraft.world.Containers.dropItemStack(
            l, p.getX() + .5, p.getY() + .5, p.getZ() + .5, be.fuel.getStackInSlot(0).copy());
      }
      super.onRemove(s, l, p, other, moving);
    }
  }

  public static final class BoilerEntity extends DeviceEntity implements DeviceBlock.Ticking {
    public final FluidTank tank =
        new FluidTank(16000) {
          @Override
          protected void onContentsChanged() {
            changed();
          }
        };
    public final ItemStackHandler fuel =
        new ItemStackHandler(1) {
          @Override
          public boolean isItemValid(int slot, ItemStack s) {
            return s.getBurnTime(null) > 0;
          }

          @Override
          protected void onContentsChanged(int slot) {
            changed();
          }
        };
    public AspectList pending = AspectList.EMPTY;
    public int burn, work;

    public BoilerEntity(BlockPos p, BlockState s) {
      super(TYPE.get(), p, s);
    }

    public String kind() {
      return "alchemical_boiler";
    }

    public int controlCount() {
      return 4;
    }

    public void configure(int i, int delta) {}

    public void serverTick() {
      if (!pending.isEmpty()) {
        flush();
        display();
        return;
      }
      AspectList result = EssentiaBoiler.yield(tank.getFluid());
      if (tank.getFluidAmount() < 1000 || result.isEmpty() || !canOutput(result)) {
        work = 0;
        display();
        return;
      }
      if (burn <= 0) {
        ItemStack stack = fuel.getStackInSlot(0);
        int time = stack.getBurnTime(null);
        if (time <= 0) {
          display();
          return;
        }
        ItemStack used = fuel.extractItem(0, 1, false);
        burn = time;
        ItemStack remainder = used.getCraftingRemainingItem();
        if (!remainder.isEmpty()) {
          ItemStack left = fuel.insertItem(0, remainder, false);
          if (!left.isEmpty())
            net.minecraft.world.Containers.dropItemStack(
                level,
                worldPosition.getX() + .5,
                worldPosition.getY() + .5,
                worldPosition.getZ() + .5,
                left);
        }
      }
      burn--;
      int speed =
          1
              + BellowsHelper.countBellows(
                  level,
                  worldPosition,
                  Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new),
                  4);
      if (++work * speed >= 100) {
        tank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        pending = result;
        work = 0;
        flush();
      }
      display();
      setChanged();
    }

    private List<com.leclowndu93150.thaumaturge.content.essentia.smeltery.BlockEntityAlembic>
        outputs() {
      List<com.leclowndu93150.thaumaturge.content.essentia.smeltery.BlockEntityAlembic> list =
          new ArrayList<>();
      for (int i = 1; i <= 5; i++) {
        BlockPos p = worldPosition.above(i);
        if (!level.hasChunkAt(p)
            || !(level.getBlockEntity(p)
                instanceof
                com.leclowndu93150.thaumaturge.content.essentia.smeltery.BlockEntityAlembic
                        alembic)) break;
        list.add(alembic);
      }
      return list;
    }

    private int room(
        com.leclowndu93150.thaumaturge.content.essentia.smeltery.BlockEntityAlembic out,
        Holder<IAspect> a) {
      var key = a.unwrapKey().orElse(null);
      return out.accepts(a) && (out.amount() == 0 || Objects.equals(out.aspectKey(), key))
          ? Math.max(0, out.capacity() - out.amount())
          : 0;
    }

    public boolean canOutput(AspectList result) {
      var out = outputs();
      Set<com.leclowndu93150.thaumaturge.content.essentia.smeltery.BlockEntityAlembic> assigned =
          Collections.newSetFromMap(new IdentityHashMap<>());
      for (AspectInstance a : result.entries()) {
        int left = a.amount();
        for (var store : out) {
          if (assigned.contains(store)) continue;
          int room = Math.min(left, room(store, a.aspect()));
          if (room > 0) {
            assigned.add(store);
            left -= room;
          }
          if (left <= 0) break;
        }
        if (left > 0) return false;
      }
      return true;
    }

    public void flush() {
      for (AspectInstance a : List.copyOf(pending.entries())) {
        int left = a.amount();
        for (var out : outputs()) {
          int offered = Math.min(left, room(out, a.aspect()));
          if (offered <= 0) continue;
          int moved = offered - out.fill(a.aspect(), offered);
          if (moved > 0) {
            pending = pending.remove(a.aspect(), moved);
            left -= moved;
          }
          if (left <= 0) break;
        }
      }
      setChanged();
    }

    private void display() {
      settings[0] = tank.getFluidAmount();
      settings[1] = burn;
      settings[2] = work;
      settings[3] = pending.totalAmount();
      progress = Math.clamp(work * 15 / 100, 0, 15);
      changed();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      tag.put("fluid", tank.writeToNBT(lookup, new CompoundTag()));
      tag.put("fuel", fuel.serializeNBT(lookup));
      tag.putInt("burn", burn);
      tag.putInt("work", work);
      tag.put(
          "pending",
          AspectList.CODEC
              .encodeStart(
                  lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), pending)
              .getOrThrow());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      tank.readFromNBT(lookup, tag.getCompound("fluid"));
      fuel.deserializeNBT(lookup, tag.getCompound("fuel"));
      burn = Math.max(0, tag.getInt("burn"));
      work = Math.clamp(tag.getInt("work"), 0, 99);
      pending =
          AspectList.CODEC
              .parse(
                  lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                  tag.get("pending"))
              .result()
              .orElse(AspectList.EMPTY);
    }
  }

  private EssentiaBoiler() {}
}
