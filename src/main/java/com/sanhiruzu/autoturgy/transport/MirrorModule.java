/* Mirror adapters transport real stacks through independently validated host routes.
 * Dispersal accepts only earmarked outstanding orders; hand deposits retain configured reserves. */
package com.sanhiruzu.autoturgy.transport;

import com.sanhiruzu.autoturgy.*;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.inventarium.*;
import com.sanhiruzu.autoturgy.sensing.SensingModule;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.*;

public final class MirrorModule {
  public static DeferredBlock<MirrorBlock> MELDING, DISPERSING;
  public static DeferredItem<HandMirrorItem> HAND;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MirrorEntity>> TYPE;

  public static void register(IEventBus bus) {
    MELDING = Content.block("melding_mirror", () -> new MirrorBlock(false));
    DISPERSING = Content.block("dispersing_mirror", () -> new MirrorBlock(true));
    HAND = Content.item("hungry_hand_mirror", HandMirrorItem::new);
    TYPE =
        Content.ENTITIES.register(
            "mirror_adapter",
            () ->
                BlockEntityType.Builder.of(MirrorEntity::new, MELDING.get(), DISPERSING.get())
                    .build(null));
    bus.addListener(MirrorModule::capabilities);
  }

  private static void capabilities(RegisterCapabilitiesEvent e) {
    e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TYPE.get(), (be, side) -> be.input);
  }

  public static final class MirrorBlock extends DeviceBlock {
    public final boolean dispersing;

    MirrorBlock(boolean dispersing) {
      this.dispersing = dispersing;
    }

    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new MirrorEntity(p, s);
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit) {
      if (level.getBlockEntity(pos) instanceof MirrorEntity mirror) {
        if (stack.is(SensingModule.EYE.get()) || stack.is(SensingModule.FILTER.get()))
          return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide) {
          ItemStack remainder = mirror.input.insertItem(0, stack, false);
          player.setItemInHand(hand, remainder);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
      }
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
  }

  public record DeliveryToken(UUID lease, UUID worker, UUID owner) {}

  public static final class MirrorEntity extends DeviceEntity
      implements DeviceBlock.Ticking, InventariumModule.MemoryDevice {
    public UUID identity = UUID.randomUUID();

    public UUID memoryIdentity() {
      return identity;
    }

    public LoadedTargets.Target coreTarget() {
      return target;
    }

    public MirrorEntity(BlockPos p, BlockState s) {
      super(TYPE.get(), p, s);
    }

    public boolean dispersing() {
      return ((MirrorBlock) getBlockState().getBlock()).dispersing;
    }

    public String kind() {
      return dispersing() ? "dispersing_mirror" : "melding_mirror";
    }

    public int controlCount() {
      return 0;
    }

    public void configure(int i, int delta) {}

    public final IItemHandler input =
        new IItemHandler() {
          public int getSlots() {
            return 1;
          }

          public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
          }

          public int getSlotLimit(int slot) {
            return 64;
          }

          public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && StackFilters.accepts(filter, stack);
          }

          public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
          }

          public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || level == null || level.isClientSide || !isItemValid(slot, stack))
              return stack.copy();
            if (!dispersing())
              return MirrorRoutes.insert(level.getServer(), target, stack, simulate);
            return stack.copy();
          }
        };

    public InventariumModule.CoreEntity core() {
      if (target == null || level == null || level.isClientSide) return null;
      var be = LoadedTargets.blockEntity(level.getServer(), target).orElse(null);
      return be instanceof InventariumModule.CoreEntity core
              && core.memory(
                  identity,
                  new LoadedTargets.Target(level.dimension(), worldPosition, Direction.UP))
          ? core
          : null;
    }

    public ItemStack accept(DeliveryToken token, ItemStack actual, boolean simulate) {
      InventariumModule.CoreEntity core = core();
      if (core == null || !StackFilters.accepts(filter, actual)) return actual.copy();
      Requisitions.Lease lease =
          core.orders.leases().stream()
              .filter(l -> l.id.equals(token.lease()) && l.worker.equals(token.worker()))
              .findFirst()
              .orElse(null);
      Requisitions.Order order = lease == null ? null : core.orders.find(lease.order).orElse(null);
      if (order == null
          || !order.owner.equals(token.owner())
          || !ItemStack.isSameItemSameComponents(order.template(), actual)) return actual.copy();
      var device = core.device(order.tome);
      if (!(device instanceof InventariumModule.TomeEntity tome)
          || tome.core() != core
          || !tome.operational()
          || tome.destination == null
          || !tome.destination.equals(order.destination)
          || !LoadedTargets.local(tome.getLevel(), tome.getBlockPos(), tome.destination, 16))
        return actual.copy();
      int quantity =
          Math.min(
              Math.min(actual.getCount(), order.remaining()),
              lease.carried() - lease.returnAmount());
      if (quantity <= 0) return actual.copy();
      ItemStack remainder =
          MirrorRoutes.deliver(
              level.getServer(), order.destination, actual.copyWithCount(quantity), simulate);
      int delivered = quantity - remainder.getCount();
      if (delivered > 0 && !simulate) {
        core.orders.deliveredByWorker(lease.id, token.worker(), actual, delivered);
        core.orders.reconcile(core.index);
        core.changed();
      }
      return actual.copyWithCount(actual.getCount() - delivered);
    }

    public void serverTick() {
      if (level.getGameTime() % 4 != 0) return;
      for (ItemEntity entity :
          level.getEntitiesOfClass(
              ItemEntity.class, new AABB(worldPosition).inflate(.15), e -> e.isAlive())) {
        ItemStack remainder = input.insertItem(0, entity.getItem(), false);
        if (remainder.isEmpty()) entity.discard();
        else entity.setItem(remainder);
      }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      tag.putUUID("identity", identity);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      identity = tag.hasUUID("identity") ? tag.getUUID("identity") : UUID.randomUUID();
    }
  }

  public static final class HandMirrorItem extends Item {
    HandMirrorItem() {
      super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
      if (context.getPlayer() == null) return InteractionResult.PASS;
      if (!context.getLevel().isClientSide) {
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = SensingModule.itemData(stack);
        if (context.getPlayer().isShiftKeyDown())
          tag.put(
              "route",
              new LoadedTargets.Target(
                      context.getLevel().dimension(),
                      context.getClickedPos(),
                      context.getClickedFace())
                  .save());
        else if (context.getLevel().getBlockEntity(context.getClickedPos())
            instanceof DeviceEntity device)
          tag.put("filter", StackFilters.save(device.filter, context.getLevel().registryAccess()));
        SensingModule.itemData(stack, tag);
      }
      return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
        Level level, Player player, InteractionHand hand) {
      ItemStack mirror = player.getItemInHand(hand);
      if (!level.isClientSide) {
        ItemStack other =
            player.getItemInHand(
                hand == InteractionHand.MAIN_HAND
                    ? InteractionHand.OFF_HAND
                    : InteractionHand.MAIN_HAND);
        if (other.is(SensingModule.FILTER.get())) {
          CompoundTag tag = SensingModule.itemData(mirror);
          if (MirrorRoutes.resolve(
                  level.getServer(),
                  LoadedTargets.Target.load(tag.getCompound("route")).orElse(null))
              .isPresent()) {
            tag.put(player.isShiftKeyDown() ? "filter2" : "filter", SensingModule.itemData(other));
            SensingModule.itemData(mirror, tag);
          }
        } else if (player.isShiftKeyDown()) {
          CompoundTag tag = SensingModule.itemData(mirror);
          tag.putBoolean("automatic", !tag.getBoolean("automatic"));
          SensingModule.itemData(mirror, tag);
          player.displayClientMessage(
              Component.translatable("message.autoturgy.hand_auto", tag.getBoolean("automatic")),
              true);
        } else if (!other.isEmpty()) {
          CompoundTag tag = SensingModule.itemData(mirror);
          int reserve = 0;
          for (String key : List.of("filter", "filter2"))
            if (tag.contains(key)) {
              StackFilters.Filter f =
                  StackFilters.load(tag.getCompound(key), level.registryAccess());
              if (f.blacklist())
                reserve = Math.max(reserve, StackFilters.configuredQuantity(f, other));
            }
          int total = hand == InteractionHand.MAIN_HAND ? other.getCount() : 0;
          for (int i = 0; i < 36; i++)
            if (ItemStack.isSameItemSameComponents(other, player.getInventory().getItem(i)))
              total += player.getInventory().getItem(i).getCount();
          int amount = Math.min(other.getCount(), Math.max(0, total - reserve));
          ItemStack remainder =
              MirrorRoutes.insert(
                  level.getServer(),
                  LoadedTargets.Target.load(tag.getCompound("route")).orElse(null),
                  other.copyWithCount(amount),
                  false);
          other.shrink(amount - remainder.getCount());
        } else deposit(player, mirror);
      }
      return InteractionResultHolder.sidedSuccess(mirror, level.isClientSide);
    }

    public static int deposit(Player player, ItemStack mirror) {
      if (player.level().isClientSide) return 0;
      CompoundTag tag = SensingModule.itemData(mirror);
      LoadedTargets.Target target =
          LoadedTargets.Target.load(tag.getCompound("route")).orElse(null);
      if (MirrorRoutes.resolve(player.getServer(), target).isEmpty()) return 0;
      List<StackFilters.Filter> lists = new ArrayList<>();
      for (String key : List.of("filter", "filter2"))
        if (tag.contains(key))
          lists.add(StackFilters.load(tag.getCompound(key), player.level().registryAccess()));
      int moved = 0;
      for (int slot = 0; slot < 36; slot++) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack == mirror || stack.isEmpty() || stack.getItem() instanceof HandMirrorItem)
          continue;
        List<StackFilters.Filter> matching =
            lists.stream()
                .filter(
                    f ->
                        f.samples().stream()
                            .anyMatch(s -> StackFilters.matches(f.options(), s.template(), stack)))
                .toList();
        if (matching.isEmpty()) continue;
        int available = 0;
        for (int i = 0; i < 36; i++) {
          ItemStack s = player.getInventory().getItem(i);
          if (ItemStack.isSameItemSameComponents(stack, s)) available += s.getCount();
        }
        int reserve =
            matching.stream()
                .filter(StackFilters.Filter::blacklist)
                .mapToInt(f -> StackFilters.configuredQuantity(f, stack))
                .max()
                .orElse(0);
        int amount = Math.min(stack.getCount(), Math.max(0, available - reserve));
        if (amount <= 0) continue;
        ItemStack remainder =
            MirrorRoutes.insert(player.getServer(), target, stack.copyWithCount(amount), false);
        int sent = amount - remainder.getCount();
        stack.shrink(sent);
        moved += sent;
      }
      player.getInventory().setChanged();
      return moved;
    }

    @Override
    public void inventoryTick(
        ItemStack stack,
        Level level,
        net.minecraft.world.entity.Entity entity,
        int slot,
        boolean selected) {
      if (!level.isClientSide
          && entity instanceof Player player
          && level.getGameTime() % 20 == 0
          && SensingModule.itemData(stack).getBoolean("automatic")) deposit(player, stack);
    }

    @Override
    public void appendHoverText(
        ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
      lines.add(Component.translatable("tooltip.autoturgy.hand_mirror"));
    }
  }

  private MirrorModule() {}
}
