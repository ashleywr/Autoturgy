/* Pure timing transitions keep loaded-world ticking and persisted state consistent.
 * Modern defaults use 20 ticks per second and a two-tick completion pulse. */
package com.sanhiruzu.autoturgy.signal;

public final class TimerLogic {
  public enum Output {
    PULSE,
    CONSTANT,
    ALTERNATING,
    NEGATIVE_PULSE,
    DISABLED
  }

  public enum Trigger {
    RISING_EDGE,
    REQUIRE_POWERED,
    REQUIRE_UNPOWERED,
    IGNORE
  }

  public record Config(
      int duration,
      Output output,
      Trigger trigger,
      boolean repeat,
      boolean restart,
      boolean cancelFalling) {
    public Config {
      duration = Math.clamp(duration, 1, 1_728_000);
    }
  }

  public record State(int elapsed, boolean running, int pulse, boolean latched, boolean powered) {
    public static State idle() {
      return new State(0, false, 0, false, false);
    }
  }

  public record Step(State state, int signal, int progress, boolean completed) {}

  public static Step advance(Config config, State old, boolean powered, boolean manual) {
    boolean rising = powered && !old.powered();
    boolean falling = !powered && old.powered();
    int elapsed = Math.clamp(old.elapsed(), 0, config.duration());
    int pulse = Math.max(0, old.pulse() - 1);
    boolean running = old.running(), latched = old.latched(), completed = false;
    boolean trigger =
        manual
            || switch (config.trigger()) {
              case RISING_EDGE, REQUIRE_POWERED -> rising;
              case REQUIRE_UNPOWERED -> falling;
              case IGNORE -> false;
            };
    if (falling && config.cancelFalling()) {
      running = false;
      elapsed = 0;
    }
    if (trigger && (!running || config.restart())) {
      elapsed = 0;
      running = true;
    }
    boolean enabled =
        switch (config.trigger()) {
          case REQUIRE_POWERED -> powered;
          case REQUIRE_UNPOWERED -> !powered;
          default -> true;
        };
    if (running && enabled && ++elapsed >= config.duration()) {
      elapsed = config.duration();
      completed = true;
      pulse = 2;
      latched = !latched;
      running = false;
      if (config.repeat()) {
        elapsed = 0;
        running = true;
      }
    }
    int signal =
        switch (config.output()) {
          case PULSE -> pulse > 0 ? 15 : 0;
          case CONSTANT -> running ? 15 : 0;
          case ALTERNATING -> running && latched ? 15 : 0;
          case NEGATIVE_PULSE -> running && pulse == 0 ? 15 : 0;
          case DISABLED -> 0;
        };
    return new Step(
        new State(elapsed, running, pulse, latched, powered),
        signal,
        (int) ((long) elapsed * 15 / config.duration()),
        completed);
  }

  public static int invert(int input) {
    return 15 - Math.clamp(input, 0, 15);
  }

  private TimerLogic() {}
}
