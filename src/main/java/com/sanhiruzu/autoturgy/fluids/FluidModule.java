/* Fluid registration and the thirsty tank's physical resource ownership.
 * Face glyphs compose bounded intake/output rules without virtual fluid copies. */
package com.sanhiruzu.autoturgy.fluids;

import com.sanhiruzu.autoturgy.Content;
import com.sanhiruzu.autoturgy.common.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.*;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.registries.*;

public final class FluidModule {
  public enum Glyph {
    VOID,
    CONSUMPTION,
    SIPHONING,
    ENVY,
    TEMPERANCE,
    PRESERVATION,
    GUZZLER,
    RESERVOIR,
    BOVINE
  }

  public static final Map<Glyph, DeferredItem<Item>> GLYPHS = new EnumMap<>(Glyph.class);
  public static DeferredBlock<TankBlock> TANK;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<TankEntity>> TANK_TYPE;
  public static final DeferredRegister<Fluid> FLUIDS =
      DeferredRegister.create(Registries.FLUID, "autoturgy");
  public static final DeferredRegister<FluidType> TYPES =
      DeferredRegister.create(
          net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_TYPES, "autoturgy");
  public static SoupFluid MUSHROOM, VISHROOM;
  public static DeferredBlock<MilkSource> MILK_BLOCK;
  public static DeferredItem<Item> MILK_BUCKET;

  public static void register(IEventBus bus) {
    NeoForgeMod.enableMilkFluid();
    TANK = Content.block("thirsty_tank", TankBlock::new);
    TANK_TYPE =
        Content.ENTITIES.register(
            "thirsty_tank",
            () -> BlockEntityType.Builder.of(TankEntity::new, TANK.get()).build(null));
    for (Glyph g : Glyph.values())
      GLYPHS.put(
          g, Content.item("glyph_" + g.name().toLowerCase(Locale.ROOT), () -> new GlyphItem(g)));
    MUSHROOM = new SoupFluid("mushroom_soup", false);
    VISHROOM = new SoupFluid("vishroom_soup", true);
    MILK_BLOCK = Content.BLOCKS.register("milk", MilkSource::new);
    MILK_BUCKET = Content.item("milk_bucket", PouringMilkBucket::new);
    EssentiaBoiler.register(bus);
    FLUIDS.register(bus);
    TYPES.register(bus);
    bus.addListener(FluidModule::capabilities);
  }

  private static void capabilities(RegisterCapabilitiesEvent e) {
    e.registerBlockEntity(
        Capabilities.FluidHandler.BLOCK, TANK_TYPE.get(), (be, side) -> be.handler(side));
    e.registerItem(
        Capabilities.FluidHandler.ITEM,
        (stack, context) ->
            new net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper(stack),
        MILK_BUCKET.get());
  }

  public static final class GlyphItem extends Item {
    private final Glyph glyph;

    GlyphItem(Glyph glyph) {
      super(new Item.Properties());
      this.glyph = glyph;
    }

    @Override
    public void appendHoverText(
        ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
      lines.add(
          Component.translatable(
              "tooltip.autoturgy.glyph." + glyph.name().toLowerCase(Locale.ROOT)));
    }
  }

  public static final class TankBlock extends DeviceBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new TankEntity(p, s);
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
      if (!(level.getBlockEntity(pos) instanceof TankEntity tank))
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      for (var entry : GLYPHS.entrySet())
        if (stack.is(entry.getValue().get())) {
          if (!level.isClientSide && tank.apply(hit.getDirection(), entry.getKey())) {
            if (!player.isCreative()) stack.shrink(1);
          }
          return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
      if (player.isShiftKeyDown()
          && (stack.is(Items.STICK)
              || stack.getItem()
                  instanceof com.leclowndu93150.thaumaturge.content.wands.ItemWand)) {
        if (!level.isClientSide) tank.removeGlyph(hit.getDirection(), player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
      }
      if (serve(tank.handler(hit.getDirection()), stack, level, player, hand))
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
      if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection()))
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
      return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public void setPlacedBy(
        Level l, BlockPos p, BlockState s, LivingEntity placer, ItemStack stack) {
      if (l.getBlockEntity(p) instanceof TankEntity tank) {
        CompoundTag data =
            stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.contains("tankState"))
          tank.loadWithComponents(data.getCompound("tankState"), l.registryAccess());
      }
    }

    @Override
    protected void onRemove(
        BlockState s, Level l, BlockPos p, BlockState replacement, boolean moving) {
      if (!s.is(replacement.getBlock())
          && !l.isClientSide
          && l.getBlockEntity(p) instanceof TankEntity tank) {
        ItemStack drop = new ItemStack(this);
        CompoundTag data = new CompoundTag();
        data.put("tankState", tank.saveWithoutMetadata(l.registryAccess()));
        drop.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        net.minecraft.world.Containers.dropItemStack(
            l, p.getX() + .5, p.getY() + .5, p.getZ() + .5, drop);
      }
      super.onRemove(s, l, p, replacement, moving);
    }
  }

  public static final class TankEntity extends DeviceEntity implements DeviceBlock.Ticking {
    public final Glyph[] glyphs = new Glyph[6];
    public final FluidTank tank =
        new FluidTank(16000) {
          @Override
          protected void onContentsChanged() {
            changed();
          }
        };
    // Real drained fluid rejected by a changing destination remains here until returned.
    private FluidStack custody = FluidStack.EMPTY;

    public TankEntity(BlockPos p, BlockState s) {
      super(TANK_TYPE.get(), p, s);
    }

    public String kind() {
      return "thirsty_tank";
    }

    public int controlCount() {
      return 8;
    }

    public void configure(int i, int delta) {
      /* Glyphs require their physical items. */
    }

    public int count(Glyph g) {
      int n = 0;
      for (Glyph v : glyphs) if (v == g) n++;
      return n;
    }

    public int capacity() {
      return 16000 + count(Glyph.RESERVOIR) * 16000;
    }

    public boolean apply(Direction face, Glyph g) {
      if (glyphs[face.ordinal()] != null) return false;
      glyphs[face.ordinal()] = g;
      update();
      return true;
    }

    public boolean removeGlyph(Direction face, Player player) {
      Glyph g = glyphs[face.ordinal()];
      if (g == null) return false;
      if (g == Glyph.RESERVOIR && tank.getFluidAmount() > capacity() - 16000) {
        player.displayClientMessage(
            Component.translatable("message.autoturgy.reservoir_full"), true);
        return false;
      }
      glyphs[face.ordinal()] = null;
      update();
      ItemStack item = new ItemStack(GLYPHS.get(g).get());
      if (!player.addItem(item)) player.drop(item, false);
      return true;
    }

    private void update() {
      tank.setCapacity(capacity());
      for (int i = 0; i < 6; i++) settings[i] = glyphs[i] == null ? 0 : glyphs[i].ordinal() + 1;
      settings[6] = tank.getFluidAmount();
      settings[7] = capacity();
      settings[8] =
          net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(tank.getFluid().getFluid());
      changed();
    }

    public boolean intakeAllowed() {
      return count(Glyph.TEMPERANCE) == 0
          || level != null && level.hasNeighborSignal(worldPosition);
    }

    public IFluidHandler handler(Direction side) {
      return new IFluidHandler() {
        public int getTanks() {
          return 1;
        }

        public FluidStack getFluidInTank(int n) {
          return tank.getFluid().copy();
        }

        public int getTankCapacity(int n) {
          return capacity();
        }

        public boolean isFluidValid(int n, FluidStack s) {
          return !s.isEmpty();
        }

        public int fill(FluidStack s, FluidAction a) {
          if (s.isEmpty()
              || !intakeAllowed()
              || !tank.isEmpty() && !FluidStack.isSameFluidSameComponents(tank.getFluid(), s))
            return 0;
          int stored = tank.fill(s, a);
          if (a.execute()) update();
          return count(Glyph.VOID) > 0 ? s.getAmount() : stored;
        }

        public FluidStack drain(FluidStack s, FluidAction a) {
          return FluidStack.isSameFluidSameComponents(s, tank.getFluid())
              ? drain(s.getAmount(), a)
              : FluidStack.EMPTY;
        }

        public FluidStack drain(int amount, FluidAction a) {
          FluidStack result =
              tank.drain(
                  Math.min(
                      amount,
                      Math.max(0, tank.getFluidAmount() - (count(Glyph.PRESERVATION) > 0 ? 1 : 0))),
                  a);
          if (a.execute()) update();
          return result;
        }
      };
    }

    public void serverTick() {
      if (!custody.isEmpty()) {
        int returned = tank.fill(custody, IFluidHandler.FluidAction.EXECUTE);
        custody.shrink(returned);
        setChanged();
        if (!custody.isEmpty()) return;
      }
      int interval = Math.max(2, 20 / (1 + count(Glyph.GUZZLER)));
      if (level.getGameTime() % interval != 0) return;
      IFluidHandler own = handler(null);
      if (count(Glyph.CONSUMPTION) > 0)
        own.drain(count(Glyph.CONSUMPTION) * 100, IFluidHandler.FluidAction.EXECUTE);
      for (Direction side : Direction.values()) {
        Glyph g = glyphs[side.ordinal()];
        BlockPos p = worldPosition.relative(side);
        if (!level.hasChunkAt(p)) continue;
        if (g == Glyph.SIPHONING || g == Glyph.ENVY) {
          IFluidHandler other =
              level.getCapability(Capabilities.FluidHandler.BLOCK, p, side.getOpposite());
          if (other != null)
            move(g == Glyph.ENVY ? other : own, g == Glyph.ENVY ? own : other, 1000);
          if (!custody.isEmpty()) return;
        }
        if (g == Glyph.BOVINE) milk(side);
      }
      if (count(Glyph.BOVINE) == 0) absorb();
      if (level.isRainingAt(worldPosition.above()) && level.canSeeSky(worldPosition.above()))
        own.fill(new FluidStack(Fluids.WATER, 25), IFluidHandler.FluidAction.EXECUTE);
      update();
    }

    private void move(IFluidHandler from, IFluidHandler to, int limit) {
      FluidStack candidate = from.drain(limit, IFluidHandler.FluidAction.SIMULATE);
      if (candidate.isEmpty()) return;
      int accepted = to.fill(candidate, IFluidHandler.FluidAction.SIMULATE);
      if (accepted <= 0) return;
      FluidStack actual = from.drain(Math.min(limit, accepted), IFluidHandler.FluidAction.EXECUTE);
      if (actual.isEmpty()) return;
      int moved = to.fill(actual, IFluidHandler.FluidAction.EXECUTE);
      actual.shrink(moved);
      if (!actual.isEmpty()) {
        int back = from.fill(actual, IFluidHandler.FluidAction.EXECUTE);
        actual.shrink(back);
        if (!actual.isEmpty()) {
          custody = actual;
          setChanged();
        }
      }
    }

    public boolean milk(Direction side) {
      if (!intakeAllowed()) return false;
      AABB box =
          new AABB(worldPosition.relative(side))
              .expandTowards(side.getStepX() * 3, side.getStepY() * 3, side.getStepZ() * 3)
              .inflate(.25);
      for (Cow cow : level.getEntitiesOfClass(Cow.class, box, c -> c.isAlive() && !c.isBaby())) {
        CompoundTag data = cow.getPersistentData();
        long until = data.getLong("autoturgyMilkUntil");
        if (until > level.getGameTime()) continue;
        FluidStack milk = new FluidStack(NeoForgeMod.MILK.get(), 1000);
        if (handler(side).fill(milk, IFluidHandler.FluidAction.SIMULATE) < 1000) continue;
        if (handler(side).fill(milk, IFluidHandler.FluidAction.EXECUTE) == 1000) {
          data.putLong("autoturgyMilkUntil", level.getGameTime() + 1200);
          return true;
        }
      }
      return false;
    }

    public boolean absorb() {
      if (!intakeAllowed()) return false;
      for (Direction side : Direction.values()) {
        BlockPos start = worldPosition.relative(side);
        if (!level.hasChunkAt(start)) continue;
        FluidState initial = level.getFluidState(start);
        if (initial.isEmpty()) continue;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < 256) {
          BlockPos p = queue.removeFirst();
          if (!seen.add(p) || !level.hasChunkAt(p) || p.distManhattan(worldPosition) > 16) continue;
          FluidState f = level.getFluidState(p);
          if (f.isEmpty() || !initial.getType().isSame(f.getType())) continue;
          if (f.isSource() && level.getBlockState(p).getBlock() instanceof BucketPickup pickup) {
            FluidStack offered = new FluidStack(f.getType(), 1000);
            if (f.getType().getBucket() != Items.AIR
                && handler(side).fill(offered, IFluidHandler.FluidAction.SIMULATE) >= 1000) {
              boolean renewable = f.is(Fluids.WATER) && p.distManhattan(worldPosition) == 1;
              if (renewable) {
                handler(side).fill(offered, IFluidHandler.FluidAction.EXECUTE);
                return true;
              }
              // The tank's own insertion cannot change between this check and commit on the server
              // thread.
              ItemStack bucket = pickup.pickupBlock(null, level, p, level.getBlockState(p));
              if (!bucket.isEmpty()) {
                // Pickup may synchronously invoke neighbor callbacks that change redstone or
                // storage.
                int stored = handler(side).fill(offered, IFluidHandler.FluidAction.EXECUTE);
                if (stored < 1000) {
                  custody = offered.copyWithAmount(1000 - stored);
                  setChanged();
                }
                return true;
              }
            }
          }
          for (Direction d : Direction.values()) queue.add(p.relative(d));
        }
      }
      return false;
    }

    @Override
    public int comparator() {
      return tank.isEmpty() ? 0 : 1 + (int) (14L * tank.getFluidAmount() / capacity());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      tag.put("fluid", tank.writeToNBT(lookup, new CompoundTag()));
      int[] gs = new int[6];
      for (int i = 0; i < 6; i++) gs[i] = glyphs[i] == null ? -1 : glyphs[i].ordinal();
      tag.putIntArray("glyphs", gs);
      if (!custody.isEmpty()) tag.put("custody", custody.save(lookup));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      int[] gs = tag.getIntArray("glyphs");
      for (int i = 0; i < 6; i++)
        glyphs[i] =
            i < gs.length && gs[i] >= 0 && gs[i] < Glyph.values().length
                ? Glyph.values()[gs[i]]
                : null;
      tank.setCapacity(capacity());
      tank.readFromNBT(lookup, tag.getCompound("fluid"));
      custody = FluidStack.parseOptional(lookup, tag.getCompound("custody"));
    }
  }

  public static boolean serve(
      IFluidHandler handler, ItemStack stack, Level level, Player player, InteractionHand hand) {
    FluidStack input = FluidStack.EMPTY;
    ItemStack result = ItemStack.EMPTY;
    boolean filling = false;
    if (stack.is(Items.POTION)
        && stack
            .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
            .is(Potions.WATER)) {
      input = new FluidStack(Fluids.WATER, 250);
      result = new ItemStack(Items.GLASS_BOTTLE);
      filling = true;
    } else if (stack.is(Items.MUSHROOM_STEW)) {
      input = new FluidStack(MUSHROOM.source.get(), 250);
      result = new ItemStack(Items.BOWL);
      filling = true;
    } else if (stack.is(VISHROOM.bowl.get())) {
      input = new FluidStack(VISHROOM.source.get(), 250);
      result = new ItemStack(Items.BOWL);
      filling = true;
    } else if (stack.is(Items.GLASS_BOTTLE)) {
      input = new FluidStack(Fluids.WATER, 250);
      result = PotionContents.createItemStack(Items.POTION, Potions.WATER);
    } else if (stack.is(Items.BOWL)) {
      FluidStack held = handler.getFluidInTank(0);
      if (held.is(MUSHROOM.source.get())) {
        input = new FluidStack(MUSHROOM.source.get(), 250);
        result = new ItemStack(Items.MUSHROOM_STEW);
      } else if (held.is(VISHROOM.source.get())) {
        input = new FluidStack(VISHROOM.source.get(), 250);
        result = new ItemStack(VISHROOM.bowl.get());
      }
    }
    if (input.isEmpty()) return false;
    boolean allowed =
        filling
            ? handler.fill(input, IFluidHandler.FluidAction.SIMULATE) == 250
            : handler.drain(input, IFluidHandler.FluidAction.SIMULATE).getAmount() == 250;
    if (!allowed) return false;
    if (!level.isClientSide) {
      if (filling) handler.fill(input, IFluidHandler.FluidAction.EXECUTE);
      else handler.drain(input, IFluidHandler.FluidAction.EXECUTE);
      player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, result));
    }
    return true;
  }

  public static final class MilkSource extends Block implements BucketPickup {
    MilkSource() {
      super(Properties.of().noCollission().strength(100).noLootTable().replaceable());
    }

    @Override
    protected FluidState getFluidState(BlockState s) {
      return NeoForgeMod.MILK.get().defaultFluidState();
    }

    @Override
    protected RenderShape getRenderShape(BlockState s) {
      return RenderShape.INVISIBLE;
    }

    public ItemStack pickupBlock(Player player, LevelAccessor l, BlockPos p, BlockState s) {
      l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
      return new ItemStack(Items.MILK_BUCKET);
    }

    public java.util.Optional<net.minecraft.sounds.SoundEvent> getPickupSound() {
      return java.util.Optional.of(net.minecraft.sounds.SoundEvents.BUCKET_FILL);
    }
  }

  public static final class PouringMilkBucket extends MilkBucketItem {
    PouringMilkBucket() {
      super(new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
      if (!p.isShiftKeyDown()) return super.use(l, p, h);
      BlockHitResult hit = getPlayerPOVHitResult(l, p, ClipContext.Fluid.NONE);
      BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
      if (hit.getType() == HitResult.Type.BLOCK
          && l.mayInteract(p, pos)
          && p.mayUseItemAt(pos, hit.getDirection(), p.getItemInHand(h))
          && l.getBlockState(pos).canBeReplaced()) {
        if (!l.isClientSide) {
          l.setBlockAndUpdate(pos, MILK_BLOCK.get().defaultBlockState());
          if (!p.isCreative()) p.setItemInHand(h, new ItemStack(Items.BUCKET));
        }
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(h), l.isClientSide);
      }
      return InteractionResultHolder.fail(p.getItemInHand(h));
    }
  }

  private FluidModule() {}
}
