/* Common placement, redstone faces and eye/filter attachment for devices.
 * Input is behind the marked front; output is confined to that front face. */
package com.sanhiruzu.autoturgy.common;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.autoturgy.sensing.SensingModule;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

public abstract class DeviceBlock extends BaseEntityBlock {
  public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

  protected DeviceBlock() {
    super(Properties.of().strength(1.5F).requiresCorrectToolForDrops());
    registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return MapCodec.unit(this);
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  protected boolean isSignalSource(BlockState state) {
    return true;
  }

  @Override
  protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
    return side == state.getValue(FACING).getOpposite()
            && level.getBlockEntity(pos) instanceof DeviceEntity be
        ? be.signal()
        : 0;
  }

  @Override
  protected boolean hasAnalogOutputSignal(BlockState state) {
    return true;
  }

  @Override
  protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
    return level.getBlockEntity(pos) instanceof DeviceEntity be ? be.comparator() : 0;
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!level.isClientSide
        && level.getBlockEntity(pos) instanceof DeviceEntity be
        && player instanceof ServerPlayer server) {
      if (player.isShiftKeyDown()) be.activate();
      else server.openMenu(be, buf -> ControlMenu.writeOpening(buf, be));
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
    if (!(level.getBlockEntity(pos) instanceof DeviceEntity be))
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (SensingModule.attach(stack, be, player))
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  public static int input(Level level, BlockPos pos, BlockState state) {
    Direction back = state.getValue(FACING).getOpposite();
    return level.hasChunkAt(pos.relative(back)) ? level.getSignal(pos.relative(back), back) : 0;
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide
        ? null
        : (world, pos, s, be) -> {
          if (be instanceof Ticking ticking) ticking.serverTick();
        };
  }

  public interface Ticking {
    void serverTick();
  }
}
