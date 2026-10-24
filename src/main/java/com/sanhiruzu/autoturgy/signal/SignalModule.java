/* Analog inversion and two deliberately separate clock variants.
 * Clock transitions use the pure timer and preserve elapsed/edge/latch state. */
package com.sanhiruzu.autoturgy.signal;

import com.sanhiruzu.autoturgy.Content;
import com.sanhiruzu.autoturgy.common.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.registries.*;

public final class SignalModule {
  public static DeferredBlock<ClockBlock> HOURGLASS, MAGIC_HOURGLASS;
  public static DeferredBlock<InverterBlock> INVERTER;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<ClockEntity>> CLOCK_TYPE;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<InverterEntity>> INVERTER_TYPE;

  public static void register() {
    HOURGLASS = Content.block("hourglass", () -> new ClockBlock(false));
    MAGIC_HOURGLASS = Content.block("magic_hourglass", () -> new ClockBlock(true));
    INVERTER = Content.block("analog_inverter", InverterBlock::new);
    CLOCK_TYPE =
        Content.ENTITIES.register(
            "clock",
            () ->
                BlockEntityType.Builder.of(ClockEntity::new, HOURGLASS.get(), MAGIC_HOURGLASS.get())
                    .build(null));
    INVERTER_TYPE =
        Content.ENTITIES.register(
            "inverter",
            () -> BlockEntityType.Builder.of(InverterEntity::new, INVERTER.get()).build(null));
  }

  public static final class ClockBlock extends DeviceBlock {
    private final boolean magic;

    ClockBlock(boolean magic) {
      this.magic = magic;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new ClockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide
          && !player.isShiftKeyDown()
          && level.getBlockEntity(pos) instanceof ClockEntity clock) clock.activate();
      return super.useWithoutItem(state, level, pos, player, hit);
    }
  }

  public static final class InverterBlock extends DeviceBlock {
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new InverterEntity(pos, state);
    }
  }

  public static final class InverterEntity extends DeviceEntity implements DeviceBlock.Ticking {
    public InverterEntity(BlockPos p, BlockState s) {
      super(INVERTER_TYPE.get(), p, s);
    }

    public String kind() {
      return "analog_inverter";
    }

    public int controlCount() {
      return 0;
    }

    public void configure(int i, int d) {}

    public void serverTick() {
      output(TimerLogic.invert(DeviceBlock.input(level, worldPosition, getBlockState())));
      progress = signal();
    }
  }

  public static final class ClockEntity extends DeviceEntity implements DeviceBlock.Ticking {
    private TimerLogic.State timer = TimerLogic.State.idle();
    private boolean manual;

    public ClockEntity(BlockPos p, BlockState s) {
      super(CLOCK_TYPE.get(), p, s);
      settings[0] = 5;
    }

    private boolean magic() {
      return getBlockState().is(MAGIC_HOURGLASS.get());
    }

    public String kind() {
      return magic() ? "magic_hourglass" : "hourglass";
    }

    public int controlCount() {
      return magic() ? 6 : 1;
    }

    public void configure(int i, int delta) {
      if (i == 0) bounded(0, delta * (magic() ? 1 : 5), magic() ? 1 : 5, magic() ? 3600 : 60);
      else if (magic() && i == 1) cycle(i, delta, 5);
      else if (magic() && i == 2) cycle(i, delta, 4);
      else if (magic() && i < 6) cycle(i, delta, 2);
    }

    @Override
    public void activate() {
      manual = true;
    }

    @Override
    public void cancel() {
      manual = false;
      timer = new TimerLogic.State(0, false, 0, timer.latched(), timer.powered());
      output(0);
      progress = 0;
      changed();
    }

    public void serverTick() {
      int duration = Math.clamp(settings[0], 1, magic() ? 3600 : 60) * 20;
      TimerLogic.Config config =
          new TimerLogic.Config(
              duration,
              TimerLogic.Output.values()[Math.floorMod(settings[1], 5)],
              TimerLogic.Trigger.values()[Math.floorMod(settings[2], 4)],
              magic() && settings[3] != 0,
              magic() && settings[4] != 0,
              magic() && settings[5] != 0);
      TimerLogic.Step step =
          TimerLogic.advance(
              config, timer, DeviceBlock.input(level, worldPosition, getBlockState()) > 0, manual);
      manual = false;
      timer = step.state();
      output(step.signal());
      if (progress != step.progress()) {
        progress = step.progress();
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
      }
      if (timer.running() || step.completed()) setChanged();
    }

    public TimerLogic.State timerState() {
      return timer;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.saveAdditional(tag, lookup);
      tag.putInt("elapsed", timer.elapsed());
      tag.putBoolean("running", timer.running());
      tag.putInt("pulse", timer.pulse());
      tag.putBoolean("latched", timer.latched());
      tag.putBoolean("powered", timer.powered());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
      super.loadAdditional(tag, lookup);
      settings[0] = Math.clamp(settings[0], magic() ? 1 : 5, magic() ? 3600 : 60);
      timer =
          new TimerLogic.State(
              Math.clamp(tag.getInt("elapsed"), 0, 1_728_000),
              tag.getBoolean("running"),
              Math.clamp(tag.getInt("pulse"), 0, 2),
              tag.getBoolean("latched"),
              tag.getBoolean("powered"));
    }
  }

  private SignalModule() {}
}
