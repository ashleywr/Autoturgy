package com.sanhiruzu.autoturgy.signal;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TimerLogicTest {
  private TimerLogic.Config config(
      TimerLogic.Output output,
      TimerLogic.Trigger trigger,
      boolean repeat,
      boolean restart,
      boolean cancel) {
    return new TimerLogic.Config(4, output, trigger, repeat, restart, cancel);
  }

  @Test
  void analogInversionClampsBoundaries() {
    assertEquals(15, TimerLogic.invert(-1));
    assertEquals(15, TimerLogic.invert(0));
    assertEquals(8, TimerLogic.invert(7));
    assertEquals(0, TimerLogic.invert(15));
    assertEquals(0, TimerLogic.invert(31));
  }

  @Test
  void risingEdgeRunsOnceAndCompletesWithTwoTickPulse() {
    var config =
        config(TimerLogic.Output.PULSE, TimerLogic.Trigger.RISING_EDGE, false, false, false);
    var step = TimerLogic.advance(config, TimerLogic.State.idle(), true, false);
    assertTrue(step.state().running());
    assertEquals(1, step.state().elapsed());
    for (int i = 0; i < 3; i++) step = TimerLogic.advance(config, step.state(), true, false);
    assertTrue(step.completed());
    assertEquals(15, step.signal());
    assertEquals(15, step.progress());
    step = TimerLogic.advance(config, step.state(), true, false);
    assertEquals(15, step.signal());
    assertFalse(step.state().running());
    step = TimerLogic.advance(config, step.state(), true, false);
    assertEquals(0, step.signal());
    assertFalse(step.state().running());
  }

  @Test
  void manualStartAndRestartRespectPolicy() {
    var noRestart =
        config(TimerLogic.Output.CONSTANT, TimerLogic.Trigger.RISING_EDGE, false, false, false);
    var started = TimerLogic.advance(noRestart, TimerLogic.State.idle(), false, true);
    var continued = TimerLogic.advance(noRestart, started.state(), false, true);
    assertEquals(2, continued.state().elapsed());
    var restarted =
        TimerLogic.advance(
            config(TimerLogic.Output.CONSTANT, TimerLogic.Trigger.RISING_EDGE, false, true, false),
            continued.state(),
            false,
            true);
    assertEquals(1, restarted.state().elapsed());
    assertEquals(15, restarted.signal());
  }

  @Test
  void requirePoweredPausesAndFallingCanCancel() {
    var config =
        config(TimerLogic.Output.CONSTANT, TimerLogic.Trigger.REQUIRE_POWERED, false, false, false);
    var step = TimerLogic.advance(config, TimerLogic.State.idle(), true, false);
    step = TimerLogic.advance(config, step.state(), false, false);
    assertEquals(1, step.state().elapsed());
    assertTrue(step.state().running());
    step =
        TimerLogic.advance(
            config(TimerLogic.Output.PULSE, TimerLogic.Trigger.REQUIRE_POWERED, false, false, true),
            new TimerLogic.State(2, true, 0, false, true),
            false,
            false);
    assertEquals(0, step.state().elapsed());
    assertFalse(step.state().running());
  }

  @Test
  void requireUnpoweredStartsOnFallingAndPausesWhenPowered() {
    var config =
        config(
            TimerLogic.Output.CONSTANT, TimerLogic.Trigger.REQUIRE_UNPOWERED, false, false, false);
    var step =
        TimerLogic.advance(config, new TimerLogic.State(0, false, 0, false, true), false, false);
    assertTrue(step.state().running());
    assertEquals(1, step.state().elapsed());
    step = TimerLogic.advance(config, step.state(), true, false);
    assertEquals(1, step.state().elapsed());
  }

  @Test
  void repeatKeepsThePeriodAndAlternatesLatch() {
    var config =
        config(TimerLogic.Output.ALTERNATING, TimerLogic.Trigger.IGNORE, true, false, false);
    var step = TimerLogic.advance(config, TimerLogic.State.idle(), false, true);
    for (int i = 0; i < 3; i++) step = TimerLogic.advance(config, step.state(), false, false);
    assertTrue(step.completed());
    assertTrue(step.state().running());
    assertEquals(15, step.signal());
    for (int i = 0; i < 4; i++) step = TimerLogic.advance(config, step.state(), false, false);
    assertTrue(step.completed());
    assertEquals(0, step.signal());
  }

  @Test
  void negativeAndDisabledModesProduceTheirSpecifiedSignal() {
    var old = new TimerLogic.State(3, true, 0, false, false);
    assertEquals(
        0,
        TimerLogic.advance(
                config(
                    TimerLogic.Output.NEGATIVE_PULSE,
                    TimerLogic.Trigger.RISING_EDGE,
                    false,
                    false,
                    false),
                old,
                false,
                false)
            .signal());
    assertEquals(
        0,
        TimerLogic.advance(
                config(
                    TimerLogic.Output.NEGATIVE_PULSE,
                    TimerLogic.Trigger.RISING_EDGE,
                    false,
                    false,
                    false),
                TimerLogic.State.idle(),
                false,
                false)
            .signal());
    assertEquals(
        15,
        TimerLogic.advance(
                config(
                    TimerLogic.Output.NEGATIVE_PULSE,
                    TimerLogic.Trigger.RISING_EDGE,
                    false,
                    false,
                    false),
                TimerLogic.State.idle(),
                false,
                true)
            .signal());
    assertEquals(
        0,
        TimerLogic.advance(
                config(
                    TimerLogic.Output.DISABLED,
                    TimerLogic.Trigger.RISING_EDGE,
                    false,
                    false,
                    false),
                old,
                false,
                false)
            .signal());
  }

  @Test
  void loadedStateContinuesWithoutASecondEdge() {
    var state = new TimerLogic.State(2, true, 0, true, true);
    var step =
        TimerLogic.advance(
            config(TimerLogic.Output.PULSE, TimerLogic.Trigger.RISING_EDGE, false, true, false),
            state,
            true,
            false);
    assertEquals(3, step.state().elapsed());
    assertTrue(step.state().latched());
  }

  @Test
  void stoppedModesAndIgnoreRemainStoppedAfterCancellation() {
    for (TimerLogic.Output output : TimerLogic.Output.values()) {
      var idle = new TimerLogic.State(0, false, 0, true, false);
      var step =
          TimerLogic.advance(
              config(output, TimerLogic.Trigger.IGNORE, true, true, false), idle, true, false);
      assertEquals(0, step.signal(), output.name());
      assertFalse(step.state().running(), output.name());
    }
  }

  @Test
  void negativePulseHasRepeatGapThenResumesRunning() {
    var config =
        config(TimerLogic.Output.NEGATIVE_PULSE, TimerLogic.Trigger.IGNORE, true, false, false);
    var step =
        TimerLogic.advance(config, new TimerLogic.State(3, true, 0, false, false), false, false);
    assertEquals(0, step.signal());
    assertTrue(step.state().running());
    step = TimerLogic.advance(config, step.state(), false, false);
    assertEquals(0, step.signal());
    step = TimerLogic.advance(config, step.state(), false, false);
    assertEquals(15, step.signal());
  }
}
