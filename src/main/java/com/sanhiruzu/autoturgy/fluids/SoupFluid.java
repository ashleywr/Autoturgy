/* Registered soup fluids, edible servings and bounded vishroom exposure.
 * Source growth and warp use real host content and affect only loaded neighbors. */
package com.sanhiruzu.autoturgy.fluids;

import com.leclowndu93150.thaumaturge.api.warp.*;
import com.sanhiruzu.autoturgy.Content;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.registries.*;

public final class SoupFluid {
  public final String name;
  public final boolean warped;
  public final DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
  public final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
  public final DeferredHolder<FluidType, FluidType> type;
  public final DeferredBlock<LiquidBlock> block;
  public final DeferredItem<Item> bucket, bowl;

  public SoupFluid(String name, boolean warped) {
    this.name = name;
    this.warped = warped;
    type =
        FluidModule.TYPES.register(
            name,
            () ->
                new FluidType(
                    FluidType.Properties.create()
                        .density(1100)
                        .viscosity(2000)
                        .canConvertToSource(false)) {});
    source = FluidModule.FLUIDS.register(name, () -> new BaseFlowingFluid.Source(properties()));
    flowing =
        FluidModule.FLUIDS.register(
            "flowing_" + name, () -> new BaseFlowingFluid.Flowing(properties()));
    block = Content.BLOCKS.register(name, () -> new SoupBlock(this));
    bucket =
        Content.item(
            name + "_bucket",
            () ->
                new BucketItem(
                    source.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    bowl =
        warped
            ? Content.item(
                "vishroom_stew",
                () ->
                    new Item(
                        new Item.Properties()
                            .stacksTo(1)
                            .craftRemainder(Items.BOWL)
                            .food(
                                new FoodProperties.Builder()
                                    .nutrition(2)
                                    .saturationModifier(.1F)
                                    .alwaysEdible()
                                    .build())) {
                      @Override
                      public ItemStack finishUsingItem(
                          ItemStack stack, Level l, LivingEntity entity) {
                        ItemStack eaten = super.finishUsingItem(stack, l, entity);
                        if (!l.isClientSide) {
                          entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400));
                          entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300));
                          if (entity instanceof ServerPlayer p)
                            WarpHelper.addWarp(p, 1, WarpType.TEMPORARY);
                        }
                        return entity instanceof Player p && p.isCreative()
                            ? eaten
                            : new ItemStack(Items.BOWL);
                      }
                    })
            : null;
  }

  private BaseFlowingFluid.Properties properties() {
    return new BaseFlowingFluid.Properties(type, source, flowing)
        .bucket(bucket)
        .block(block)
        .tickRate(10);
  }

  public static final class SoupBlock extends LiquidBlock {
    final SoupFluid soup;

    SoupBlock(SoupFluid soup) {
      super(
          soup.source.get(),
          Properties.of()
              .noCollission()
              .strength(100)
              .noLootTable()
              .replaceable()
              .liquid()
              .randomTicks());
      this.soup = soup;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
      return soup.warped && state.getFluidState().isSource();
    }

    @Override
    protected void entityInside(BlockState s, Level l, BlockPos p, Entity entity) {
      super.entityInside(s, l, p, entity);
      if (soup.warped
          && !l.isClientSide
          && l.getFluidState(p).isSource()
          && entity instanceof LivingEntity living) {
        long next = entity.getPersistentData().getLong("autoturgySoupExposureUntil");
        if (next <= l.getGameTime()) {
          entity.getPersistentData().putLong("autoturgySoupExposureUntil", l.getGameTime() + 200);
          living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 240));
          if (entity instanceof ServerPlayer player)
            WarpHelper.addWarp(player, 1, WarpType.TEMPORARY);
        }
      }
    }

    @Override
    protected void randomTick(BlockState s, ServerLevel l, BlockPos p, RandomSource random) {
      if (!soup.warped || !l.getFluidState(p).isSource()) return;
      BlockPos target = p.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
      if (!l.hasChunkAt(target) || !l.isEmptyBlock(target)) return;
      Block fungus =
          BuiltInRegistries.BLOCK.get(
              ResourceLocation.fromNamespaceAndPath("thaumaturge", "vishroom"));
      if (fungus != Blocks.AIR && fungus.defaultBlockState().canSurvive(l, target))
        l.setBlockAndUpdate(target, fungus.defaultBlockState());
    }
  }
}
