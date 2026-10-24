/* Crafts registered ordinary recipes from real storage for linked host empty-role golems.
 * Ghost patterns never own items; removable upgrades affect paid work and recovery. */
package com.sanhiruzu.autoturgy.golems;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import com.leclowndu93150.thaumaturge.registry.TTGolemTraits;
import com.sanhiruzu.autoturgy.Content;
import com.sanhiruzu.autoturgy.common.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.registries.*;

public final class GolemWorkbench {
  public enum Upgrade {
    AIR,
    FIRE,
    EARTH,
    WATER,
    ORDER,
    ENTROPY
  }

  public static DeferredBlock<BenchBlock> BLOCK;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BenchEntity>> TYPE;
  public static final Map<Upgrade, DeferredItem<Item>> UPGRADES = new EnumMap<>(Upgrade.class);

  static void register(IEventBus bus) {
    BLOCK = Content.block("golem_workbench", BenchBlock::new);
    TYPE =
        Content.ENTITIES.register(
            "golem_workbench",
            () -> BlockEntityType.Builder.of(BenchEntity::new, BLOCK.get()).build(null));
    for (var u : Upgrade.values())
      UPGRADES.put(
          u,
          Content.item(
              "workbench_upgrade_" + u.name().toLowerCase(Locale.ROOT),
              () -> new Item(new Item.Properties())));
    WorkbenchMenu.register();
    bus.addListener(GolemWorkbench::capabilities);
  }

  private static void capabilities(RegisterCapabilitiesEvent e) {
    e.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK,
        TYPE.get(),
        (be, side) -> side == Direction.DOWN ? be.output : be.ingredients);
  }

  public static final class BenchBlock extends GolemModule.GolemBlock {
    BenchBlock() {
      super();
    }

    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new BenchEntity(p, s);
    }

    protected InteractionResult useWithoutItem(
        BlockState s, Level level, BlockPos p, Player player, BlockHitResult hit) {
      if (!(level.getBlockEntity(p) instanceof BenchEntity be) || !be.authorized(player))
        return InteractionResult.FAIL;
      if (!level.isClientSide) {
        if (player.isShiftKeyDown() && hit.getDirection().getAxis().isHorizontal())
          be.removeUpgrade(hit.getDirection(), player);
        else if (player instanceof ServerPlayer server)
          server.openMenu(be, buf -> buf.writeBlockPos(p));
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
    }

    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState s,
        Level level,
        BlockPos p,
        Player player,
        InteractionHand hand,
        BlockHitResult hit) {
      if (!(level.getBlockEntity(p) instanceof BenchEntity be) || !be.authorized(player))
        return ItemInteractionResult.FAIL;
      for (var entry : UPGRADES.entrySet())
        if (stack.is(entry.getValue().get())) {
          if (!level.isClientSide && be.install(hit.getDirection(), entry.getKey())) {
            if (!player.isCreative()) stack.shrink(1);
          }
          return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
      if (player.isShiftKeyDown()
          && stack.getItem() instanceof com.leclowndu93150.thaumaturge.content.wands.ItemWand) {
        if (!level.isClientSide) be.removeUpgrade(hit.getDirection(), player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
      }
      return super.useItemOn(stack, s, level, p, player, hand, hit);
    }

    public void onRemove(BlockState old, Level level, BlockPos p, BlockState next, boolean moved) {
      if (!level.isClientSide
          && !old.is(next.getBlock())
          && level.getBlockEntity(p) instanceof BenchEntity be) be.dropContents();
      super.onRemove(old, level, p, next, moved);
    }
  }

  public record Plan(
      CraftingInput input,
      RecipeHolder<CraftingRecipe> recipe,
      ItemStack result,
      int[] slots,
      int pattern) {}

  public static final class BenchEntity extends GolemModule.LinkEntity {
    public final ItemStackHandler ingredients = storage(18), output = storage(9);
    public final NonNullList<ItemStack> patterns = NonNullList.withSize(27, ItemStack.EMPTY);
    public final Map<Direction, Upgrade> upgrades = new EnumMap<>(Direction.class);
    private long lastWork = -1;
    public int crafts;

    BenchEntity(BlockPos p, BlockState s) {
      super(TYPE.get(), p, s);
    }

    private ItemStackHandler storage(int size) {
      return new ItemStackHandler(size) {
        protected void onContentsChanged(int slot) {
          changed();
        }
      };
    }

    public String kind() {
      return "golem_workbench";
    }

    public int controlCount() {
      return 0;
    }

    public boolean compatible(EntityThaumaturgeGolem g) {
      return level == g.level()
          && GolemModule.role(g, true)
          && g.hasRestriction()
          && LoadedTargets.withinRange(worldPosition, g.getRestrictCenter(), 5);
    }

    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
      return new WorkbenchMenu(id, inv, this);
    }

    public int count(Upgrade u) {
      return (int) upgrades.values().stream().filter(x -> x == u).count();
    }

    public int patternCount() {
      return 1 + count(Upgrade.FIRE);
    }

    public boolean install(Direction side, Upgrade u) {
      if (!side.getAxis().isHorizontal()
          || upgrades.containsKey(side)
          || u == Upgrade.FIRE && count(u) >= 2
          || u == Upgrade.ENTROPY && count(u) > 0) return false;
      upgrades.put(side, u);
      changed();
      return true;
    }

    public void removeUpgrade(Direction side, Player p) {
      Upgrade u = upgrades.remove(side);
      if (u != null) {
        ItemStack item = new ItemStack(UPGRADES.get(u).get());
        if (!p.addItem(item)) p.drop(item, false);
        changed();
      }
    }

    public void pattern(int page, int slot, ItemStack sample) {
      if (page >= 0 && page < patternCount() && slot >= 0 && slot < 9) {
        patterns.set(page * 9 + slot, sample.isEmpty() ? ItemStack.EMPTY : sample.copyWithCount(1));
        changed();
      }
    }

    public boolean paused() {
      return GolemModule.activeFetter(level, worldPosition.below());
    }

    public Plan plan(EntityThaumaturgeGolem g) {
      for (int p = 0; p < patternCount(); p++) {
        List<ItemStack> nominal = new ArrayList<>();
        for (int i = 0; i < 9; i++) nominal.add(patterns.get(p * 9 + i).copy());
        ItemStack intended = result(nominal);
        Plan exact = planGrid(g, nominal, p, intended);
        if (exact != null) return exact;
        if (count(Upgrade.ENTROPY) == 0 || ingredients.getStackInSlot(0).isEmpty()) continue;
        ItemStack placeholder = ingredients.getStackInSlot(0);
        for (int slot = 1; slot < ingredients.getSlots(); slot++) {
          ItemStack replacement = ingredients.getStackInSlot(slot);
          if (replacement.isEmpty()) continue;
          List<ItemStack> substituted =
              nominal.stream()
                  .map(
                      s ->
                          ItemStack.isSameItemSameComponents(s, placeholder)
                              ? replacement.copyWithCount(1)
                              : s.copy())
                  .toList();
          Plan alternative = planGrid(g, substituted, p, intended);
          if (alternative != null) return alternative;
        }
      }
      return null;
    }

    private ItemStack result(List<ItemStack> grid) {
      var input = CraftingInput.of(3, 3, grid);
      return level
          .getRecipeManager()
          .getRecipeFor(RecipeType.CRAFTING, input, level)
          .map(r -> r.value().assemble(input, level.registryAccess()))
          .orElse(ItemStack.EMPTY);
    }

    private Plan planGrid(
        EntityThaumaturgeGolem g, List<ItemStack> grid, int p, ItemStack intended) {
      if (!g.properties().hasTrait(TTGolemTraits.SMART.get()) && !basic(grid)) return null;
      CraftingInput input = CraftingInput.of(3, 3, grid);
      var recipe =
          level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
      if (recipe == null) return null;
      ItemStack product = recipe.value().assemble(input, level.registryAccess());
      if (product.isEmpty()) return null;
      boolean permitted =
          filter.samples().isEmpty()
              ? !intended.isEmpty() && ItemStack.isSameItemSameComponents(intended, product)
              : StackFilters.accepts(filter, product);
      if (!permitted
          || !ItemHandlerHelper.insertItemStacked(output, product.copy(), true).isEmpty())
        return null;
      int[] slots = new int[9], used = new int[18];
      Arrays.fill(slots, -1);
      for (int cell = 0; cell < 9; cell++) {
        if (grid.get(cell).isEmpty()) continue;
        for (int slot = 0; slot < 18; slot++)
          if (ingredients.getStackInSlot(slot).getCount() > used[slot]
              && ItemStack.isSameItemSameComponents(
                  grid.get(cell), ingredients.getStackInSlot(slot))) {
            slots[cell] = slot;
            used[slot]++;
            break;
          }
        if (slots[cell] < 0) return null;
      }
      return new Plan(input, recipe, product, slots, p);
    }

    private boolean basic(List<ItemStack> grid) {
      int minX = 3, minY = 3, maxX = -1, maxY = -1;
      ItemStack first = ItemStack.EMPTY;
      boolean same = true;
      for (int i = 0; i < 9; i++)
        if (!grid.get(i).isEmpty()) {
          minX = Math.min(minX, i % 3);
          maxX = Math.max(maxX, i % 3);
          minY = Math.min(minY, i / 3);
          maxY = Math.max(maxY, i / 3);
          if (first.isEmpty()) first = grid.get(i);
          else same &= ItemStack.isSameItemSameComponents(first, grid.get(i));
        }
      return maxX >= 0 && (same || maxX - minX < 2 && maxY - minY < 2);
    }

    public int interval(EntityThaumaturgeGolem g) {
      double speed = 1 + count(Upgrade.AIR) * Math.max(0, g.getGolemMoveSpeed()) * 2;
      double strength =
          count(Upgrade.EARTH)
              * Math.max(
                  g.properties().material().damage(), g.getAttributeValue(Attributes.ATTACK_DAMAGE))
              * .08;
      return Math.max(4, (int) Math.ceil(40 / (speed + strength)));
    }

    public int effort() {
      return Math.max(
          1, 4 + count(Upgrade.AIR) * 3 + count(Upgrade.ENTROPY) * 6 - count(Upgrade.EARTH) * 2);
    }

    public int fatigue(EntityThaumaturgeGolem g) {
      return g.getPersistentData().getInt("autoturgy_fatigue");
    }

    private void fatigue(EntityThaumaturgeGolem g, int value) {
      g.getPersistentData().putInt("autoturgy_fatigue", Math.clamp(value, 0, 120));
    }

    public void work(EntityThaumaturgeGolem g) {
      if (!compatible(g)
          || GolemModule.linked(g) != this
          || GolemModule.inhibited(g)
          || g.isNoAi()
          || g.isInCombat()
          || g.isFettered()
          || !GolemModule.withinWorkReach(g, worldPosition)) return;
      routeOutputs();
      if (level.getGameTime() == lastWork) return;
      boolean wet = false;
      for (Direction side : Direction.values())
        if (level.hasChunkAt(worldPosition.relative(side))
            && level.getFluidState(worldPosition.relative(side)).is(Fluids.WATER)) wet = true;
      if (level.getGameTime() % 20 == 0)
        fatigue(g, fatigue(g) - (1 + (wet ? count(Upgrade.WATER) * 3 : 0)));
      if (paused() || level.getGameTime() % interval(g) != 0) return;
      Plan plan = plan(g);
      if (plan == null) return;
      if (fatigue(g) >= 20 && payFabrico()) fatigue(g, fatigue(g) - 40);
      if (fatigue(g) >= 100) return;
      // The server thread owns these handlers; re-planning immediately precedes mutation.
      for (int slot : plan.slots()) if (slot >= 0) ingredients.extractItem(slot, 1, false);
      ItemStack left = ItemHandlerHelper.insertItemStacked(output, plan.result().copy(), false);
      drop(left);
      for (ItemStack remainder : plan.recipe().value().getRemainingItems(plan.input())) {
        if (remainder.isEmpty()) continue;
        ItemStack sample = remainder;
        boolean ingredient =
            plan.input().items().stream()
                .anyMatch(s -> ItemStack.isSameItemSameComponents(s, sample));
        if (ingredient || count(Upgrade.ORDER) > 0)
          remainder = ItemHandlerHelper.insertItemStacked(ingredients, remainder, false);
        drop(remainder);
      }
      fatigue(g, fatigue(g) + effort());
      lastWork = level.getGameTime();
      crafts++;
      g.swingArm();
      g.addRankXp(1);
      changed();
      routeOutputs();
    }

    private boolean payFabrico() {
      var aspect = Aspects.resolve(level, TTAspects.FABRICO);
      for (BlockPos p :
          BlockPos.betweenClosed(worldPosition.offset(-2, -1, -2), worldPosition.offset(2, 1, 2))) {
        if (!level.hasChunkAt(p) || p.equals(worldPosition)) continue;
        if (level.getBlockEntity(p)
                instanceof com.leclowndu93150.thaumaturge.api.aspect.IAspectSource source
            && source.isBlocked()) continue;
        var conduit = level.getCapability(EssentiaCapabilities.CONDUIT, p, Direction.UP);
        if (conduit != null && !conduit.releasesOn(Direction.UP)) continue;
        var storage = level.getCapability(EssentiaCapabilities.STORAGE, p, Direction.UP);
        if (storage != null
            && storage.extract(aspect, 1, true) == 1
            && storage.extract(aspect, 1, false) == 1) return true;
      }
      return false;
    }

    public void routeOutputs() {
      Direction side = getBlockState().getValue(DeviceBlock.FACING);
      BlockPos p = worldPosition.relative(side);
      if (!level.hasChunkAt(p)) return;
      IItemHandler handler =
          level.getCapability(Capabilities.ItemHandler.BLOCK, p, side.getOpposite());
      if (handler == null || handler == ingredients || handler == output) return;
      Transfers.ItemResult result =
          Transfers.moveItems(output, handler, StackFilters.Filter.all(), 64);
      for (ItemStack s : result.leftovers()) drop(s);
    }

    private void drop(ItemStack s) {
      if (!s.isEmpty())
        net.minecraft.world.Containers.dropItemStack(
            level,
            worldPosition.getX() + .5,
            worldPosition.getY() + 1,
            worldPosition.getZ() + .5,
            s);
    }

    void dropContents() {
      for (var h : List.of(ingredients, output))
        for (int i = 0; i < h.getSlots(); i++) drop(h.extractItem(i, Integer.MAX_VALUE, false));
      for (Upgrade u : upgrades.values()) drop(new ItemStack(UPGRADES.get(u).get()));
      upgrades.clear();
    }

    public void serverTick() {
      super.serverTick();
      if (level.getGameTime() % 4 == 0) routeOutputs();
    }

    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      tag.put("ingredients", ingredients.serializeNBT(lookup));
      tag.put("products", output.serializeNBT(lookup));
      ListTag ghosts = new ListTag();
      for (ItemStack s : patterns) ghosts.add(s.isEmpty() ? new CompoundTag() : s.save(lookup));
      tag.put("patterns", ghosts);
      for (var e : upgrades.entrySet())
        tag.putString("upgrade_" + e.getKey().getName(), e.getValue().name());
      tag.putInt("crafts", crafts);
    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      ingredients.deserializeNBT(lookup, tag.getCompound("ingredients"));
      output.deserializeNBT(lookup, tag.getCompound("products"));
      ListTag ghosts = tag.getList("patterns", Tag.TAG_COMPOUND);
      for (int i = 0; i < 27; i++)
        patterns.set(
            i,
            i < ghosts.size()
                ? ItemStack.parseOptional(lookup, ghosts.getCompound(i))
                : ItemStack.EMPTY);
      upgrades.clear();
      for (Direction side : Direction.Plane.HORIZONTAL)
        try {
          Upgrade u = Upgrade.valueOf(tag.getString("upgrade_" + side.getName()));
          install(side, u);
        } catch (IllegalArgumentException ignored) {
        }
      crafts = Math.max(0, tag.getInt("crafts"));
    }
  }

  private GolemWorkbench() {}
}
