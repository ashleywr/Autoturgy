/* Core structures and paid scribes derive an index of existing loaded storage.
 * Memory permits persistent tome links; all costs use the installed host relay API. */
package com.sanhiruzu.autoturgy.inventarium;

import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.api.transaction.Transaction;
import com.sanhiruzu.autoturgy.*;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.sensing.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;

public final class InventariumModule {
  public static DeferredBlock<CoreBlock> CORE;
  public static DeferredBlock<ScribeBlock> SCRIBE;
  public static DeferredBlock<OculusBlock> OCULUS;
  public static DeferredBlock<MemoryBlock> MEMORY;
  public static DeferredBlock<Block> ENCHANTING_SHELF, ENSORCELLED_SHELF;
  public static DeferredBlock<TomeBlock> TOME_BLOCK;
  public static DeferredItem<BlockItem> TOME;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<CoreEntity>> CORE_TYPE;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<ScribeEntity>> SCRIBE_TYPE;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<OculusEntity>> OCULUS_TYPE;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MemoryEntity>> MEMORY_TYPE;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<TomeEntity>> TOME_TYPE;

  public static void register(IEventBus bus) {
    CORE = Content.block("inventarium_core", CoreBlock::new);
    SCRIBE = Content.block("inventarium_scribe", ScribeBlock::new);
    OCULUS = Content.block("inventarium_oculus", OculusBlock::new);
    MEMORY = Content.block("inventarium_memory", MemoryBlock::new);
    ENCHANTING_SHELF = Content.block("enchanting_bookshelf", () -> new ShelfBlock(4));
    ENSORCELLED_SHELF = Content.block("ensorcelled_bookshelf", () -> new ShelfBlock(8));
    TOME_BLOCK = Content.BLOCKS.register("requisition_tome", TomeBlock::new);
    TOME =
        Content.item(
            "requisition_tome",
            () -> new BlockItem(TOME_BLOCK.get(), new Item.Properties().stacksTo(1)));
    CORE_TYPE =
        Content.ENTITIES.register(
            "inventarium_core",
            () -> BlockEntityType.Builder.of(CoreEntity::new, CORE.get()).build(null));
    SCRIBE_TYPE =
        Content.ENTITIES.register(
            "inventarium_scribe",
            () -> BlockEntityType.Builder.of(ScribeEntity::new, SCRIBE.get()).build(null));
    OCULUS_TYPE =
        Content.ENTITIES.register(
            "inventarium_oculus",
            () -> BlockEntityType.Builder.of(OculusEntity::new, OCULUS.get()).build(null));
    MEMORY_TYPE =
        Content.ENTITIES.register(
            "inventarium_memory",
            () -> BlockEntityType.Builder.of(MemoryEntity::new, MEMORY.get()).build(null));
    TOME_TYPE =
        Content.ENTITIES.register(
            "requisition_tome",
            () -> BlockEntityType.Builder.of(TomeEntity::new, TOME_BLOCK.get()).build(null));
    InventariumMenu.register();
    InventariumPayload.register(bus);
    bus.addListener(
        (RegisterCapabilitiesEvent event) ->
            event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, TOME_TYPE.get(), (be, side) -> be.input));
    for (int i = 11; i <= 16; i++) GuideItem.PAGES.add("guide.autoturgy." + i);
  }

  public record Structure(boolean valid, int capacity, int clusters, boolean memory) {}

  public static boolean amber(Level level, BlockPos pos) {
    return block(level, pos, "thaumaturge:amber_block")
        || block(level, pos, "thaumaturge:amber_brick");
  }

  public static List<MemoryEntity> memories(Level level, BlockPos origin) {
    List<MemoryEntity> found = new ArrayList<>();
    for (BlockPos pos :
        BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, -2, 3))) {
      if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof MemoryEntity memory))
        continue;
      for (Direction d : Direction.values())
        if (amber(level, pos.relative(d))) {
          found.add(memory);
          break;
        }
    }
    return found;
  }

  public static final class ShelfBlock extends Block {
    private final float power;

    ShelfBlock(float power) {
      super(Properties.of().strength(2));
      this.power = power;
    }

    @Override
    public float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) {
      return power;
    }
  }

  public static boolean block(Level level, BlockPos pos, String id) {
    return level.hasChunkAt(pos)
        && BuiltInRegistries.BLOCK
            .getKey(level.getBlockState(pos).getBlock())
            .toString()
            .equals(id);
  }

  public static Structure structure(Level level, BlockPos origin) {
    int clusters = 0, capacity = 0, shelves = 0;
    for (BlockPos p : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3))) {
      if (!level.hasChunkAt(p)) return new Structure(false, 0, 0, false);
      Block b = level.getBlockState(p).getBlock();
      if (block(level, p, "thaumaturge:crystal_ordo")) clusters++;
      if (b == Blocks.BOOKSHELF) {
        capacity += 81;
        shelves++;
      } else if (b == ENCHANTING_SHELF.get()) {
        capacity += 216;
        shelves++;
      } else if (b == ENSORCELLED_SHELF.get()) {
        capacity += 486;
        shelves++;
      }
    }
    boolean base =
        block(level, origin.below(), "thaumaturge:crystal_ordo") && amber(level, origin.below(2));
    return new Structure(
        base && shelves >= 8,
        Math.min(capacity, 16384),
        clusters,
        !memories(level, origin).isEmpty());
  }

  public static boolean payOrdo(ServerLevel level, BlockPos pos, int amount) {
    try (Transaction tx = Transaction.openRoot()) {
      if (VisRelayHelper.drainCentivis(level, pos, TTAspects.ORDO, amount, tx) != amount)
        return false;
      tx.commit();
      return true;
    }
  }

  public static final class CoreBlock extends DeviceBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new CoreEntity(p, s);
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (player instanceof ServerPlayer server
          && level.getBlockEntity(pos) instanceof CoreEntity core) {
        core.refresh();
        server.openMenu(
            new SimpleMenuProvider(
                (id, inv, p) -> new InventariumMenu(id, inv, core, null, null),
                core.getDisplayName()),
            buf -> InventariumMenu.writeOpening(buf, false, false));
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
    }
  }

  public static final class CoreEntity extends DeviceEntity
      implements DeviceBlock.Ticking, TallyService.CountProvider {
    public final Requisitions orders = new Requisitions();
    public final Set<BlockPos> scribes = new LinkedHashSet<>();
    public InventoryIndex.Snapshot index = InventoryIndex.Snapshot.empty();
    public Structure structure = new Structure(false, 0, 0, false);
    public int memoryUsed, memoryMaximum;

    public int linkRange() {
      return 10 + 8 * structure(level, worldPosition).clusters();
    }

    public boolean admitScribe(BlockPos pos) {
      if (!LoadedTargets.withinRange(worldPosition, pos, linkRange())) return false;
      scribes.removeIf(
          p ->
              level.hasChunkAt(p)
                  && (!(level.getBlockEntity(p) instanceof ScribeEntity s) || !s.linked(this)));
      if (scribes.contains(pos)) return true;
      if (scribes.size() >= 1 + structure(level, worldPosition).clusters()) return false;
      scribes.add(pos.immutable());
      changed();
      return true;
    }

    public boolean memory(UUID device, LoadedTargets.Target location) {
      if (!structure(level, worldPosition).valid()
          || !LoadedTargets.local(level, worldPosition, location, linkRange())) return false;
      List<MemoryEntity> slots = memories(level, worldPosition);
      for (MemoryEntity slot : slots)
        if (device.equals(slot.device)
            && slot.target != null
            && slot.target.dimension().equals(level.dimension())
            && slot.target.pos().equals(worldPosition)
            && location.equals(slot.location)) return true;
      for (MemoryEntity slot : slots) {
        slot.releaseInvalid();
        if (slot.device == null) {
          slot.device = device;
          slot.location = location;
          slot.target = new LoadedTargets.Target(level.dimension(), worldPosition, Direction.UP);
          slot.changed();
          return true;
        }
      }
      return false;
    }

    public BlockEntity device(UUID id) {
      for (MemoryEntity slot : memories(level, worldPosition))
        if (id.equals(slot.device)
            && slot.target != null
            && slot.target.pos().equals(worldPosition)
            && slot.location != null)
          return LoadedTargets.blockEntity(level.getServer(), slot.location).orElse(null);
      return null;
    }

    public CoreEntity(BlockPos p, BlockState s) {
      super(CORE_TYPE.get(), p, s);
    }

    public String kind() {
      return "inventarium_core";
    }

    public int controlCount() {
      return 0;
    }

    public void configure(int i, int delta) {}

    public void serverTick() {
      if (level.getGameTime() % 20 == 0) refresh();
    }

    public void refresh() {
      if (!(level instanceof ServerLevel server)) return;
      structure = structure(level, worldPosition);
      List<MemoryEntity> memory = memories(level, worldPosition);
      memoryMaximum = memory.size();
      memoryUsed = 0;
      for (MemoryEntity slot : memory) {
        slot.releaseInvalid();
        if (slot.device != null
            && slot.target != null
            && slot.target.dimension().equals(level.dimension())
            && slot.target.pos().equals(worldPosition)) memoryUsed++;
      }
      List<LoadedTargets.Target> candidates = new ArrayList<>();
      int included = 0;
      if (structure.valid())
        for (BlockPos pos : List.copyOf(scribes)) {
          if (included >= 1 + structure.clusters()) break;
          if (!LoadedTargets.withinRange(worldPosition, pos, 10 + 8 * structure.clusters())
              || !level.hasChunkAt(pos)) continue;
          if (level.getBlockEntity(pos) instanceof ScribeEntity scribe && scribe.linked(this)) {
            included++;
            candidates.addAll(scribe.paidCandidates());
          }
        }
      index = InventoryIndex.scan(server, candidates, structure.valid() ? structure.capacity() : 0);
      orders.reconcile(index);
      progress =
          structure.valid()
              ? Math.min(15, index.slots() * 15 / Math.max(1, structure.capacity()))
              : 0;
      changed();
    }

    public long count(StackFilters.Filter filter, Set<BlockPos> visited, int depth) {
      if (depth >= 16) return 0;
      return settings[0] == 1 ? orders.demand(filter) : index.count(filter);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      tag.put("orders", orders.save(lookup));
      tag.putLongArray(
          "scribes", scribes.stream().limit(344).mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      orders.load(tag.getCompound("orders"), lookup);
      scribes.clear();
      for (long p : tag.getLongArray("scribes"))
        if (scribes.size() < 344) scribes.add(BlockPos.of(p));
      index = InventoryIndex.Snapshot.empty();
    }
  }

  public static final class ScribeBlock extends DeviceBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new ScribeEntity(p, s);
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (player.isShiftKeyDown()) return super.useWithoutItem(state, level, pos, player, hit);
      if (player instanceof ServerPlayer server
          && level.getBlockEntity(pos) instanceof ScribeEntity scribe
          && scribe.core() != null) {
        CoreEntity core = scribe.core();
        core.refresh();
        server.openMenu(
            new SimpleMenuProvider(
                (id, inv, p) -> new InventariumMenu(id, inv, core, null, scribe),
                scribe.getDisplayName()),
            buf -> InventariumMenu.writeOpening(buf, false, true));
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
      if (stack.is(Items.STICK)
          && player instanceof ServerPlayer server
          && level.getBlockEntity(pos) instanceof ScribeEntity scribe) {
        server.openMenu(scribe, buf -> ControlMenu.writeOpening(buf, scribe));
        return ItemInteractionResult.SUCCESS;
      }
      return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
  }

  public static final class ScribeEntity extends DeviceEntity implements DeviceBlock.Ticking {
    public String inventoryType = "";
    private long paidAt = Long.MIN_VALUE;
    private List<LoadedTargets.Target> cached = List.of();

    public ScribeEntity(BlockPos p, BlockState s) {
      super(SCRIBE_TYPE.get(), p, s);
    }

    public String kind() {
      return "inventarium_scribe";
    }

    public int controlCount() {
      return 1;
    }

    public void configure(int i, int d) {
      if (i == 0) cycle(0, d, 6);
    }

    public boolean linked(CoreEntity core) {
      return target != null
          && target.dimension().equals(level.dimension())
          && target.pos().equals(core.getBlockPos());
    }

    public void serverTick() {
      if (level.getGameTime() % 20 != 0
          || target == null
          || !LoadedTargets.local(level, worldPosition, target, 4096)) return;
      CoreEntity core = core();
      if (core != null) core.admitScribe(worldPosition);
    }

    public CoreEntity core() {
      return target != null
              && LoadedTargets.local(level, worldPosition, target, 4096)
              && level.getBlockEntity(target.pos()) instanceof CoreEntity core
              && LoadedTargets.withinRange(worldPosition, target.pos(), core.linkRange())
          ? core
          : null;
    }

    public List<LoadedTargets.Target> candidates() {
      if (!(level instanceof ServerLevel)) return List.of();
      OculusEntity oculus =
          level.hasChunkAt(worldPosition.above())
                  && level.getBlockEntity(worldPosition.above()) instanceof OculusEntity o
              ? o
              : null;
      int radius = 3;
      Set<String> types = new LinkedHashSet<>();
      if (oculus != null)
        for (LoadedTargets.Target eye : oculus.eyes.stream().limit(5).toList()) {
          if (!LoadedTargets.local(level, worldPosition, eye, 64)) continue;
          if (eye.pos().equals(worldPosition)) {
            radius++;
            continue;
          }
          BlockEntity be = level.getBlockEntity(eye.pos());
          if (be != null)
            types.add(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
        }
      if (inventoryType.isEmpty())
        for (int distance = 1; distance <= radius; distance++) {
          BlockPos behind =
              worldPosition.relative(
                  getBlockState().getValue(DeviceBlock.FACING).getOpposite(), distance);
          if (!level.hasChunkAt(behind)) break;
          BlockEntity be = level.getBlockEntity(behind);
          if (be != null
              && level.getCapability(
                      Capabilities.ItemHandler.BLOCK,
                      behind,
                      Direction.from3DDataValue(settings[0]))
                  != null) {
            inventoryType = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();
            changed();
            break;
          }
        }
      if (!inventoryType.isEmpty()) types.add(inventoryType);
      List<LoadedTargets.Target> found = new ArrayList<>();
      for (BlockPos pos :
          BlockPos.betweenClosed(
              worldPosition.offset(-radius, -radius, -radius),
              worldPosition.offset(radius, radius, radius))) {
        if (!level.hasChunkAt(pos) || level.getBlockEntity(pos) instanceof CoreEntity) continue;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) continue;
        String type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();
        Direction side = Direction.from3DDataValue(settings[0]);
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) == null) continue;
        if (types.contains(type)) found.add(new LoadedTargets.Target(level.dimension(), pos, side));
      }
      return found;
    }

    public List<LoadedTargets.Target> paidCandidates() {
      long now = level.getGameTime();
      if (paidAt == now) return cached;
      cached = List.of();
      paidAt = now;
      if (target == null
          || !LoadedTargets.local(level, worldPosition, target, 4096)
          || !(level.getBlockEntity(target.pos()) instanceof CoreEntity core)
          || !linked(core)
          || !LoadedTargets.withinRange(
              worldPosition, target.pos(), 10 + 8 * core.structure.clusters())) return cached;
      if (core.admitScribe(worldPosition) && payOrdo((ServerLevel) level, worldPosition, 1))
        cached = List.copyOf(candidates());
      return cached;
    }

    @Override
    public void cancel() {
      inventoryType = "";
      changed();
    }

    @Override
    public void activate() {
      cancel();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      tag.putString("inventoryType", inventoryType);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      inventoryType = tag.getString("inventoryType");
      paidAt = Long.MIN_VALUE;
      cached = List.of();
    }
  }

  public static final class OculusBlock extends DeviceBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new OculusEntity(p, s);
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
      if (stack.is(SensingModule.EYE.get())
          && level.getBlockEntity(pos) instanceof OculusEntity o) {
        if (!level.isClientSide)
          LoadedTargets.Target.load(SensingModule.itemData(stack))
              .ifPresent(
                  t -> {
                    if (o.eyes.size() < 5) {
                      o.eyes.add(t);
                      o.changed();
                    }
                  });
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
      }
      return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide
          && level.getBlockEntity(pos) instanceof OculusEntity o
          && !o.eyes.isEmpty()) {
        o.eyes.removeLast();
        o.changed();
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
    }
  }

  public static final class OculusEntity extends DeviceEntity {
    public final List<LoadedTargets.Target> eyes = new ArrayList<>();

    public OculusEntity(BlockPos p, BlockState s) {
      super(OCULUS_TYPE.get(), p, s);
    }

    public String kind() {
      return "inventarium_oculus";
    }

    public int controlCount() {
      return 0;
    }

    public void configure(int i, int d) {}

    @Override
    public void activate() {
      eyes.clear();
      changed();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      ListTag list = new ListTag();
      eyes.forEach(t -> list.add(t.save()));
      tag.put("eyes", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      eyes.clear();
      ListTag list = tag.getList("eyes", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(5, list.size()); i++)
        LoadedTargets.Target.load(list.getCompound(i)).ifPresent(eyes::add);
    }
  }

  public static final class MemoryBlock extends DeviceBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new MemoryEntity(p, s);
    }
  }

  public static final class MemoryEntity extends DeviceEntity {
    public UUID device;
    public LoadedTargets.Target location;

    public MemoryEntity(BlockPos p, BlockState s) {
      super(MEMORY_TYPE.get(), p, s);
    }

    public String kind() {
      return "inventarium_memory";
    }

    public int controlCount() {
      return 0;
    }

    public void configure(int i, int d) {}

    public void releaseInvalid() {
      if (device == null) return;
      if (target == null || location == null) {
        device = null;
        changed();
        return;
      }
      var core = LoadedTargets.level(level.getServer(), target);
      var deviceLevel = LoadedTargets.level(level.getServer(), location);
      if (core.isPresent() && !(core.get().getBlockEntity(target.pos()) instanceof CoreEntity)
          || deviceLevel.isPresent()
              && (!(deviceLevel.get().getBlockEntity(location.pos()) instanceof MemoryDevice d)
                  || !device.equals(d.memoryIdentity())
                  || d.coreTarget() == null
                  || !d.coreTarget().pos().equals(target.pos())
                  || !d.coreTarget().dimension().equals(target.dimension()))) {
        device = null;
        location = null;
        target = null;
        changed();
      }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      if (device != null) tag.putUUID("device", device);
      if (location != null) tag.put("location", location.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      device = tag.hasUUID("device") ? tag.getUUID("device") : null;
      location = LoadedTargets.Target.load(tag.getCompound("location")).orElse(null);
    }
  }

  public interface MemoryDevice {
    UUID memoryIdentity();

    LoadedTargets.Target coreTarget();
  }

  public static final class TomeBlock extends DeviceBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new TomeEntity(p, s);
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (player instanceof ServerPlayer server
          && level.getBlockEntity(pos) instanceof TomeEntity tome) {
        CoreEntity core = tome.core();
        if (core != null && tome.operational()) {
          core.refresh();
          server.openMenu(
              new SimpleMenuProvider(
                  (id, inv, p) -> new InventariumMenu(id, inv, core, tome, null),
                  tome.getDisplayName()),
              buf -> InventariumMenu.writeOpening(buf, true, false));
        } else
          player.displayClientMessage(
              Component.translatable("message.autoturgy.tome_unavailable"), true);
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
      if (level.getBlockEntity(pos) instanceof TomeEntity tome) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.is(SensingModule.EYE.get())) {
          if (!level.isClientSide)
            LoadedTargets.Target.load(SensingModule.itemData(stack))
                .ifPresent(
                    t -> {
                      if (LoadedTargets.blockEntity(level.getServer(), t).orElse(null)
                          instanceof CoreEntity) tome.target = t;
                      else if (LoadedTargets.local(level, pos, t, 16)) {
                        if (tome.settings[0] == 2) tome.destination = t;
                        else tome.inputRoute = t;
                      }
                      tome.changed();
                    });
          return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
          ItemStack remainder =
              net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(
                  tome.input, stack.copy(), false);
          player.setItemInHand(hand, remainder);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
      }
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(
        BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
      if (!state.is(replacement.getBlock())
          && level.getBlockEntity(pos) instanceof TomeEntity tome) {
        if (!level.isClientSide) {
          ItemStack items = tome.input.extractItem(0, Integer.MAX_VALUE, false);
          if (!items.isEmpty())
            Containers.dropItemStack(
                level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, items);
          CoreEntity core = tome.core();
          if (core != null) {
            for (Requisitions.Order order : core.orders.orders())
              if (order.tome.equals(tome.identity)) core.orders.cancel(order.id, order.owner);
            core.changed();
          }
        }
      }
      super.onRemove(state, level, pos, replacement, moving);
    }
  }

  public static final class TomeEntity extends DeviceEntity
      implements DeviceBlock.Ticking, TallyService.CountProvider, MemoryDevice {
    public UUID identity = UUID.randomUUID();
    public LoadedTargets.Target inputRoute, destination;
    private long lastPaid = Long.MIN_VALUE;
    public final net.neoforged.neoforge.items.ItemStackHandler input =
        new net.neoforged.neoforge.items.ItemStackHandler(1) {
          @Override
          protected void onContentsChanged(int slot) {
            changed();
          }
        };

    public TomeEntity(BlockPos p, BlockState s) {
      super(TOME_TYPE.get(), p, s);
      settings[0] = 1;
    }

    public String kind() {
      return "requisition_tome";
    }

    public int controlCount() {
      return 0;
    }

    public void configure(int i, int d) {}

    public UUID memoryIdentity() {
      return identity;
    }

    public LoadedTargets.Target coreTarget() {
      return target;
    }

    public CoreEntity core() {
      return target != null
              && LoadedTargets.local(level, worldPosition, target, 4096)
              && level.getBlockEntity(target.pos()) instanceof CoreEntity core
              && LoadedTargets.withinRange(worldPosition, target.pos(), core.linkRange())
          ? core
          : null;
    }

    public boolean operational() {
      CoreEntity core = core();
      if (core == null
          || !core.memory(
              identity, new LoadedTargets.Target(level.dimension(), worldPosition, Direction.UP)))
        return false;
      long now = level.getGameTime();
      if (lastPaid != Long.MIN_VALUE && now - lastPaid >= 0 && now - lastPaid < 20) return true;
      if (payOrdo((ServerLevel) level, worldPosition, 1)) {
        lastPaid = now;
        return true;
      }
      return false;
    }

    public void serverTick() {
      if (level.getGameTime() % 20 == 0 && operational() && !input.getStackInSlot(0).isEmpty()) {
        if (inputRoute == null || !LoadedTargets.local(level, worldPosition, inputRoute, 16))
          return;
        ItemStack actual = input.getStackInSlot(0);
        ItemStack remainder =
            com.sanhiruzu.autoturgy.transport.MirrorRoutes.input(
                level.getServer(), inputRoute, actual, false);
        input.setStackInSlot(0, remainder);
      }
    }

    public long count(StackFilters.Filter filter, Set<BlockPos> visited, int depth) {
      CoreEntity core = core();
      return core == null
          ? 0
          : core.orders.orders().stream()
              .filter(o -> o.tome.equals(identity) && StackFilters.accepts(filter, o.template()))
              .mapToLong(Requisitions.Order::remaining)
              .sum();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      tag.putUUID("identity", identity);
      tag.put("input", input.serializeNBT(lookup));
      if (inputRoute != null) tag.put("inputRoute", inputRoute.save());
      if (destination != null) tag.put("destination", destination.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      identity = tag.hasUUID("identity") ? tag.getUUID("identity") : UUID.randomUUID();
      input.deserializeNBT(lookup, tag.getCompound("input"));
      inputRoute = LoadedTargets.Target.load(tag.getCompound("inputRoute")).orElse(null);
      destination = LoadedTargets.Target.load(tag.getCompound("destination")).orElse(null);
      lastPaid = Long.MIN_VALUE;
    }
  }

  private InventariumModule() {}
}
