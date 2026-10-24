/* Owns only essentia physically fed into a construct and its six host ports.
 * Shared wand activation and persistence keep every family server-authoritative. */
package com.sanhiruzu.autoturgy.constructs;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.essentia.*;
import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import com.leclowndu93150.thaumaturge.api.wands.WandAccess;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import com.sanhiruzu.autoturgy.Content;
import com.sanhiruzu.autoturgy.common.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

public abstract class ConstructEntity extends DeviceEntity implements DeviceBlock.Ticking {
  private AspectList fuel = AspectList.EMPTY;
  private long revision;
  public boolean active;
  public int operations;
  public String failure = "inactive";

  protected ConstructEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
    Arrays.fill(settings, 0, 6, 1);
  }

  public abstract List<ResourceKey<IAspect>> fuels();

  public abstract ResourceKey<IAspect> activationAspect();

  public abstract int activationCost();

  public abstract boolean validStart();

  public abstract String research();

  @Override
  public List<com.sanhiruzu.autoturgy.host.ThaumaturgeIntegration.AspectReading> menuAspects() {
    return fuels().stream()
        .map(
            k ->
                new com.sanhiruzu.autoturgy.host.ThaumaturgeIntegration.AspectReading(
                    k.location().toString(), amount(k), 64))
        .toList();
  }

  public int controlCount() {
    return 6;
  }

  public void configure(int i, int delta) {
    if (i >= 0 && i < 6) cycle(i, delta, fuels().size() + 1);
  }

  // Generic menu and inherited sneak-empty-hand gestures can only stop.
  public final void activate() {}

  public void cancel() {
    active = false;
    failure = "inactive";
    changed();
  }

  public final boolean start(Player player, InteractionHand hand) {
    if (level == null || level.isClientSide || !usable(player)) return false;
    if (active) {
      cancel();
      return true;
    }
    ItemStack wand = player.getItemInHand(hand);
    if (!(wand.getItem() instanceof ItemWand)
        || !ResearchGate.passes(
            player, new ResearchGate(Content.id(research()), Optional.empty(), false))
        || !validStart()) return false;
    int cost = activationCost();
    if (WandAccess.getVis(wand, activationAspect()) < cost) {
      failure = "wand_vis";
      return false;
    }
    player.setItemInHand(
        hand,
        WandAccess.withVis(
            wand, activationAspect(), WandAccess.getVis(wand, activationAspect()) - cost));
    active = true;
    failure = "ready";
    changed();
    return true;
  }

  public int amount(ResourceKey<IAspect> key) {
    return level == null ? 0 : fuel.amountOf(Aspects.resolve(level, key));
  }

  public boolean has(Map<ResourceKey<IAspect>, Integer> cost) {
    return cost.entrySet().stream().allMatch(e -> amount(e.getKey()) >= e.getValue());
  }

  public void debit(Map<ResourceKey<IAspect>, Integer> cost) {
    cost.forEach((k, n) -> fuel = fuel.reduce(Aspects.resolve(level, k), n));
    revision++;
    changed();
  }

  public Port port(Direction side) {
    return new Port(side);
  }

  public Holder<IAspect> selection(Direction side) {
    int selected = side == null ? 0 : settings[side.ordinal()];
    return selected <= 0 || selected > fuels().size()
        ? null
        : Aspects.resolve(level, fuels().get(selected - 1));
  }

  public void intake() {
    if (level.getGameTime() % 5 != 0) return;
    for (Direction side : Direction.values()) {
      Holder<IAspect> aspect = selection(side);
      BlockPos adjacent = worldPosition.relative(side);
      if (aspect == null || !level.hasChunkAt(adjacent)) continue;
      if (level.getBlockEntity(adjacent)
              instanceof com.leclowndu93150.thaumaturge.api.aspect.IAspectSource host
          && host.isBlocked()) continue;
      IEssentiaConduit source =
          level.getCapability(EssentiaCapabilities.CONDUIT, adjacent, side.getOpposite());
      Port own = new Port(side);
      if (source == null
          || !source.releasesOn(side.getOpposite())
          || source.pull(side.getOpposite()).strength() >= own.getSuctionAmount(side)
          || source.pullThreshold() > own.getSuctionAmount(side)
          || own.insert(aspect, 1, true) != 1) continue;
      int extracted = source.extract(aspect, 1, side.getOpposite(), false);
      if (extracted > 0) own.insert(aspect, extracted, false);
    }
  }

  public void status() {
    settings[6] = active ? 1 : 0;
    settings[7] = fuel.totalAmount();
    settings[8] = operations;
    output(active && failure.equals("ready") ? 15 : 0);
    progress = Math.min(15, fuel.totalAmount() / 16);
    changed();
  }

  public boolean jar(Player player, InteractionHand hand, Direction face) {
    ItemStack stack = player.getItemInHand(hand);
    var storage = stack.getCapability(EssentiaCapabilities.ITEM_STORAGE);
    var aspect = selection(face);
    if (storage == null || aspect == null || stack.getCount() != 1) return false;
    if (level.isClientSide) return true;
    if (player.isShiftKeyDown()) {
      int n = Math.min(8, fuel.amountOf(aspect));
      if (n <= 0 || !storage.canInsert(aspect)) return true;
      var moved = storage.insert(aspect, n);
      if (moved.amountMoved() > 0) {
        player.setItemInHand(hand, moved.resultingStack());
        fuel = fuel.reduce(aspect, moved.amountMoved());
        revision++;
        changed();
      }
    } else {
      int room = new Port(face).insert(aspect, 8, true);
      if (room <= 0) return true;
      var moved = storage.extract(aspect, room);
      if (moved.amountMoved() > 0) {
        player.setItemInHand(hand, moved.resultingStack());
        new Port(face).insert(aspect, moved.amountMoved(), false);
      }
    }
    return true;
  }

  public final class Port implements IEssentiaStorage, IEssentiaTransport {
    private final Direction face;

    Port(Direction face) {
      this.face = face;
    }

    public AspectList contents() {
      return fuel;
    }

    public int insert(Holder<IAspect> a, int n, boolean simulate) {
      if (level == null || level.isClientSide || !Objects.equals(a, selection(face))) return 0;
      int moved =
          Math.clamp(n, 0, Math.max(0, Math.min(64 - fuel.amountOf(a), 256 - fuel.totalAmount())));
      if (!simulate && moved > 0) {
        fuel = fuel.add(a, moved);
        revision++;
        changed();
      }
      return moved;
    }

    public int extract(Holder<IAspect> a, int n, boolean simulate) {
      return 0;
    }

    public long contentRevision() {
      return revision;
    }

    public int addEssentia(Holder<IAspect> a, int n, Direction s) {
      return s == face ? insert(a, n, false) : 0;
    }

    public int takeEssentia(Holder<IAspect> a, int n, Direction s) {
      return 0;
    }

    public int addEssentia(Holder<IAspect> a, int n, Direction s, boolean sim) {
      return s == face ? insert(a, n, sim) : 0;
    }

    public int getEssentiaAmount(Direction s) {
      var a = getEssentiaType(s);
      return a == null ? 0 : fuel.amountOf(a);
    }

    public Holder<IAspect> getEssentiaType(Direction s) {
      return s == face ? selection(face) : null;
    }

    public void setSuction(Holder<IAspect> a, int n) {}

    public Holder<IAspect> getSuctionType(Direction s) {
      return getEssentiaType(s);
    }

    public int getSuctionAmount(Direction s) {
      return s == face
              && selection(face) != null
              && fuel.totalAmount() < 256
              && fuel.amountOf(selection(face)) < 64
          ? 64
          : 0;
    }

    public int getMinimumSuction() {
      return 0;
    }

    public boolean canOutputTo(Direction s) {
      return false;
    }

    public boolean canInputFrom(Direction s) {
      return s == face && selection(face) != null;
    }

    public boolean isConnectable(Direction s) {
      return s == face && selection(face) != null;
    }
  }

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
    super.saveAdditional(tag, lookup);
    tag.putBoolean("active", active);
    tag.putInt("operations", operations);
    tag.put(
        "fuel",
        AspectList.CODEC
            .encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), fuel)
            .getOrThrow());
  }

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
    super.loadAdditional(tag, lookup);
    active = tag.getBoolean("active");
    operations = Math.max(0, tag.getInt("operations"));
    fuel =
        tag.contains("fuel")
            ? AspectList.CODEC
                .parse(lookup.createSerializationContext(NbtOps.INSTANCE), tag.get("fuel"))
                .result()
                .orElse(AspectList.EMPTY)
            : AspectList.EMPTY;
    for (int i = 0; i < 6; i++) settings[i] = Math.clamp(settings[i], 0, fuels().size());
  }

  public abstract static class ConstructBlock extends DeviceBlock {
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState s,
        Level l,
        BlockPos p,
        Player player,
        InteractionHand hand,
        BlockHitResult hit) {
      if (!(l.getBlockEntity(p) instanceof ConstructEntity c)) return ItemInteractionResult.FAIL;
      if (stack.getItem() instanceof ItemWand) {
        if (!l.isClientSide) {
          boolean started = c.start(player, hand);
          player.displayClientMessage(
              net.minecraft.network.chat.Component.translatable(
                  "message.autoturgy.construct."
                      + (started ? c.active ? "started" : "stopped" : c.failure)),
              true);
        }
        return ItemInteractionResult.sidedSuccess(l.isClientSide);
      }
      if (c.jar(player, hand, hit.getDirection()))
        return ItemInteractionResult.sidedSuccess(l.isClientSide);
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    protected List<ItemStack> getDrops(BlockState s, LootParams.Builder params) {
      ItemStack drop = new ItemStack(this);
      if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
          instanceof ConstructEntity c) {
        CompoundTag saved = c.saveWithFullMetadata(params.getLevel().registryAccess());
        saved.putBoolean("active", false);
        drop.set(
            net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
            net.minecraft.world.item.component.CustomData.of(saved));
      }
      return List.of(drop);
    }
  }
}
