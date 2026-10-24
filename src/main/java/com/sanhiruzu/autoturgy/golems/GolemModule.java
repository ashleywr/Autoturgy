/* Links player-owned host golems to loaded devices and arbitrates reversible pauses.
 * Device identities reject stale links; no entity lookup loads a chunk. */
package com.sanhiruzu.autoturgy.golems;

import com.leclowndu93150.thaumaturge.content.golem.*;
import com.leclowndu93150.thaumaturge.registry.TTSeals;
import com.sanhiruzu.autoturgy.*;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.inventarium.*;
import com.sanhiruzu.autoturgy.sensing.SensingModule;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.*;

public final class GolemModule {
  public static DeferredBlock<GolemBlock> INHIBITOR, TASKMASTER;
  public static DeferredItem<BellItem> BELL;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<LinkEntity>> TYPE;
  public static final String LINK = "autoturgy_link", CUSTODY = "autoturgy_custody";
  private static final Map<Level, Map<Long, Set<BlockPos>>> INHIBITORS = new WeakHashMap<>();
  private static final String RESTORE_HOME = "autoturgy_restore_home";
  private static final String FIRST_CUSTODY_TICK = "autoturgy_first_custody_tick";
  private static final Map<
          EntityThaumaturgeGolem,
          List<java.lang.ref.WeakReference<net.minecraft.world.entity.ai.goal.WrappedGoal>>>
      OWN_GOALS = new WeakHashMap<>();

  public static void register(IEventBus bus) {
    INHIBITOR = Content.block("golemic_inhibitor", GolemBlock::new);
    TASKMASTER = Content.block("golemic_taskmaster", GolemBlock::new);
    TYPE =
        Content.ENTITIES.register(
            "golem_device",
            () ->
                BlockEntityType.Builder.of(LinkEntity::new, INHIBITOR.get(), TASKMASTER.get())
                    .build(null));
    BELL = Content.item("golem_linking_bell", BellItem::new);
    GolemWorkbench.register(bus);
    NeoForge.EVENT_BUS.addListener(GolemModule::join);
    NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract e) -> {
          if (e.getItemStack().is(BELL.get())
              && e.getTarget() instanceof EntityThaumaturgeGolem g) {
            e.setCancellationResult(
                BELL.get().interactLivingEntity(e.getItemStack(), e.getEntity(), g, e.getHand()));
            e.setCanceled(true);
          }
        });
    NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e) -> {
          if (!e.getLevel().isClientSide && e.getEntity() instanceof EntityThaumaturgeGolem g) {
            restoreHome(g);
            OWN_GOALS.remove(g);
            if (g.getRemovalReason() == Entity.RemovalReason.KILLED
                || g.getRemovalReason() == Entity.RemovalReason.DISCARDED) retire(g);
          }
        });
    NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, GolemModule::tick);
    NeoForge.EVENT_BUS.addListener(
        (EntityTickEvent.Post e) -> {
          if (e.getEntity() instanceof EntityThaumaturgeGolem g && !g.level().isClientSide)
            restoreHome(g);
        });
    NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> {
          if (e.getServer().getTickCount() % 200 == 0) LinkRemovals.maintain(e.getServer());
        });
    NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, GolemModule::death);
  }

  public static boolean role(EntityThaumaturgeGolem golem, boolean crafting) {
    var type = crafting ? TTSeals.EMPTY.get() : TTSeals.FILL.get();
    return type.requiredTraits().stream().allMatch(t -> golem.properties().hasTrait(t.value()))
        && type.forbiddenTraits().stream().noneMatch(t -> golem.properties().hasTrait(t.value()));
  }

  private static void join(EntityJoinLevelEvent e) {
    if (!e.getLevel().isClientSide && e.getEntity() instanceof EntityThaumaturgeGolem g) {
      restoreHome(g);
      var link = LoadedTargets.Target.load(g.getPersistentData().getCompound(LINK)).orElse(null);
      if (link != null && !link.dimension().equals(g.level().dimension())) detachLink(g);
      var custody =
          LoadedTargets.Target.load(g.getPersistentData().getCompound(CUSTODY)).orElse(null);
      if (custody != null && !custody.dimension().equals(g.level().dimension()))
        GolemWorkGoal.dropCrossDimensionCustody(g);
      if (g.getPersistentData().contains(CUSTODY))
        g.getPersistentData().putBoolean(FIRST_CUSTODY_TICK, true);
      install(g);
    }
  }

  private static void restoreHome(EntityThaumaturgeGolem g) {
    if (!g.getPersistentData().contains(RESTORE_HOME)) return;
    CompoundTag tag = g.getPersistentData().getCompound(RESTORE_HOME);
    if (tag.contains("home"))
      g.restrictTo(BlockPos.of(tag.getLong("home")), Math.clamp(tag.getInt("radius"), 0, 4096));
    g.getPersistentData().remove(RESTORE_HOME);
  }

  private static void tick(EntityTickEvent.Pre e) {
    if (e.getEntity() instanceof EntityThaumaturgeGolem g && !g.level().isClientSide) {
      // Host firstRun teleports loaded golems home. Only saved addon custody suppresses this tick.
      if (g.getPersistentData().getBoolean(FIRST_CUSTODY_TICK)) {
        g.getPersistentData().remove(FIRST_CUSTODY_TICK);
        if (g.hasRestriction()
            && !g.blockPosition().equals(g.getRestrictCenter())
            && g.getPersistentData().contains(CUSTODY)) {
          CompoundTag home = new CompoundTag();
          home.putLong("home", g.getRestrictCenter().asLong());
          home.putInt("radius", (int) g.getRestrictRadius());
          g.getPersistentData().put(RESTORE_HOME, home);
          g.restrictTo(g.blockPosition(), (int) g.getRestrictRadius());
        }
      }
      if (g.getPersistentData().contains(CUSTODY) && g.getTask() != null) {
        var task = g.getTask();
        task.release();
        task.setClaimant(null);
        task.assignTo(null);
        g.setTask(null);
      }
      // Parts may rebuild selectors at any time; custody cannot leave a one-second ownership gap.
      install(g);
    }
  }

  public static void install(EntityThaumaturgeGolem g) {
    var available = g.goalSelector.getAvailableGoals();
    var tracked = OWN_GOALS.computeIfAbsent(g, x -> new ArrayList<>());
    // Host selector rebuilds remove wrappers without stopping their running movement locks.
    tracked.removeIf(
        ref -> {
          var wrapper = ref.get();
          if (wrapper == null) return true;
          if (!available.contains(wrapper)) {
            wrapper.stop();
            return true;
          }
          return false;
        });
    if (g.goalSelector.getAvailableGoals().stream()
        .noneMatch(w -> w.getGoal() instanceof PauseGoal))
      g.goalSelector.addGoal(-2, new PauseGoal(g));
    if (g.goalSelector.getAvailableGoals().stream()
        .noneMatch(w -> w.getGoal() instanceof GolemWorkGoal))
      g.goalSelector.addGoal(-1, new GolemWorkGoal(g));
    for (var wrapper : available)
      if ((wrapper.getGoal() instanceof PauseGoal || wrapper.getGoal() instanceof GolemWorkGoal)
          && tracked.stream().noneMatch(ref -> ref.get() == wrapper))
        tracked.add(new java.lang.ref.WeakReference<>(wrapper));
  }

  private static void death(LivingDeathEvent e) {
    if (!(e.getEntity() instanceof EntityThaumaturgeGolem g) || g.level().isClientSide) return;
    retire(g);
  }

  private static void retire(EntityThaumaturgeGolem g) {
    detachLink(g);
    GolemWorkGoal.releaseOnDeath(g);
  }

  private static void detachLink(EntityThaumaturgeGolem g) {
    rememberRemoval(g);
    CompoundTag data = g.getPersistentData().getCompound(LINK);
    var at = LoadedTargets.Target.load(data).orElse(null);
    var entity = at == null ? null : LoadedTargets.blockEntity(g.getServer(), at).orElse(null);
    LinkEntity device =
        entity instanceof LinkEntity d
                && data.hasUUID("device")
                && d.identity.equals(data.getUUID("device"))
            ? d
            : null;
    if (device != null) {
      device.links.remove(g.getUUID());
      device.changed();
    }
    g.getPersistentData().remove(LINK);
  }

  static void rememberRemoval(EntityThaumaturgeGolem g) {
    CompoundTag tag = g.getPersistentData().getCompound(LINK);
    if (!tag.hasUUID("device")) return;
    var at = LoadedTargets.Target.load(tag).orElse(null);
    if (at != null && LoadedTargets.level(g.getServer(), at).isEmpty())
      LinkRemovals.get(g.getServer()).add(tag.getUUID("device"), g.getUUID(), at);
  }

  public static LinkEntity linked(EntityThaumaturgeGolem g) {
    CompoundTag tag = g.getPersistentData().getCompound(LINK);
    var at = LoadedTargets.Target.load(tag).orElse(null);
    if (at == null || !tag.hasUUID("device")) return null;
    var be = LoadedTargets.blockEntity(g.getServer(), at).orElse(null);
    return be instanceof LinkEntity d
            && d.getLevel() == g.level()
            && d.identity.equals(tag.getUUID("device"))
            && d.links.contains(g.getUUID())
            && Objects.equals(d.playerIdentity, g.getOwnerUUID())
        ? d
        : null;
  }

  public static boolean inhibited(EntityThaumaturgeGolem g) {
    if (g.isInCombat()) return false;
    int checked = 0;
    var chunks = INHIBITORS.getOrDefault(g.level(), Map.of());
    int centerX = g.blockPosition().getX() >> 4, centerZ = g.blockPosition().getZ() >> 4;
    for (int x = centerX - 2; x <= centerX + 2; x++)
      for (int z = centerZ - 2; z <= centerZ + 2; z++)
        for (BlockPos p :
            chunks.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x, z), Set.of())) {
          if (++checked > 4096) return false;
          if (!g.level().hasChunkAt(p)
              || !(g.level().getBlockEntity(p) instanceof LinkEntity d)
              || d.isRemoved()
              || !Objects.equals(d.playerIdentity, g.getOwnerUUID())) continue;
          int radius = d.radius();
          if (radius > 0
              && g.distanceToSqr(p.getCenter()) <= radius * radius
              && (d.settings[0] == 1) == d.links.contains(g.getUUID())) return true;
        }
    return false;
  }

  public static boolean activeFetter(Level level, BlockPos p) {
    if (!level.hasChunkAt(p)) return false;
    var state = level.getBlockState(p);
    return state.getBlock() instanceof BlockGolemFetter
        && state.getValue(BlockStateProperties.POWERED);
  }

  public static boolean withinWorkReach(EntityThaumaturgeGolem golem, BlockPos pos) {
    double dx = Math.max(Math.max(pos.getX() - golem.getX(), 0), golem.getX() - pos.getX() - 1);
    double dy = Math.max(Math.max(pos.getY() - golem.getY(), 0), golem.getY() - pos.getY() - 1);
    double dz = Math.max(Math.max(pos.getZ() - golem.getZ(), 0), golem.getZ() - pos.getZ() - 1);
    return dx * dx + dy * dy + dz * dz <= 4;
  }

  private static final class PauseGoal extends Goal {
    private final EntityThaumaturgeGolem golem;

    PauseGoal(EntityThaumaturgeGolem golem) {
      this.golem = golem;
      setFlags(EnumSet.allOf(Flag.class));
    }

    public boolean canUse() {
      return inhibited(golem);
    }

    public boolean canContinueToUse() {
      return canUse();
    }

    public boolean requiresUpdateEveryTick() {
      return true;
    }

    public void start() {
      tick();
    }

    public void tick() {
      golem.getNavigation().stop();
      golem.setDeltaMovement(0, golem.getDeltaMovement().y, 0);
    }
  }

  public static class GolemBlock extends DeviceBlock {
    GolemBlock() {}

    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new LinkEntity(p, s);
    }

    public void setPlacedBy(
        Level l, BlockPos p, BlockState s, LivingEntity entity, ItemStack stack) {
      super.setPlacedBy(l, p, s, entity, stack);
      if (entity instanceof Player && l.getBlockEntity(p) instanceof LinkEntity d) {
        d.playerIdentity = entity.getUUID();
        d.changed();
      }
    }

    protected InteractionResult useWithoutItem(
        BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
      if (l.getBlockEntity(p) instanceof LinkEntity d && !d.authorized(player))
        return InteractionResult.FAIL;
      return super.useWithoutItem(s, l, p, player, hit);
    }

    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState s,
        Level l,
        BlockPos p,
        Player player,
        InteractionHand hand,
        BlockHitResult hit) {
      if (stack.is(BELL.get())) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
      if (l.getBlockEntity(p) instanceof LinkEntity d && !d.authorized(player))
        return ItemInteractionResult.FAIL;
      if (stack.is(SensingModule.ENTITY_EYE.get())) return ItemInteractionResult.FAIL;
      return super.useItemOn(stack, s, l, p, player, hand, hit);
    }

    public void onRemove(BlockState old, Level l, BlockPos p, BlockState next, boolean moved) {
      if (!old.is(next.getBlock()) && l.getBlockEntity(p) instanceof LinkEntity d) d.clear();
      super.onRemove(old, l, p, next, moved);
    }
  }

  public static class LinkEntity extends DeviceEntity
      implements DeviceBlock.Ticking, InventariumModule.MemoryDevice {
    public UUID identity = UUID.randomUUID();
    public final Set<UUID> links = new LinkedHashSet<>();

    public LinkEntity(BlockPos p, BlockState s) {
      this(TYPE.get(), p, s);
    }

    protected LinkEntity(BlockEntityType<?> type, BlockPos p, BlockState s) {
      super(type, p, s);
    }

    public boolean taskmaster() {
      return getBlockState().is(TASKMASTER.get());
    }

    public String kind() {
      return taskmaster() ? "golemic_taskmaster" : "golemic_inhibitor";
    }

    public int controlCount() {
      return taskmaster() ? 0 : 1;
    }

    public void configure(int i, int delta) {
      if (i == 0 && !taskmaster()) cycle(0, delta, 2);
    }

    public boolean authorized(Player player) {
      return playerIdentity == null || playerIdentity.equals(player.getUUID());
    }

    public boolean usable(Player player) {
      return authorized(player) && super.usable(player);
    }

    public UUID memoryIdentity() {
      return identity;
    }

    public LoadedTargets.Target coreTarget() {
      return target;
    }

    public InventariumModule.CoreEntity core() {
      if (!taskmaster() || target == null || level == null || level.isClientSide) return null;
      var be = LoadedTargets.blockEntity(level.getServer(), target).orElse(null);
      return be instanceof InventariumModule.CoreEntity c && c.memory(identity, location())
          ? c
          : null;
    }

    public LoadedTargets.Target location() {
      return new LoadedTargets.Target(level.dimension(), worldPosition, Direction.UP);
    }

    public boolean compatible(EntityThaumaturgeGolem g) {
      return level == g.level()
          && (!taskmaster()
              || role(g, false)
                  && g.hasRestriction()
                  && LoadedTargets.withinRange(worldPosition, g.getRestrictCenter(), 5));
    }

    public String link(Player player, EntityThaumaturgeGolem g) {
      if (level != g.level()) return "dimension";
      if (!authorized(player) || !g.ownerIdentity().filter(player.getUUID()::equals).isPresent())
        return "owner";
      if (!compatible(g)) return "role";
      if ((taskmaster() || this instanceof GolemWorkbench.BenchEntity)
          && (g.hands().contents().stream().anyMatch(s -> !s.isEmpty())
              || g.getTask() != null && !g.getTask().isEnded())) return "busy";
      if (!links.contains(g.getUUID()) && links.size() >= 6) return "full";
      if (g.getPersistentData().contains(CUSTODY)) return "carrying";
      rememberRemoval(g);
      LinkEntity previous = linked(g);
      if (previous != null && previous != this) {
        previous.links.remove(g.getUUID());
        previous.changed();
      }
      playerIdentity = player.getUUID();
      links.add(g.getUUID());
      CompoundTag tag = location().save();
      tag.putUUID("device", identity);
      g.getPersistentData().put(LINK, tag);
      install(g);
      changed();
      return "linked";
    }

    public void clear() {
      if (level instanceof ServerLevel server)
        for (UUID id : links)
          if (server.getEntity(id) instanceof EntityThaumaturgeGolem g && linked(g) == this)
            g.getPersistentData().remove(LINK);
      links.clear();
      changed();
    }

    public int radius() {
      if (!getBlockState().is(INHIBITOR.get())) return 0;
      int count = 0;
      while (count < 4 && activeFetter(level, worldPosition.below(count + 1))) count++;
      return count * 8;
    }

    public void serverTick() {
      if (level.getGameTime() % 20 != 0) return;
      links.removeIf(
          id ->
              ((ServerLevel) level).getEntity(id) instanceof EntityThaumaturgeGolem g
                  && (!g.isAlive() || linked(g) != this));
      progress = links.size();
    }

    public void onLoad() {
      super.onLoad();
      if (!level.isClientSide) LinkRemovals.get(level.getServer()).apply(this);
      if (!level.isClientSide && getBlockState().is(INHIBITOR.get()))
        INHIBITORS
            .computeIfAbsent(level, x -> new HashMap<>())
            .computeIfAbsent(
                new net.minecraft.world.level.ChunkPos(worldPosition).toLong(),
                x -> new LinkedHashSet<>())
            .add(worldPosition);
    }

    private void unregister() {
      var chunks = INHIBITORS.get(level);
      if (chunks != null) {
        long key = new net.minecraft.world.level.ChunkPos(worldPosition).toLong();
        var set = chunks.get(key);
        if (set != null) {
          set.remove(worldPosition);
          if (set.isEmpty()) chunks.remove(key);
        }
      }
    }

    public void setRemoved() {
      unregister();
      super.setRemoved();
    }

    public void onChunkUnloaded() {
      unregister();
      super.onChunkUnloaded();
    }

    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      tag.putUUID("identity", identity);
      ListTag list = new ListTag();
      for (UUID id : links) {
        CompoundTag row = new CompoundTag();
        row.putUUID("id", id);
        list.add(row);
      }
      tag.put("golems", list);
    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      if (tag.hasUUID("identity")) identity = tag.getUUID("identity");
      links.clear();
      ListTag list = tag.getList("golems", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(6, list.size()); i++)
        if (list.getCompound(i).hasUUID("id")) links.add(list.getCompound(i).getUUID("id"));
    }
  }

  public static final class BellItem extends Item {
    BellItem() {
      super(new Item.Properties().stacksTo(1));
    }

    public InteractionResult useOn(UseOnContext c) {
      if (c.getPlayer() == null) return InteractionResult.PASS;
      if (!c.getLevel().isClientSide) {
        var player = c.getPlayer();
        var data = SensingModule.itemData(c.getItemInHand());
        if (c.getLevel().getBlockEntity(c.getClickedPos()) instanceof LinkEntity d) {
          if (!d.authorized(player)) {
            message(player, "owner");
            return InteractionResult.FAIL;
          }
          if (player.isShiftKeyDown()) {
            d.clear();
            data = new CompoundTag();
            message(player, "cleared");
          } else {
            if (d.playerIdentity == null) {
              d.playerIdentity = player.getUUID();
              d.changed();
            }
            data = d.location().save();
            data.putUUID("device", d.identity);
            message(player, "attuned");
          }
        } else if (player.isShiftKeyDown()) {
          data = new CompoundTag();
          message(player, "unbound");
        } else {
          message(player, "device");
          return InteractionResult.FAIL;
        }
        SensingModule.itemData(c.getItemInHand(), data);
      }
      return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }

    public InteractionResult interactLivingEntity(
        ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
      if (!(target instanceof EntityThaumaturgeGolem g)) return InteractionResult.PASS;
      if (!player.level().isClientSide) {
        CompoundTag data = SensingModule.itemData(stack);
        var at = LoadedTargets.Target.load(data).orElse(null);
        var be = at == null ? null : LoadedTargets.blockEntity(player.getServer(), at).orElse(null);
        if (!(be instanceof LinkEntity d)
            || !data.hasUUID("device")
            || !d.identity.equals(data.getUUID("device"))) message(player, "device");
        else message(player, d.link(player, g));
      }
      return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
  }

  static void message(Player p, String result) {
    p.displayClientMessage(Component.translatable("message.autoturgy.golem." + result), true);
  }

  private GolemModule() {}
}
