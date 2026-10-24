/* Owns finite stone and concentrate balances, with exact physical XP transfers.
 * Host brain jars retain their own XP; break, bottle and drip each debit once. */
package com.sanhiruzu.autoturgy.experience;

import com.leclowndu93150.thaumaturge.content.essentia.jar.*;
import com.mojang.serialization.MapCodec;
import com.sanhiruzu.autoturgy.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.registries.*;

public final class ExperienceModule {
  public static final int MAX_PLAYER_LEVEL = 200_000_000;
  public static final long MAX_PLAYER_POINTS =
      ExperiencePoints.atLevel(MAX_PLAYER_LEVEL) + ExperiencePoints.next(MAX_PLAYER_LEVEL) - 1;

  public enum Kind {
    AMNESIAC,
    SOUL,
    CONCENTRATE
  }

  public static final Map<Kind, DeferredBlock<XpBlock>> BLOCKS = new EnumMap<>(Kind.class);
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<XpEntity>> TYPE;
  public static DeferredItem<Item> PHIAL, FILLED_PHIAL;

  public static void register(IEventBus bus) {
    for (Kind k : Kind.values())
      BLOCKS.put(
          k,
          Content.block(
              switch (k) {
                case AMNESIAC -> "amnesiac_stone";
                case SOUL -> "soul_stone";
                case CONCENTRATE -> "enchanters_concentrate";
              },
              () -> new XpBlock(k)));
    TYPE =
        Content.ENTITIES.register(
            "experience",
            () ->
                BlockEntityType.Builder.of(
                        XpEntity::new,
                        BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new))
                    .build(null));
    PHIAL = Content.item("experience_phial", () -> new Item(new Item.Properties()));
    FILLED_PHIAL = Content.item("concentrate_phial", () -> new Drink());
    NeoForge.EVENT_BUS.addListener(ExperienceModule::impact);
    NeoForge.EVENT_BUS.addListener(ExperienceModule::brainUse);
  }

  public static boolean validPoints(Player p) {
    return p.experienceLevel >= 0
        && p.experienceLevel <= MAX_PLAYER_LEVEL
        && Float.isFinite(p.experienceProgress)
        && p.experienceProgress >= 0
        && p.experienceProgress < 1;
  }

  public static long points(Player p) {
    if (!validPoints(p)) return 0;
    CompoundTag exact = p.getPersistentData().getCompound("autoturgyExactXp");
    if (exact.contains("Points")
        && exact.getInt("Level") == p.experienceLevel
        && exact.getInt("Progress") == Float.floatToIntBits(p.experienceProgress))
      return Math.clamp(
          exact.getLong("Points"),
          ExperiencePoints.atLevel(p.experienceLevel),
          ExperiencePoints.atLevel(p.experienceLevel)
              + ExperiencePoints.next(p.experienceLevel)
              - 1);
    long base = ExperiencePoints.atLevel(p.experienceLevel);
    long fraction =
        Math.min(
            ExperiencePoints.next(p.experienceLevel) - 1,
            Math.max(
                0,
                Math.round(
                    (double) p.experienceProgress * ExperiencePoints.next(p.experienceLevel))));
    return base > Long.MAX_VALUE - fraction ? Long.MAX_VALUE : base + fraction;
  }

  public static void setPoints(Player p, long total) {
    total = Math.clamp(total, 0, MAX_PLAYER_POINTS);
    var b = ExperiencePoints.split(total);
    p.experienceLevel = b.level();
    p.experienceProgress =
        b.width() == 0 ? 0 : Math.min(Math.nextDown(1F), (float) ((double) b.points() / b.width()));
    p.totalExperience = (int) Math.min(Integer.MAX_VALUE, Math.max(0, total));
    CompoundTag exact = new CompoundTag();
    exact.putLong("Points", Math.max(0, total));
    exact.putInt("Level", p.experienceLevel);
    exact.putInt("Progress", Float.floatToIntBits(p.experienceProgress));
    p.getPersistentData().put("autoturgyExactXp", exact);
    if (p instanceof net.minecraft.server.level.ServerPlayer server && server.connection != null)
      server.connection.send(
          new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(
              p.experienceProgress, p.totalExperience, p.experienceLevel));
  }

  public static int withdraw(Player p, int max) {
    if (!validPoints(p) || max <= 0) return 0;
    long balance = points(p);
    int n = (int) Math.min(balance, max);
    setPoints(p, balance - n);
    return n;
  }

  public static void grant(Player p, int n) {
    if (!validPoints(p)) return;
    long old = points(p);
    setPoints(p, old > Long.MAX_VALUE - Math.max(0, n) ? Long.MAX_VALUE : old + Math.max(0, n));
  }

  private static void impact(ProjectileImpactEvent event) {
    if (!(event.getProjectile() instanceof ThrownExperienceBottle bottle)
        || !(bottle.level() instanceof ServerLevel level)
        || !(event.getRayTraceResult() instanceof BlockHitResult hit)
        || !(level.getBlockEntity(hit.getBlockPos()) instanceof XpEntity xp)
        || xp.kind() != Kind.AMNESIAC
        || xp.room() < 9) return;
    xp.add(9);
    bottle.discard();
    event.setCanceled(true);
  }

  private static void brainUse(
      net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
    if (e.getLevel() instanceof ServerLevel l
        && e.getItemStack().isEmpty()
        && e.getEntity().isShiftKeyDown()
        && l.getBlockEntity(e.getPos()) instanceof BlockEntityJarBrain brain
        && l.getBlockEntity(e.getPos().below()) instanceof XpEntity x
        && x.kind() == Kind.AMNESIAC) {
      int n = brain.xp();
      brain.setXp(0);
      brain.setEatDelay(40);
      ExperienceOrb.award(l, Vec3.atCenterOf(e.getPos().above()), n);
      e.setCanceled(true);
      e.setCancellationResult(InteractionResult.SUCCESS);
    }
  }

  public static class XpBlock extends BaseEntityBlock {
    final Kind kind;

    XpBlock(Kind kind) {
      super(Properties.of().strength(1.5F));
      this.kind = kind;
    }

    protected MapCodec<? extends BaseEntityBlock> codec() {
      return MapCodec.unit(this);
    }

    protected RenderShape getRenderShape(BlockState s) {
      return RenderShape.MODEL;
    }

    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
        BlockState s,
        BlockGetter l,
        BlockPos p,
        net.minecraft.world.phys.shapes.CollisionContext c) {
      return kind == Kind.CONCENTRATE ? Block.box(2, 0, 2, 14, 16, 14) : super.getShape(s, l, p, c);
    }

    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new XpEntity(p, s);
    }

    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level l, BlockState s, BlockEntityType<T> t) {
      return l.isClientSide
          ? null
          : (w, p, b, e) -> {
            if (e instanceof XpEntity x) x.tick();
          };
    }

    protected boolean hasAnalogOutputSignal(BlockState s) {
      return true;
    }

    protected int getAnalogOutputSignal(BlockState s, Level l, BlockPos p) {
      return l.getBlockEntity(p) instanceof XpEntity x ? x.comparator() : 0;
    }

    protected InteractionResult useWithoutItem(
        BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
      if (!l.isClientSide && l.getBlockEntity(p) instanceof XpEntity x) x.click(pl);
      return InteractionResult.sidedSuccess(l.isClientSide);
    }

    protected ItemInteractionResult useItemOn(
        ItemStack item,
        BlockState s,
        Level l,
        BlockPos p,
        Player pl,
        InteractionHand hand,
        BlockHitResult h) {
      if (!(l.getBlockEntity(p) instanceof XpEntity x))
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      if (item.is(Items.EXPERIENCE_BOTTLE) && kind == Kind.AMNESIAC) {
        if (!l.isClientSide && x.room() >= 9) {
          x.add(9);
          exchange(pl, hand, new ItemStack(Items.GLASS_BOTTLE));
        }
      } else if (kind == Kind.CONCENTRATE && item.is(PHIAL.get())) {
        if (!l.isClientSide && x.xp >= 100) {
          x.add(-100);
          exchange(pl, hand, new ItemStack(FILLED_PHIAL.get()));
        }
      } else if (kind == Kind.CONCENTRATE && item.is(FILLED_PHIAL.get())) {
        if (!l.isClientSide && x.room() >= 100) {
          x.add(100);
          exchange(pl, hand, new ItemStack(PHIAL.get()));
        }
      } else if (kind == Kind.CONCENTRATE && item.is(Items.GLASS_BOTTLE)) {
        if (!l.isClientSide && x.xp >= 20) {
          x.add(-20);
          exchange(pl, hand, new ItemStack(Items.EXPERIENCE_BOTTLE));
        }
      } else return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      return ItemInteractionResult.sidedSuccess(l.isClientSide);
    }

    public void stepOn(Level l, BlockPos pos, BlockState s, Entity entity) {
      drainStanding(l, pos, entity);
      super.stepOn(l, pos, s, entity);
    }

    private void drainStanding(Level l, BlockPos pos, Entity entity) {
      if (!l.isClientSide
          && kind == Kind.AMNESIAC
          && entity instanceof Player p
          && l.getGameTime() % 4 == 0
          && l.getBlockEntity(pos) instanceof XpEntity x) {
        CompoundTag data = p.getPersistentData();
        if (data.getLong("autoturgyLastDrain") == l.getGameTime()) return;
        data.putLong("autoturgyLastDrain", l.getGameTime());
        int n = withdraw(p, Math.min(10, x.room()));
        if (n > 0) x.add(n);
      }
    }

    protected void entityInside(BlockState s, Level l, BlockPos pos, Entity entity) {
      drainStanding(l, pos, entity);
    }

    protected List<ItemStack> getDrops(BlockState s, LootParams.Builder b) {
      ItemStack drop = new ItemStack(this);
      if (b.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof XpEntity x
          && kind == Kind.CONCENTRATE) {
        CompoundTag data = new CompoundTag();
        data.putInt("xp", x.xp);
        data.putString(
            "id",
            net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                .getKey(x.getType())
                .toString());
        drop.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
      }
      return List.of(drop);
    }

    protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
      if (!s.is(next.getBlock())
          && !l.isClientSide
          && l.getBlockEntity(p) instanceof XpEntity x
          && kind != Kind.CONCENTRATE) {
        int remainder = x.xp / 2;
        x.xp = 0;
        if (remainder > 0) ExperienceOrb.award((ServerLevel) l, Vec3.atCenterOf(p), remainder);
      }
      super.onRemove(s, l, p, next, moving);
    }
  }

  private static void exchange(Player p, InteractionHand hand, ItemStack out) {
    p.getItemInHand(hand).shrink(1);
    if (p.getItemInHand(hand).isEmpty()) p.setItemInHand(hand, out);
    else if (!p.addItem(out)) p.drop(out, false);
  }

  public static final class XpEntity extends BlockEntity {
    public int xp;

    public XpEntity(BlockPos p, BlockState s) {
      super(TYPE.get(), p, s);
    }

    public Kind kind() {
      return ((XpBlock) getBlockState().getBlock()).kind;
    }

    public int capacity() {
      return kind() == Kind.AMNESIAC ? 2000 : 10000;
    }

    public int room() {
      return capacity() - xp;
    }

    public void add(int n) {
      xp = Math.clamp(xp + n, 0, capacity());
      setChanged();
      if (level != null)
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    public void click(Player p) {
      if (kind() == Kind.AMNESIAC) {
        if (!validPoints(p)) {
          p.displayClientMessage(Component.translatable("message.autoturgy.invalid_xp"), true);
          return;
        }
        int levels = p.isShiftKeyDown() ? 5 : 1;
        long base = ExperiencePoints.atLevel(p.experienceLevel);
        int targetLevel = Math.max(0, p.experienceLevel - levels);
        long fraction = points(p) - base;
        long target =
            ExperiencePoints.atLevel(targetLevel)
                + fraction
                    * ExperiencePoints.next(targetLevel)
                    / ExperiencePoints.next(p.experienceLevel);
        long requested = points(p) - target;
        int n = withdraw(p, (int) Math.min(room(), Math.max(0, requested)));
        add(n);
      } else
        p.displayClientMessage(
            Component.translatable("message.autoturgy.xp", xp, capacity()), true);
    }

    public int comparator() {
      if (kind() != Kind.AMNESIAC) return xp == 0 ? 0 : 1 + (int) (14L * xp / capacity());
      Set<BlockPos> seen = new HashSet<>();
      ArrayDeque<BlockPos> todo = new ArrayDeque<>();
      todo.add(worldPosition);
      long total = 0, cap = 0;
      while (!todo.isEmpty() && seen.size() < 512) {
        BlockPos p = todo.remove();
        if (!seen.add(p) || !level.hasChunkAt(p)) continue;
        BlockEntity be = level.getBlockEntity(p);
        if (be instanceof XpEntity x && x.kind() != Kind.CONCENTRATE) {
          total += x.xp;
          cap += x.capacity();
        } else if (be instanceof BlockEntityJarBrain brain) {
          total += brain.xp();
          cap += BlockEntityJarBrain.XP_MAX;
        } else continue;
        for (Direction d : Direction.values()) todo.add(p.relative(d));
      }
      return total == 0 ? 0 : 1 + (int) (14 * total / Math.max(1, cap));
    }

    public void tick() {
      if (level.getGameTime() % 10 != 0) return;
      transfer(level.getGameTime() % 20 == 0);
    }

    public void transfer(boolean condense) {
      if (kind() == Kind.AMNESIAC) {
        if (level.getBlockEntity(worldPosition.above()) instanceof BlockEntityJarBrain brain) {
          int n = Math.min(xp, Math.min(20, BlockEntityJarBrain.XP_MAX - brain.xp()));
          brain.setXp(brain.xp() + n);
          add(-n);
        } else if (xp > 0) {
          int n = Math.min(10, xp);
          add(-n);
          ExperienceOrb.award((ServerLevel) level, Vec3.atCenterOf(worldPosition.above()), n);
        }
      } else if (kind() == Kind.SOUL) {
        for (Direction d : Direction.values()) {
          BlockPos p = worldPosition.relative(d);
          if (!level.hasChunkAt(p)) continue;
          BlockEntity neighbor = level.getBlockEntity(p);
          BlockEntityJarBrain brain =
              neighbor instanceof BlockEntityJarBrain b
                  ? b
                  : neighbor instanceof XpEntity x
                          && x.kind() == Kind.AMNESIAC
                          && level.hasChunkAt(p.above())
                          && level.getBlockEntity(p.above()) instanceof BlockEntityJarBrain b
                      ? b
                      : null;
          if (brain != null) {
            if (xp > brain.xp()) {
              int n =
                  Math.min(10, Math.min(xp - brain.xp(), BlockEntityJarBrain.XP_MAX - brain.xp()));
              brain.setXp(brain.xp() + n);
              add(-n);
            } else {
              int n = Math.min(room(), Math.min(10, brain.xp() - xp));
              brain.setXp(brain.xp() - n);
              add(n);
            }
          } else if (neighbor instanceof XpEntity x && x.kind() == Kind.AMNESIAC) {
            int n = Math.min(room(), Math.min(10, x.xp));
            x.add(-n);
            add(n);
          }
        }
        if (xp == 0 || !condense) return;
        BlockPos below = worldPosition.below();
        if (!level.hasChunkAt(below)) return;
        if (level.isEmptyBlock(below)
            || (level.getBlockState(below).getBlock()
                    instanceof com.leclowndu93150.thaumaturge.content.device.BlockItemGrate
                && level
                    .getBlockState(below)
                    .getValue(com.leclowndu93150.thaumaturge.content.device.BlockItemGrate.OPEN)))
          below = below.below();
        if (!level.hasChunkAt(below)) return;
        if (level.getBlockEntity(below) instanceof BlockEntityJar jar
            && jar.amount() == 0
            && jar.aspectFilterKey() == null) {
          level.setBlockAndUpdate(below, BLOCKS.get(Kind.CONCENTRATE).get().defaultBlockState());
        }
        if (level.getBlockEntity(below) instanceof XpEntity jar && jar.kind() == Kind.CONCENTRATE) {
          int n = Math.min(10, Math.min(xp, jar.room()));
          jar.add(n);
          add(-n);
        } else if (level.isEmptyBlock(below)) add(-Math.min(1, xp));
      }
    }

    protected void saveAdditional(CompoundTag t, HolderLookup.Provider p) {
      super.saveAdditional(t, p);
      t.putInt("xp", xp);
    }

    protected void loadAdditional(CompoundTag t, HolderLookup.Provider p) {
      super.loadAdditional(t, p);
      xp = Math.clamp(t.getInt("xp"), 0, capacity());
    }
  }

  private static final class Drink extends Item {
    Drink() {
      super(new Properties().stacksTo(16));
    }

    public UseAnim getUseAnimation(ItemStack s) {
      return UseAnim.DRINK;
    }

    public int getUseDuration(ItemStack s, LivingEntity e) {
      return 32;
    }

    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
      p.startUsingItem(h);
      return InteractionResultHolder.consume(p.getItemInHand(h));
    }

    public ItemStack finishUsingItem(ItemStack s, Level l, LivingEntity e) {
      if (!l.isClientSide && e instanceof Player p) {
        if (!validPoints(p) || MAX_PLAYER_POINTS - points(p) < 100) return s;
        grant(p, 100);
        s.shrink(1);
        ItemStack empty = new ItemStack(PHIAL.get());
        if (s.isEmpty()) return empty;
        if (!p.addItem(empty)) p.drop(empty, false);
      }
      return s;
    }
  }

  private ExperienceModule() {}
}
