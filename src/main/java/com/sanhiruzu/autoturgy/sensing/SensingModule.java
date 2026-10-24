/* Bound eyes connect readers to loaded targets; entity eyes carry explicit identity.
 * Distinct readers own comparator, aspect, inventory and obstruction-volume semantics. */
package com.sanhiruzu.autoturgy.sensing;

import com.sanhiruzu.autoturgy.*;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.host.ThaumaturgeIntegration;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.*;

public final class SensingModule {
  public static DeferredItem<EyeItem> EYE, ENTITY_EYE;
  public static DeferredItem<FilterItem> FILTER;
  public static DeferredItem<Item> UNBOUND_LENS, YIELD_LENS;
  public static DeferredBlock<ReaderBlock> REMOTE, VIS, TALLY, SIGNUM;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<RemoteEntity>> REMOTE_TYPE;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<VisEntity>> VIS_TYPE;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<TallyEntity>> TALLY_TYPE;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SignumEntity>> SIGNUM_TYPE;

  public static void register() {
    EYE = Content.item("crystalline_eye", () -> new EyeItem(false));
    ENTITY_EYE = Content.item("entity_eye", () -> new EyeItem(true));
    FILTER = Content.item("runic_filter", FilterItem::new);
    UNBOUND_LENS = Content.item("unbound_lens", () -> new Item(new Item.Properties()));
    YIELD_LENS = Content.item("yield_lens", () -> new Item(new Item.Properties()));
    REMOTE = Content.block("remote_comparator", () -> new ReaderBlock(RemoteEntity::new));
    VIS = Content.block("vis_reader", () -> new ReaderBlock(VisEntity::new));
    TALLY = Content.block("tally_box", () -> new ReaderBlock(TallyEntity::new));
    SIGNUM = Content.block("signum_entia", () -> new ReaderBlock(SignumEntity::new));
    REMOTE_TYPE =
        Content.ENTITIES.register(
            "remote_comparator",
            () -> BlockEntityType.Builder.of(RemoteEntity::new, REMOTE.get()).build(null));
    VIS_TYPE =
        Content.ENTITIES.register(
            "vis_reader", () -> BlockEntityType.Builder.of(VisEntity::new, VIS.get()).build(null));
    TALLY_TYPE =
        Content.ENTITIES.register(
            "tally_box",
            () -> BlockEntityType.Builder.of(TallyEntity::new, TALLY.get()).build(null));
    SIGNUM_TYPE =
        Content.ENTITIES.register(
            "signum_entia",
            () -> BlockEntityType.Builder.of(SignumEntity::new, SIGNUM.get()).build(null));
    FilterMenu.register();
    GuideItem.register();
    NeoForge.EVENT_BUS.addListener(SensingModule::onEntityInteract);
  }

  private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
    if (!event.getItemStack().is(ENTITY_EYE.get())) return;
    if (!event.getLevel().isClientSide)
      ENTITY_EYE.get().bindEntity(event.getItemStack(), event.getTarget());
    event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    event.setCanceled(true);
  }

  public static CompoundTag itemData(ItemStack stack) {
    return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
  }

  public static void itemData(ItemStack stack, CompoundTag tag) {
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
  }

  public static boolean attach(ItemStack stack, DeviceEntity be, Player player) {
    if (stack.is(EYE.get())) {
      if (!be.getLevel().isClientSide) {
        Optional<LoadedTargets.Target> target = LoadedTargets.Target.load(itemData(stack));
        if (target.isEmpty())
          player.displayClientMessage(Component.translatable("message.autoturgy.unbound"), true);
        else {
          be.target = target.get();
          be.changed();
          player.displayClientMessage(
              Component.translatable(
                  "message.autoturgy.linked", target.get().pos().toShortString()),
              true);
        }
      }
      return true;
    }
    if (stack.is(ENTITY_EYE.get())) {
      if (!be.getLevel().isClientSide) {
        CompoundTag tag = itemData(stack);
        be.entityType = tag.getString("entityType");
        be.playerIdentity = tag.hasUUID("playerIdentity") ? tag.getUUID("playerIdentity") : null;
        be.changed();
        player.displayClientMessage(
            Component.translatable("message.autoturgy.entity_linked"), true);
      }
      return true;
    }
    if (stack.is(FILTER.get())) {
      if (!be.getLevel().isClientSide) {
        be.filter = StackFilters.load(itemData(stack), be.getLevel().registryAccess());
        be.changed();
        player.displayClientMessage(
            Component.translatable("message.autoturgy.filter_linked"), true);
      }
      return true;
    }
    if (be instanceof TallyEntity tally
        && (stack.is(UNBOUND_LENS.get()) || stack.is(YIELD_LENS.get()))) {
      if (!be.getLevel().isClientSide) tally.installLens(stack, player);
      return true;
    }
    return false;
  }

  public static final class EyeItem extends Item {
    private final boolean entity;

    EyeItem(boolean entity) {
      super(new Item.Properties().stacksTo(1));
      this.entity = entity;
    }

    @Override
    public boolean doesSneakBypassUse(
        ItemStack stack, LevelReader level, BlockPos pos, Player player) {
      return entity
          && level.getBlockEntity(pos)
              instanceof com.sanhiruzu.autoturgy.storage.StorageModule.ChestEntity;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
      if (entity || context.getPlayer() == null || !context.getPlayer().isShiftKeyDown())
        return InteractionResult.PASS;
      if (!context.getLevel().isClientSide) {
        itemData(
            context.getItemInHand(),
            new LoadedTargets.Target(
                    context.getLevel().dimension(),
                    context.getClickedPos(),
                    context.getClickedFace())
                .save());
        context
            .getPlayer()
            .displayClientMessage(
                Component.translatable(
                    "message.autoturgy.bound", context.getClickedPos().toShortString()),
                true);
      }
      return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(
        ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
      if (!entity) return InteractionResult.PASS;
      if (!player.level().isClientSide) bindEntity(stack, target);
      return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    private void bindEntity(ItemStack stack, Entity target) {
      CompoundTag tag = new CompoundTag();
      tag.putString(
          "entityType", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
      if (target instanceof Player) tag.putUUID("playerIdentity", target.getUUID());
      itemData(stack, tag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
        Level level, Player player, InteractionHand hand) {
      if (entity && player.isShiftKeyDown()) {
        if (!level.isClientSide) bindEntity(player.getItemInHand(hand), player);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
      }
      return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(
        ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
      CompoundTag tag = itemData(stack);
      lines.add(
          Component.translatable(
              entity ? "tooltip.autoturgy.entity_eye" : "tooltip.autoturgy.eye"));
      LoadedTargets.Target.load(tag)
          .ifPresent(
              t ->
                  lines.add(
                      Component.literal(
                          t.dimension().location()
                              + " "
                              + t.pos().toShortString()
                              + " / "
                              + t.side().getName())));
      if (tag.contains("entityType"))
        lines.add(
            Component.literal(
                tag.getString("entityType")
                    + (tag.hasUUID("playerIdentity")
                        ? " / " + tag.getUUID("playerIdentity")
                        : "")));
    }
  }

  public static final class ReaderBlock extends DeviceBlock {
    private final java.util.function.BiFunction<BlockPos, BlockState, BlockEntity> factory;

    ReaderBlock(java.util.function.BiFunction<BlockPos, BlockState, BlockEntity> factory) {
      this.factory = factory;
    }

    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return factory.apply(p, s);
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        net.minecraft.world.phys.BlockHitResult hit) {
      if (player.isShiftKeyDown()
          && level.getBlockEntity(pos) instanceof TallyEntity tally
          && !tally.lens.isEmpty()) {
        if (!level.isClientSide) tally.removeLens(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
      }
      return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected void onRemove(
        BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
      if (!state.is(next.getBlock())
          && !level.isClientSide
          && level.getBlockEntity(pos) instanceof TallyEntity tally) {
        ItemStack owned = tally.lens;
        tally.lens = ItemStack.EMPTY;
        net.minecraft.world.level.block.Block.popResource(level, pos, owned);
      }
      super.onRemove(state, level, pos, next, moving);
    }
  }

  public static class RemoteEntity extends DeviceEntity
      implements DeviceBlock.Ticking, TallyService.CountProvider {
    public RemoteEntity(BlockPos p, BlockState s) {
      super(REMOTE_TYPE.get(), p, s);
    }

    public String kind() {
      return "remote_comparator";
    }

    public int controlCount() {
      return 0;
    }

    public void configure(int i, int delta) {}

    public long count(StackFilters.Filter filter, Set<BlockPos> visited, int depth) {
      return TallyService.count(
          (ServerLevel) level, worldPosition, target, filter, 0, visited, depth);
    }

    public void serverTick() {
      if (level.getGameTime() % 4 != 0) return;
      int signal = 0;
      if (target != null && LoadedTargets.local(level, worldPosition, target, 20)) {
        BlockState state = level.getBlockState(target.pos());
        if (!(level.getBlockEntity(target.pos()) instanceof RemoteEntity)
            && state.hasAnalogOutputSignal())
          signal = state.getAnalogOutputSignal(level, target.pos());
      }
      output(signal);
      progress = signal();
    }
  }

  public static final class VisEntity extends DeviceEntity implements DeviceBlock.Ticking {
    private String selectedAspect = "";

    public VisEntity(BlockPos p, BlockState s) {
      super(VIS_TYPE.get(), p, s);
    }

    public String kind() {
      return "vis_reader";
    }

    public int controlCount() {
      return 4;
    }

    public void configure(int i, int delta) {
      if (i == 0) cycle(i, delta, 4);
      else if (i == 1) cycle(i, delta, 2);
      else if (i == 2) bounded(i, delta, 0, 16000);
      else if (i == 3) {
        List<String> choices =
            reading().aspects().stream()
                .map(ThaumaturgeIntegration.AspectReading::id)
                .limit(64)
                .toList();
        int current = choices.indexOf(selectedAspect) + 1;
        settings[3] = Math.floorMod(current + delta, choices.size() + 1);
        selectedAspect = settings[3] == 0 ? "" : choices.get(settings[3] - 1);
        changed();
      }
    }

    public ThaumaturgeIntegration.Snapshot reading() {
      LoadedTargets.Target selected = target;
      if (selected == null) {
        BlockPos above = worldPosition.above();
        if (level.hasChunkAt(above) && level.getBlockEntity(above) instanceof RemoteEntity)
          selected = new LoadedTargets.Target(level.dimension(), above, Direction.DOWN);
        else {
          var locus = com.sanhiruzu.autoturgy.essentia.EssentiaModule.reading(level, worldPosition);
          if (locus != null) return locus;
          selected = new LoadedTargets.Target(level.dimension(), above, Direction.DOWN);
        }
      }
      Set<BlockPos> seen = new HashSet<>(Set.of(worldPosition));
      BlockPos origin = worldPosition;
      for (int depth = 0; depth < 16; depth++) {
        if (selected == null
            || !LoadedTargets.local(level, origin, selected, 20)
            || !LoadedTargets.local(level, worldPosition, selected, 20)
            || !seen.add(selected.pos())) return ThaumaturgeIntegration.Snapshot.invalid();
        if (level.getBlockEntity(selected.pos()) instanceof RemoteEntity remote) {
          origin = selected.pos();
          selected = remote.target;
        } else return ThaumaturgeIntegration.read(level, selected.pos(), selected.side());
      }
      return ThaumaturgeIntegration.Snapshot.invalid();
    }

    public void serverTick() {
      if (level.getGameTime() % 10 == 0) {
        output(
            ThaumaturgeIntegration.signal(
                reading(), settings[0], selectedAspect, settings[1] != 0, settings[2]));
        progress = signal();
        changed();
      }
    }

    @Override
    protected void writeDisplay(CompoundTag tag) {
      tag.putString("selectedAspect", selectedAspect);
    }

    @Override
    protected void readDisplay(CompoundTag tag) {
      selectedAspect = tag.getString("selectedAspect");
    }

    @Override
    public List<ThaumaturgeIntegration.AspectReading> menuAspects() {
      return reading().aspects();
    }

    public String selectedAspect() {
      return selectedAspect;
    }

    @Override
    public int selectionControl() {
      return 3;
    }

    @Override
    public String selectionKey() {
      return selectedAspect;
    }

    @Override
    public List<String> selectionOptions() {
      List<String> result = new ArrayList<>();
      result.add("");
      reading().aspects().stream()
          .map(ThaumaturgeIntegration.AspectReading::id)
          .limit(64)
          .forEach(result::add);
      if (!result.contains(selectedAspect)) result.add(selectedAspect);
      return result;
    }

    @Override
    public void selectKey(String key) {
      selectedAspect = key;
      changed();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      tag.putString("selectedAspect", selectedAspect);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      selectedAspect = tag.getString("selectedAspect");
    }
  }

  public static final class TallyEntity extends DeviceEntity
      implements DeviceBlock.Ticking, TallyService.CountProvider {
    private ItemStack lens = ItemStack.EMPTY;

    public int lensMode() {
      return lens.is(UNBOUND_LENS.get()) ? 1 : lens.is(YIELD_LENS.get()) ? 2 : 0;
    }

    public void installLens(ItemStack stack, Player player) {
      if (level == null
          || level.isClientSide
          || !lens.isEmpty()
          || !(stack.is(UNBOUND_LENS.get()) || stack.is(YIELD_LENS.get()))) return;
      lens = stack.copyWithCount(1);
      stack.shrink(1);
      settings[2] = lensMode();
      changed();
    }

    public void removeLens(Player player) {
      ItemStack owned = lens;
      lens = ItemStack.EMPTY;
      settings[2] = 0;
      changed();
      if (!owned.isEmpty() && !player.addItem(owned)) player.drop(owned, false);
    }

    public LoadedTargets.Target effectiveTarget() {
      if (target != null) return target;
      BlockPos above = worldPosition.above();
      if (level.hasChunkAt(above) && level.getBlockEntity(above) instanceof RemoteEntity)
        return new LoadedTargets.Target(level.dimension(), above, Direction.DOWN);
      Direction facing = getBlockState().getValue(DeviceBlock.FACING);
      return new LoadedTargets.Target(
          level.dimension(), worldPosition.relative(facing.getOpposite()), facing);
    }

    public TallyEntity(BlockPos p, BlockState s) {
      super(TALLY_TYPE.get(), p, s);
      settings[0] = 1;
      settings[1] = 64;
    }

    public String kind() {
      return "tally_box";
    }

    public int controlCount() {
      return 5;
    }

    public void configure(int i, int delta) {
      if (i <= 1) bounded(i, delta, i == 0 ? 0 : 1, 16000);
      else if (i == 4) cycle(i, delta, 3);
      else if (i == 3) cycle(i, delta, 2);
    }

    @Override
    public boolean controlEditable(int i) {
      return i != 2;
    }

    public long count(StackFilters.Filter incoming, Set<BlockPos> visited, int depth) {
      // Chained tally reads preserve the requesting filter, preventing hidden expansion.
      return TallyService.count(
          (ServerLevel) level,
          worldPosition,
          effectiveTarget(),
          incoming,
          lensMode(),
          visited,
          depth);
    }

    public long measured() {
      return count(filter, new HashSet<>(Set.of(worldPosition)), 0);
    }

    public boolean quantitiesSatisfied() {
      if (filter.samples().isEmpty()) return false;
      boolean any = false, all = true;
      for (var sample : filter.samples().stream().limit(9).toList()) {
        var single = new StackFilters.Filter(List.of(sample), false, filter.options());
        long found = count(single, new HashSet<>(Set.of(worldPosition)), 0);
        boolean satisfied =
            filter.blacklist() ? found < sample.quantity() : found >= sample.quantity();
        any |= satisfied;
        all &= satisfied;
      }
      return settings[4] == 2 ? any : all;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      if (!lens.isEmpty()) tag.put("installedLens", lens.save(lookup));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      ItemStack saved = ItemStack.parseOptional(lookup, tag.getCompound("installedLens"));
      lens =
          saved.is(UNBOUND_LENS.get()) || saved.is(YIELD_LENS.get())
              ? saved.copyWithCount(1)
              : ItemStack.EMPTY;
      settings[2] = lensMode();
      settings[4] = Math.clamp(settings[4], 0, 2);
    }

    public void serverTick() {
      if (level.getGameTime() % 10 != 0) return;
      long count = measured();
      count = Math.min(count, Math.max(settings[0], settings[1]));
      int signal =
          count < settings[0]
              ? 0
              : (int)
                  Math.min(
                      15,
                      1
                          + 14
                              * Math.max(0, count - settings[0])
                              / Math.max(1, settings[1] - settings[0]));
      if (settings[4] != 0) signal = quantitiesSatisfied() ? 15 : 0;
      output(settings[3] != 0 ? 15 - signal : signal);
      progress = signal();
    }
  }

  public static final class SignumEntity extends DeviceEntity implements DeviceBlock.Ticking {
    public SignumEntity(BlockPos p, BlockState s) {
      super(SIGNUM_TYPE.get(), p, s);
      settings[0] = 5;
      settings[2] = 15;
      settings[3] = 1;
    }

    public String kind() {
      return "signum_entia";
    }

    public int controlCount() {
      return 4;
    }

    public void configure(int i, int delta) {
      if (i == 0) bounded(i, delta, 1, 5);
      else if (i == 1) cycle(i, delta, 3);
      else if (i == 2) bounded(i, delta, 1, 1000);
      else if (i == 3) cycle(i, delta, 2);
      level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
      level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    public long measured() {
      return level
          .getEntitiesOfClass(
              Entity.class,
              detectionBounds(),
              entity -> {
                if (!entity.isAlive()) return false;
                if (settings[3] != 0
                    && playerIdentity != null
                    && !playerIdentity.equals(entity.getUUID())) return false;
                if (!entityType.isEmpty()
                    && !entityType.equals(
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()))
                  return false;
                return !(entity instanceof LivingEntity living)
                    || settings[1] == 0
                    || (settings[1] == 2) == living.isBaby();
              })
          .size();
    }

    public AABB detectionBounds() {
      int halfSpan = Math.clamp(settings[0], 1, 5);
      Set<Direction> blocked = EnumSet.noneOf(Direction.class);
      for (Direction side : Direction.values()) {
        BlockPos neighbor = worldPosition.relative(side);
        if (!level.hasChunkAt(neighbor)) {
          blocked.add(side);
          continue;
        }
        BlockState state = level.getBlockState(neighbor);
        if (!state.canBeReplaced()
            && !state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)
            && !state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES)
            && !state.getCollisionShape(level, neighbor).isEmpty()) blocked.add(side);
      }
      return new AABB(
          worldPosition.getX() - extent(Direction.WEST, blocked, halfSpan),
          worldPosition.getY() - extent(Direction.DOWN, blocked, halfSpan),
          worldPosition.getZ() - extent(Direction.NORTH, blocked, halfSpan),
          worldPosition.getX() + 1 + extent(Direction.EAST, blocked, halfSpan),
          worldPosition.getY() + 1 + extent(Direction.UP, blocked, halfSpan),
          worldPosition.getZ() + 1 + extent(Direction.SOUTH, blocked, halfSpan));
    }

    private static int extent(Direction side, Set<Direction> blocked, int halfSpan) {
      if (blocked.contains(side)) return 0;
      return blocked.contains(side.getOpposite()) ? halfSpan * 2 : halfSpan;
    }

    public void serverTick() {
      if (level.getGameTime() % 10 == 0) {
        output((int) Math.min(255, measured()));
        progress = signal();
      }
    }

    @Override
    public int signal() {
      return (int) Math.min(15, (long) internalSignal() * 15 / Math.max(1, settings[2]));
    }
  }

  private SensingModule() {}
}
