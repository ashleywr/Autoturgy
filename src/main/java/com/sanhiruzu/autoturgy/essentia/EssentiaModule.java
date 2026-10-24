/* A continuously validated locus indexes original jars without owning essentia.
 * Its embedded-cluster aggregator delegates real host tube and infusion extraction. */
package com.sanhiruzu.autoturgy.essentia;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.essentia.*;
import com.leclowndu93150.thaumaturge.content.essentia.storage.SingleAspectEssentiaHost;
import com.sanhiruzu.autoturgy.Content;
import com.sanhiruzu.autoturgy.common.*;
import com.sanhiruzu.autoturgy.host.ThaumaturgeIntegration;
import com.sanhiruzu.autoturgy.sensing.SensingModule;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;

public final class EssentiaModule {
  public static DeferredBlock<LocusBlock> LOCUS;
  public static DeferredBlock<AggregatorBlock> AGGREGATOR;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<LocusEntity>> LOCUS_TYPE;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<AggregatorEntity>>
      AGGREGATOR_TYPE;

  public record Structure(boolean valid, int extras) {
    public int containers() {
      return 12 + 8 * extras;
    }

    public int eyes() {
      return 1 + extras;
    }

    public int range() {
      return 16 + 8 * extras;
    }
  }

  public record Endpoint(
      BlockPos pos, Direction side, IEssentiaStorage storage, IEssentiaConduit conduit) {}

  public static void register(IEventBus bus) {
    LOCUS = Content.block("essentia_locus", LocusBlock::new);
    AGGREGATOR = Content.block("essentia_aggregator", AggregatorBlock::new);
    LOCUS_TYPE =
        Content.ENTITIES.register(
            "essentia_locus",
            () -> BlockEntityType.Builder.of(LocusEntity::new, LOCUS.get()).build(null));
    AGGREGATOR_TYPE =
        Content.ENTITIES.register(
            "essentia_aggregator",
            () -> BlockEntityType.Builder.of(AggregatorEntity::new, AGGREGATOR.get()).build(null));
    bus.addListener(EssentiaModule::capabilities);
  }

  private static void capabilities(RegisterCapabilitiesEvent e) {
    e.registerBlockEntity(EssentiaCapabilities.STORAGE, AGGREGATOR_TYPE.get(), (be, side) -> be);
    e.registerBlockEntity(EssentiaCapabilities.CONDUIT, AGGREGATOR_TYPE.get(), (be, side) -> be);
    e.registerBlockEntity(EssentiaCapabilities.TRANSPORT, AGGREGATOR_TYPE.get(), (be, side) -> be);
    e.registerBlockEntity(
        EssentiaCapabilities.ASPECT_QUERY, AGGREGATOR_TYPE.get(), (be, side) -> be::getAspects);
    e.registerBlockEntity(AspectCapabilities.CONTAINER, AGGREGATOR_TYPE.get(), (be, side) -> be);
  }

  public static Block host(String path) {
    return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("thaumaturge", path));
  }

  public static Structure structure(Level level, BlockPos locus) {
    if (!level.hasChunkAt(locus) || !level.getBlockState(locus).is(LOCUS.get()))
      return new Structure(false, 0);
    BlockPos base = locus.below(2);
    Block central = level.getBlockState(locus.below()).getBlock();
    if (!(central == host("crystal_aqua") || central == AGGREGATOR.get())
        || !level.getBlockState(base).is(Blocks.LAPIS_BLOCK)) return new Structure(false, 0);
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++) {
        if (x == 0 && z == 0) continue;
        BlockPos p = base.offset(x, 0, z);
        if (!level.hasChunkAt(p)) return new Structure(false, 0);
        if (Math.max(Math.abs(x), Math.abs(z)) == 1) {
          if (!level.getFluidState(p).is(Fluids.WATER) || !level.getFluidState(p).isSource())
            return new Structure(false, 0);
        } else if (!level.getBlockState(p).is(host("plank_silverwood")))
          return new Structure(false, 0);
      }
    int extras = 0;
    for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, -1, -3), base.offset(3, 1, 3))) {
      if (p.equals(locus.below())
          || !level.hasChunkAt(p)
          || !level.getBlockState(p).is(host("crystal_aqua"))) continue;
      for (Direction d : Direction.values()) {
        BlockPos q = p.relative(d);
        if (q.getY() == base.getY()
            && Math.max(Math.abs(q.getX() - base.getX()), Math.abs(q.getZ() - base.getZ())) == 2
            && level.hasChunkAt(q)
            && level.getBlockState(q).is(host("plank_silverwood"))) {
          extras++;
          break;
        }
      }
    }
    return new Structure(true, Math.min(8, extras));
  }

  public static LocusEntity at(Level level, BlockPos pos) {
    if (!level.hasChunkAt(pos)) return null;
    if (level.getBlockEntity(pos) instanceof LocusEntity locus) return locus;
    if (level.getBlockEntity(pos.above()) instanceof LocusEntity locus) return locus;
    // A reader under any of the eight water blocks sees the same construct.
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++)
        for (int y = 2; y <= 3; y++) {
          BlockPos p = pos.offset(x, y, z);
          if (level.hasChunkAt(p) && level.getBlockEntity(p) instanceof LocusEntity locus) {
            BlockPos water = p.below(2);
            if (Math.abs(pos.getX() - water.getX()) <= 1
                && Math.abs(pos.getZ() - water.getZ()) <= 1
                && (pos.getY() == water.getY() || pos.getY() == water.getY() - 1)) return locus;
          }
        }
    return null;
  }

  public static ThaumaturgeIntegration.Snapshot reading(Level level, BlockPos pos) {
    LocusEntity locus = at(level, pos);
    return locus == null ? null : locus.reading();
  }

  public static final class LocusBlock extends DeviceBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new LocusEntity(p, s);
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
      if (stack.is(SensingModule.EYE.get()) && l.getBlockEntity(p) instanceof LocusEntity locus) {
        if (!l.isClientSide) {
          var target = LoadedTargets.Target.load(SensingModule.itemData(stack));
          if (target.isPresent() && locus.addEye(target.get())) {
            if (!player.isCreative()) stack.shrink(1);
          }
        }
        return ItemInteractionResult.sidedSuccess(l.isClientSide);
      }
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
      if (l.getBlockEntity(p) instanceof LocusEntity locus && !l.isClientSide) {
        if (player.isShiftKeyDown()
            && player instanceof net.minecraft.server.level.ServerPlayer server)
          server.openMenu(locus, buf -> ControlMenu.writeOpening(buf, locus));
        else if (!locus.eyes.isEmpty()) {
          LoadedTargets.Target eye = locus.eyes.removeLast();
          ItemStack item = new ItemStack(SensingModule.EYE.get());
          item.set(
              net.minecraft.core.component.DataComponents.CUSTOM_DATA,
              net.minecraft.world.item.component.CustomData.of(eye.save()));
          if (!player.addItem(item)) player.drop(item, false);
          locus.changed();
        }
      }
      return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Override
    protected void onRemove(BlockState s, Level l, BlockPos p, BlockState other, boolean moving) {
      if (!s.is(other.getBlock())
          && !l.isClientSide
          && l.getBlockEntity(p) instanceof LocusEntity locus)
        for (var eye : locus.eyes) {
          ItemStack item = new ItemStack(SensingModule.EYE.get());
          item.set(
              net.minecraft.core.component.DataComponents.CUSTOM_DATA,
              net.minecraft.world.item.component.CustomData.of(eye.save()));
          net.minecraft.world.Containers.dropItemStack(
              l, p.getX() + .5, p.getY() + .5, p.getZ() + .5, item);
        }
      super.onRemove(s, l, p, other, moving);
    }
  }

  public static final class LocusEntity extends DeviceEntity implements DeviceBlock.Ticking {
    public final List<LoadedTargets.Target> eyes = new ArrayList<>();

    public LocusEntity(BlockPos p, BlockState s) {
      super(LOCUS_TYPE.get(), p, s);
    }

    public String kind() {
      return "essentia_locus";
    }

    public int controlCount() {
      return 4;
    }

    public void configure(int i, int d) {}

    public boolean addEye(LoadedTargets.Target eye) {
      Structure s = structure(level, worldPosition);
      if (!s.valid
          || eyes.size() >= s.eyes()
          || !LoadedTargets.local(level, worldPosition, eye, s.range())
          || endpoint(eye.pos(), eye.side()) == null) return false;
      eyes.add(eye);
      changed();
      return true;
    }

    private Endpoint endpoint(BlockPos p, Direction side) {
      if (!level.hasChunkAt(p)) return null;
      BlockEntity be = level.getBlockEntity(p);
      if (!(be instanceof com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar
          || be
              instanceof
              com.leclowndu93150.thaumaturge.content.essentia.reservoir
                  .BlockEntityEssentiaReservoir)) return null;
      IEssentiaStorage store = level.getCapability(EssentiaCapabilities.STORAGE, p, side);
      IEssentiaConduit conduit = level.getCapability(EssentiaCapabilities.CONDUIT, p, side);
      if (store == null
          && be
              instanceof
              com.leclowndu93150.thaumaturge.content.essentia.reservoir.BlockEntityEssentiaReservoir
                      reservoir) {
        // Reservoirs expose conduit and aspect-source APIs, but no STORAGE capability in 1.0.4.
        conduit = reservoir;
        store =
            new IEssentiaStorage() {
              public AspectList contents() {
                return reservoir.contents();
              }

              public long contentRevision() {
                return level.getGameTime();
              }

              public int insert(Holder<IAspect> a, int n, boolean sim) {
                return 0;
              }

              public int extract(Holder<IAspect> a, int n, boolean sim) {
                return reservoir.releasesOn(side) ? reservoir.extract(a, n, side, sim) : 0;
              }
            };
      }
      if (store == null) return null;
      return new Endpoint(p.immutable(), side, store, conduit);
    }

    public List<Endpoint> discover() {
      Structure s = structure(level, worldPosition);
      if (!s.valid) return List.of();
      List<Endpoint> result = new ArrayList<>();
      Set<BlockPos> seen = new HashSet<>();
      Set<BlockEntity> entities = Collections.newSetFromMap(new IdentityHashMap<>());
      for (var eye : eyes.stream().limit(s.eyes()).toList()) {
        if (!LoadedTargets.local(level, worldPosition, eye, s.range())) continue;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(eye.pos());
        while (!queue.isEmpty() && result.size() < s.containers() && seen.size() < 512) {
          BlockPos p = queue.removeFirst();
          if (!seen.add(p)
              || !level.hasChunkAt(p)
              || !LoadedTargets.withinRange(worldPosition, p, s.range())) continue;
          Endpoint endpoint = endpoint(p, eye.side());
          if (endpoint == null) continue;
          if (entities.add(level.getBlockEntity(p))) result.add(endpoint);
          for (Direction d : Direction.values()) queue.add(p.relative(d));
        }
      }
      return List.copyOf(result);
    }

    public AspectList contents() {
      AspectList sum = AspectList.EMPTY;
      for (Endpoint e : discover()) sum = sum.add(e.storage.contents());
      return sum;
    }

    public ThaumaturgeIntegration.Snapshot reading() {
      if (!structure(level, worldPosition).valid) return ThaumaturgeIntegration.Snapshot.invalid();
      Map<String, int[]> totals = new TreeMap<>();
      for (Endpoint e : discover()) {
        BlockEntity be = level.getBlockEntity(e.pos);
        AspectList contents = e.storage.contents();
        for (AspectInstance a : contents.entries())
          totals
                  .computeIfAbsent(
                      a.aspect().unwrapKey().orElseThrow().location().toString(), k -> new int[2])[
                  0] +=
              a.amount();
        if (be instanceof SingleAspectEssentiaHost host) {
          var key = host.aspectKey() != null ? host.aspectKey() : host.aspectFilterKey();
          if (key != null)
            totals.computeIfAbsent(key.location().toString(), k -> new int[2])[1] +=
                host.capacity();
        } else if (be
            instanceof
            com.leclowndu93150.thaumaturge.content.essentia.reservoir.BlockEntityEssentiaReservoir)
          for (AspectInstance a : contents.entries())
            totals
                    .computeIfAbsent(
                        a.aspect().unwrapKey().orElseThrow().location().toString(),
                        k -> new int[2])[1] +=
                com.leclowndu93150.thaumaturge.content.essentia.reservoir
                    .BlockEntityEssentiaReservoir.CAPACITY;
        else if (level.getBlockState(e.pos).getBlock() instanceof IEssentiaJar jar)
          for (AspectInstance a : contents.entries())
            totals
                    .computeIfAbsent(
                        a.aspect().unwrapKey().orElseThrow().location().toString(),
                        k -> new int[2])[1] +=
                jar.jarCapacity();
      }
      return new ThaumaturgeIntegration.Snapshot(
          totals.entrySet().stream()
              .map(
                  e ->
                      new ThaumaturgeIntegration.AspectReading(
                          e.getKey(), e.getValue()[0], e.getValue()[1]))
              .toList(),
          true,
          true,
          true,
          true);
    }

    public void serverTick() {
      if (level.getGameTime() % 20 == 0) {
        Structure s = structure(level, worldPosition);
        settings[0] = s.valid ? 1 : 0;
        settings[1] = discover().size();
        settings[2] = eyes.size();
        settings[3] = contents().totalAmount();
        progress = Math.min(15, settings[1]);
        changed();
      }
    }

    @Override
    public int comparator() {
      return structure(level, worldPosition).valid ? Math.min(15, contents().totalAmount()) : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      ListTag list = new ListTag();
      eyes.forEach(e -> list.add(e.save()));
      tag.put("eyes", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      eyes.clear();
      ListTag list = tag.getList("eyes", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(9, list.size()); i++)
        LoadedTargets.Target.load(list.getCompound(i)).ifPresent(eyes::add);
    }
  }

  public static final class AggregatorBlock extends DeviceBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new AggregatorEntity(p, s);
    }
  }

  public static final class AggregatorEntity extends DeviceEntity
      implements IEssentiaStorage, IEssentiaTransport, IAspectSource {
    public AggregatorEntity(BlockPos p, BlockState s) {
      super(AGGREGATOR_TYPE.get(), p, s);
    }

    public String kind() {
      return "essentia_aggregator";
    }

    public int controlCount() {
      return 0;
    }

    public void configure(int i, int d) {}

    public LocusEntity locus() {
      return level != null
              && level.hasChunkAt(worldPosition.above())
              && level.getBlockEntity(worldPosition.above()) instanceof LocusEntity locus
              && structure(level, locus.getBlockPos()).valid
          ? locus
          : null;
    }

    public AspectList contents() {
      LocusEntity locus = locus();
      return locus == null ? AspectList.EMPTY : locus.contents();
    }

    public long contentRevision() {
      return level == null ? 0 : level.getGameTime();
    }

    public int insert(Holder<IAspect> aspect, int n, boolean simulate) {
      return 0;
    }

    public int extract(Holder<IAspect> aspect, int n, boolean simulate) {
      LocusEntity locus = locus();
      if (locus == null || n <= 0) return 0;
      int moved = 0;
      for (Endpoint e : locus.discover()) {
        if (e.conduit != null && !e.conduit.releasesOn(e.side)
            || level.getBlockEntity(e.pos) instanceof IAspectSource source && source.isBlocked())
          continue;
        int take = e.storage.extract(aspect, n - moved, simulate);
        moved += Math.clamp(take, 0, n - moved);
        if (moved >= n) break;
      }
      return moved;
    }

    public AspectList getAspects() {
      return contents();
    }

    public void setAspects(AspectList list) {}

    public boolean accepts(Holder<IAspect> a) {
      return false;
    }

    public int fill(Holder<IAspect> a, int n) {
      return n;
    }

    public boolean drain(Holder<IAspect> a, int n) {
      return n > 0 && extract(a, n, true) == n && extract(a, n, false) == n;
    }

    public boolean isBlocked() {
      return locus() == null;
    }

    public int addEssentia(Holder<IAspect> a, int n, Direction d) {
      return 0;
    }

    public int takeEssentia(Holder<IAspect> a, int n, Direction d) {
      return extract(a, n, false);
    }

    public int takeEssentia(Holder<IAspect> a, int n, Direction d, boolean simulate) {
      return extract(a, n, simulate);
    }

    public Holder<IAspect> getEssentiaType(Direction d) {
      return contents().isEmpty() ? null : contents().entries().getFirst().aspect();
    }

    public int getEssentiaAmount(Direction d) {
      Holder<IAspect> a = getEssentiaType(d);
      return a == null ? 0 : contents().amountOf(a);
    }

    public void setSuction(Holder<IAspect> a, int n) {}

    public Holder<IAspect> getSuctionType(Direction d) {
      return null;
    }

    public int getSuctionAmount(Direction d) {
      return 0;
    }

    public int getMinimumSuction() {
      return 0;
    }

    public boolean canOutputTo(Direction d) {
      return locus() != null;
    }

    public boolean canInputFrom(Direction d) {
      return false;
    }

    public boolean isConnectable(Direction d) {
      return locus() != null;
    }
  }

  private EssentiaModule() {}
}
