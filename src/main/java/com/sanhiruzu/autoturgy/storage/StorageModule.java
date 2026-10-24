/* Physical chests and attached maws retain real item ownership.
 * Automation sees sided, reserve-aware access; player slots remain ordinary storage. */
package com.sanhiruzu.autoturgy.storage;

import com.sanhiruzu.autoturgy.*;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.sensing.SensingModule;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.registries.*;

public final class StorageModule {
  public enum Kind {
    GREEDY,
    EAGER,
    TENACIOUS
  }

  public static final Map<Kind, DeferredBlock<ChestBlock>> CHESTS = new EnumMap<>(Kind.class);
  public static DeferredBlock<MawBlock> HUNGRY, FINICAL;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<ChestEntity>> CHEST_TYPE;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MawEntity>> MAW_TYPE;

  public static void register(IEventBus bus) {
    for (Kind kind : Kind.values())
      CHESTS.put(
          kind,
          Content.block(
              kind.name().toLowerCase(Locale.ROOT) + "_chest", () -> new ChestBlock(kind)));
    HUNGRY = Content.block("hungry_maw", () -> new MawBlock(false));
    FINICAL = Content.block("finical_maw", () -> new MawBlock(true));
    CHEST_TYPE =
        Content.ENTITIES.register(
            "physical_chest",
            () ->
                BlockEntityType.Builder.of(
                        ChestEntity::new,
                        CHESTS.values().stream().map(DeferredBlock::get).toArray(Block[]::new))
                    .build(null));
    MAW_TYPE =
        Content.ENTITIES.register(
            "maw",
            () ->
                BlockEntityType.Builder.of(MawEntity::new, HUNGRY.get(), FINICAL.get())
                    .build(null));
    bus.addListener(StorageModule::capabilities);
  }

  private static void capabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK,
        CHEST_TYPE.get(),
        (be, side) ->
            side == null || (be.accessMask() & 1 << side.ordinal()) != 0 ? be.automation : null);
  }

  public static final class ChestBlock extends DeviceBlock {
    public final Kind kind;

    ChestBlock(Kind kind) {
      this.kind = kind;
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new ChestEntity(pos, state);
    }

    @Override
    public void setPlacedBy(
        Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
      if (placer instanceof Player player
          && level.getBlockEntity(pos) instanceof ChestEntity chest) {
        chest.owner = player.getUUID();
        chest.changed();
      }
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide
          && level.getBlockEntity(pos) instanceof ChestEntity chest
          && player instanceof ServerPlayer server) {
        if (!player.isShiftKeyDown()) chest.trade(player);
        server.openMenu(
            new SimpleMenuProvider(
                (id, inv, p) -> ChestMenu.threeRows(id, inv, chest), chest.getDisplayName()));
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
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
      if (level.getBlockEntity(pos) instanceof ChestEntity chest) {
        if (stack.is(Items.STICK)) {
          if (player instanceof ServerPlayer server && chest.authorized(player))
            server.openMenu(chest, buf -> ControlMenu.writeOpening(buf, chest));
          return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(SensingModule.ENTITY_EYE.get())) {
          if (!level.isClientSide && chest.isOwner(player)) {
            CompoundTag tag = SensingModule.itemData(stack);
            if (tag.hasUUID("playerIdentity")) {
              UUID identity = tag.getUUID("playerIdentity");
              if (player.isShiftKeyDown()) chest.authorized.remove(identity);
              else if (chest.authorized.size() < 32) chest.authorized.add(identity);
              chest.changed();
            }
          }
          return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!chest.authorized(player))
          return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      }
      return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected void onRemove(
        BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
      if (!state.is(replacement.getBlock())
          && level.getBlockEntity(pos) instanceof ChestEntity chest) {
        Containers.dropContents(level, pos, chest);
        level.updateNeighbourForOutputSignal(pos, this);
      }
      super.onRemove(state, level, pos, replacement, moving);
    }
  }

  public static final class ChestEntity extends DeviceEntity
      implements WorldlyContainer, DeviceBlock.Ticking {
    public UUID owner;
    public final Set<UUID> authorized = new HashSet<>();
    public final ItemStackHandler items =
        new ItemStackHandler(27) {
          @Override
          protected void onContentsChanged(int slot) {
            changed();
          }
        };
    public final IItemHandler automation =
        new IItemHandler() {
          public int getSlots() {
            return items.getSlots();
          }

          public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot).copy();
          }

          public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
          }

          public boolean isItemValid(int slot, ItemStack stack) {
            return true;
          }

          public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return items.insertItem(slot, stack, simulate);
          }

          public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack stack = items.getStackInSlot(slot);
            if (chestKind() == Kind.TENACIOUS)
              amount = Math.min(amount, Math.max(0, totalVariant(stack) - 1));
            return items.extractItem(slot, amount, simulate);
          }
        };
    private final Map<Direction, List<Observed>> previous = new EnumMap<>(Direction.class);
    private final List<ShareCredit> shareCredits = new ArrayList<>();

    private static final class ShareCredit {
      final Direction side;
      final ItemStack template;
      int fraction;
      long pending;

      ShareCredit(Direction side, ItemStack template, int fraction) {
        this.side = side;
        this.template = template.copyWithCount(1);
        this.fraction = fraction;
      }
    }

    public ChestEntity(BlockPos pos, BlockState state) {
      super(CHEST_TYPE.get(), pos, state);
      settings[0] = 63;
      settings[2] = 50;
      settings[3] = 63;
    }

    public Kind chestKind() {
      return ((ChestBlock) getBlockState().getBlock()).kind;
    }

    public String kind() {
      return chestKind().name().toLowerCase(Locale.ROOT) + "_chest";
    }

    public int controlCount() {
      return chestKind() == Kind.GREEDY ? 8 : 0;
    }

    public void configure(int i, int delta) {
      if (i < 6) {
        settings[0] ^= 1 << i;
        changed();
        if (level != null) level.invalidateCapabilities(worldPosition);
      } else if (i == 6) {
        cycle(1, delta, 3);
        previous.clear();
        shareCredits.forEach(c -> c.pending = 0);
      } else if (i == 7) bounded(2, delta, 1, settings[1] == 0 ? 100 : 4096);
    }

    public boolean isOwner(Player player) {
      return owner != null && owner.equals(player.getUUID());
    }

    public boolean authorized(Player player) {
      return isOwner(player) || authorized.contains(player.getUUID());
    }

    @Override
    public boolean usable(Player player) {
      return super.usable(player) && authorized(player);
    }

    public int totalType(ItemStack template) {
      if (template.isEmpty()) return 0;
      int total = 0;
      for (int i = 0; i < 27; i++)
        if (ItemStack.isSameItem(template, items.getStackInSlot(i)))
          total += items.getStackInSlot(i).getCount();
      return total;
    }

    public int totalVariant(ItemStack template) {
      if (template.isEmpty()) return 0;
      int total = 0;
      for (int i = 0; i < 27; i++)
        if (ItemStack.isSameItemSameComponents(template, items.getStackInSlot(i)))
          total += items.getStackInSlot(i).getCount();
      return total;
    }

    public int accessMask() {
      return chestKind() == Kind.GREEDY ? settings[3] : settings[0];
    }

    public int[] getSlotsForFace(Direction side) {
      return (accessMask() & 1 << side.ordinal()) == 0
          ? new int[0]
          : java.util.stream.IntStream.range(0, 27).toArray();
    }

    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
      return (accessMask() & 1 << side.ordinal()) != 0;
    }

    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
      return (accessMask() & 1 << side.ordinal()) != 0
          && (chestKind() != Kind.TENACIOUS || totalVariant(stack) > 1);
    }

    @Override
    public int comparator() {
      return AbstractContainerMenu.getRedstoneSignalFromContainer(this);
    }

    public void trade(Player player) {
      if (chestKind() != Kind.EAGER || !authorized(player) || player.isShiftKeyDown()) return;
      IItemHandler personal = new PlayerMainInvWrapper(player.getInventory());
      if (filter.blacklist()) {
        release(Transfers.moveItems(personal, items, filter, 2304).leftovers());
        StackFilters.Filter give = filter.converted();
        for (StackFilters.Sample sample : give.samples()) {
          int retained = StackFilters.configuredQuantity(filter, sample.template());
          int available = Math.max(0, totalVariant(sample.template()) - retained);
          release(
              Transfers.moveItems(
                      items,
                      personal,
                      new StackFilters.Filter(List.of(sample), false, give.options()),
                      available)
                  .leftovers());
        }
        return;
      }
      release(Transfers.moveItems(items, personal, filter.converted(), 2304).leftovers());
      List<ItemStack> handled = new ArrayList<>();
      for (StackFilters.Sample sample : filter.samples()) {
        if (handled.stream()
            .anyMatch(s -> StackFilters.matches(filter.options(), s, sample.template()))) continue;
        handled.add(sample.template());
        StackFilters.Filter single =
            new StackFilters.Filter(List.of(sample), false, filter.options());
        int have = 0;
        for (int slot = 0; slot < 36; slot++)
          if (StackFilters.matches(
              filter.options(), sample.template(), player.getInventory().getItem(slot)))
            have += player.getInventory().getItem(slot).getCount();
        int desired = StackFilters.configuredQuantity(filter, sample.template());
        Transfers.ItemResult result =
            have < desired
                ? Transfers.moveItems(items, personal, single, desired - have)
                : Transfers.moveItems(personal, items, single, have - desired);
        release(result.leftovers());
      }
    }

    public void serverTick() {
      if (chestKind() != Kind.GREEDY || level.getGameTime() % 10 != 0) return;
      for (Direction side : Direction.values()) {
        if ((settings[0] & 1 << side.ordinal()) == 0) {
          previous.remove(side);
          shareCredits.stream().filter(c -> c.side == side).forEach(c -> c.pending = 0);
          continue;
        }
        BlockPos pos = worldPosition.relative(side);
        if (!level.hasChunkAt(pos)
            || side == Direction.DOWN && level.getBlockEntity(pos) instanceof HopperBlockEntity
            || level.getBlockEntity(pos) instanceof ChestEntity c
                && c.chestKind() == Kind.GREEDY
                && (c.settings[0] & 1 << side.getOpposite().ordinal()) != 0) {
          previous.remove(side);
          shareCredits.stream().filter(c -> c.side == side).forEach(c -> c.pending = 0);
          continue;
        }
        IItemHandler source =
            level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side.getOpposite());
        if (source == null || source.getSlots() > 4096) {
          previous.remove(side);
          shareCredits.stream().filter(c -> c.side == side).forEach(c -> c.pending = 0);
          continue;
        }
        List<Observed> now = snapshot(source), before = previous.get(side);
        int budget = 1728;
        shareCredits.stream()
            .filter(c -> c.side == side && count(now, c.template) == 0)
            .forEach(c -> c.pending = 0);
        if (before != null)
          for (Observed observed : now) {
            ItemStack stack = observed.template;
            ShareCredit credit = credit(side, stack);
            if (credit == null) continue;
            long change = observed.quantity - count(before, stack);
            credit.pending = Math.max(0, credit.pending + Math.min(0, change));
            if (!StackFilters.accepts(filter, stack)) {
              credit.pending = 0;
              continue;
            }
            if (change > 0) credit.pending += settings[1] == 0 ? share(credit, change) : change;
            long limit =
                settings[1] == 0
                    ? observed.quantity
                    : settings[1] == 1
                        ? Math.max(0, settings[2] - totalVariant(stack))
                        : Math.max(0, observed.quantity - settings[2]);
            credit.pending = Math.min(credit.pending, limit);
            Transfers.ItemResult result =
                Transfers.moveItems(
                    source,
                    items,
                    new StackFilters.Filter(
                        List.of(new StackFilters.Sample(stack, 1)),
                        false,
                        new StackFilters.MatchOptions(true, true, false, false)),
                    (int) Math.min(budget, credit.pending));
            int departed =
                result.moved() + result.leftovers().stream().mapToInt(ItemStack::getCount).sum();
            credit.pending = Math.max(0, credit.pending - departed);
            budget -= departed;
            release(result.leftovers());
          }
        previous.put(side, snapshot(source));
      }
    }

    private ShareCredit credit(Direction side, ItemStack stack) {
      ShareCredit credit =
          shareCredits.stream()
              .filter(c -> c.side == side && ItemStack.isSameItemSameComponents(c.template, stack))
              .findFirst()
              .orElse(null);
      if (credit == null && shareCredits.size() < 1024) {
        credit = new ShareCredit(side, stack, 0);
        shareCredits.add(credit);
      }
      return credit;
    }

    private long share(ShareCredit credit, long fresh) {
      long numerator = fresh * Math.clamp(settings[2], 1, 100) + credit.fraction;
      credit.fraction = (int) (numerator % 100);
      setChanged();
      return numerator / 100;
    }

    private record Observed(ItemStack template, long quantity) {}

    private static List<Observed> snapshot(IItemHandler source) {
      List<Observed> result = new ArrayList<>();
      for (int i = 0; i < Math.min(4096, source.getSlots()); i++) {
        ItemStack stack = source.getStackInSlot(i);
        if (stack.isEmpty()) continue;
        int match = -1;
        for (int j = 0; j < result.size(); j++)
          if (ItemStack.isSameItemSameComponents(result.get(j).template, stack)) {
            match = j;
            break;
          }
        if (match < 0) result.add(new Observed(stack.copyWithCount(1), stack.getCount()));
        else {
          Observed old = result.get(match);
          result.set(match, new Observed(old.template, old.quantity + stack.getCount()));
        }
      }
      return result;
    }

    private static long count(List<Observed> list, ItemStack stack) {
      return list.stream()
          .filter(x -> ItemStack.isSameItemSameComponents(x.template, stack))
          .mapToLong(Observed::quantity)
          .sum();
    }

    private void release(List<ItemStack> leftovers) {
      for (ItemStack stack : leftovers)
        Containers.dropItemStack(
            level,
            worldPosition.getX() + .5,
            worldPosition.getY() + 1,
            worldPosition.getZ() + .5,
            stack);
    }

    public int getContainerSize() {
      return 27;
    }

    public boolean isEmpty() {
      for (int i = 0; i < 27; i++) if (!items.getStackInSlot(i).isEmpty()) return false;
      return true;
    }

    public ItemStack getItem(int slot) {
      return items.getStackInSlot(slot);
    }

    public ItemStack removeItem(int slot, int amount) {
      return items.extractItem(slot, amount, false);
    }

    public ItemStack removeItemNoUpdate(int slot) {
      return items.extractItem(slot, Integer.MAX_VALUE, false);
    }

    public void setItem(int slot, ItemStack stack) {
      items.setStackInSlot(slot, stack);
    }

    public boolean stillValid(Player player) {
      return super.usable(player);
    }

    public void clearContent() {
      for (int i = 0; i < 27; i++) items.setStackInSlot(i, ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      tag.put("items", items.serializeNBT(lookup));
      if (owner != null) tag.putUUID("owner", owner);
      ListTag users = new ListTag();
      authorized.forEach(id -> users.add(StringTag.valueOf(id.toString())));
      tag.put("authorized", users);
      ListTag credits = new ListTag();
      for (ShareCredit c : shareCredits) {
        CompoundTag row = new CompoundTag();
        row.putInt("side", c.side.ordinal());
        row.putInt("fraction", c.fraction);
        row.put("item", c.template.save(lookup));
        credits.add(row);
      }
      tag.put("shareCredits", credits);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      items.deserializeNBT(lookup, tag.getCompound("items"));
      owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
      authorized.clear();
      for (Tag user : tag.getList("authorized", Tag.TAG_STRING))
        try {
          if (authorized.size() < 32) authorized.add(UUID.fromString(user.getAsString()));
        } catch (IllegalArgumentException ignored) {
        }
      previous.clear();
      shareCredits.clear();
      ListTag credits = tag.getList("shareCredits", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(1024, credits.size()); i++) {
        CompoundTag row = credits.getCompound(i);
        ItemStack item = ItemStack.parseOptional(lookup, row.getCompound("item"));
        if (!item.isEmpty())
          shareCredits.add(
              new ShareCredit(
                  Direction.from3DDataValue(row.getInt("side")),
                  item,
                  Math.clamp(row.getInt("fraction"), 0, 99)));
      }
    }
  }

  public static final class MawBlock extends DeviceBlock {
    public static final DirectionProperty ATTACHED = DirectionProperty.create("attached");
    final boolean finical;

    MawBlock(boolean finical) {
      this.finical = finical;
      registerDefaultState(defaultBlockState().setValue(ATTACHED, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      super.createBlockStateDefinition(b);
      b.add(ATTACHED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
      return super.getStateForPlacement(c).setValue(ATTACHED, c.getClickedFace().getOpposite());
    }

    @Override
    protected VoxelShape getShape(
        BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return switch (state.getValue(ATTACHED)) {
        case DOWN -> Block.box(1, 0, 1, 15, 7, 15);
        case UP -> Block.box(1, 9, 1, 15, 16, 15);
        case NORTH -> Block.box(1, 1, 0, 15, 15, 7);
        case SOUTH -> Block.box(1, 1, 9, 15, 15, 16);
        case WEST -> Block.box(0, 1, 1, 7, 15, 15);
        case EAST -> Block.box(9, 1, 1, 16, 15, 15);
      };
    }

    @Override
    protected VoxelShape getCollisionShape(
        BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return Shapes.empty();
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new MawEntity(pos, state);
    }
  }

  public static final class MawEntity extends DeviceEntity implements DeviceBlock.Ticking {
    public MawEntity(BlockPos pos, BlockState state) {
      super(MAW_TYPE.get(), pos, state);
    }

    public String kind() {
      return ((MawBlock) getBlockState().getBlock()).finical ? "finical_maw" : "hungry_maw";
    }

    public int controlCount() {
      return 0;
    }

    public void configure(int i, int delta) {}

    public void serverTick() {
      if (level.getGameTime() % 4 != 0) return;
      Direction side = getBlockState().getValue(MawBlock.ATTACHED);
      BlockPos pos = worldPosition.relative(side);
      if (!level.hasChunkAt(pos)) return;
      IItemHandler destination =
          level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side.getOpposite());
      if (destination == null) return;
      for (ItemEntity entity :
          level.getEntitiesOfClass(
              ItemEntity.class, new AABB(worldPosition).inflate(.3), e -> e.isAlive())) {
        ItemStack stack = entity.getItem();
        if (((MawBlock) getBlockState().getBlock()).finical && !StackFilters.accepts(filter, stack))
          continue;
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, stack.copy(), false);
        if (remainder.isEmpty()) entity.discard();
        else entity.setItem(remainder);
      }
    }
  }

  private StorageModule() {}
}
