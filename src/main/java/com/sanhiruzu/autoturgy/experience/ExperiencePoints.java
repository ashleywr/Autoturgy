/* Exact point arithmetic for player XP, independent of unreliable lifetime counters.
 * Integer boundaries and bounded reconstruction prevent fractional duplication. */
package com.sanhiruzu.autoturgy.experience;

public final class ExperiencePoints {
  public static final int MAX_LEVEL = 1_400_000_000;
  public static final long MAX_POINTS =
      (MAX_LEVEL / 2L) * (9L * MAX_LEVEL - 325) + 2220 + 9L * MAX_LEVEL - 159;

  public static long atLevel(int level) {
    long n = Math.max(0, level);
    if (n <= 16) return n * n + 6 * n;
    if (n <= 31) return (5 * n * n - 81 * n + 720) / 2;
    // Saturating arithmetic also accepts externally supplied extreme levels.
    if (n > MAX_LEVEL) return MAX_POINTS;
    return (n / 2) * (9 * n - 325) + (n % 2) * (9 * n - 325) / 2 + 2220;
  }

  public static long next(int level) {
    long n = Math.max(0, level);
    return n < 16 ? 2 * n + 7 : n < 31 ? 5 * n - 38 : 9 * n - 158;
  }

  public record Balance(int level, long points, long width) {}

  public static Balance split(long total) {
    total = Math.clamp(total, 0, MAX_POINTS);
    int low = 0, high = MAX_LEVEL;
    while (low < high) {
      int mid = low + (high - low + 1) / 2;
      if (atLevel(mid) <= total) low = mid;
      else high = mid - 1;
    }
    return new Balance(low, total - atLevel(low), next(low));
  }

  private ExperiencePoints() {}
}
