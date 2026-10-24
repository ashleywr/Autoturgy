package com.sanhiruzu.autoturgy.experience;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class ExperiencePointsTest {
  @Test
  void exactKnownCurveBoundaries() {
    int[] levels = {0, 1, 16, 17, 31, 32};
    long[] points = {0, 7, 352, 394, 1507, 1628};
    for (int i = 0; i < levels.length; i++)
      assertEquals(points[i], ExperiencePoints.atLevel(levels[i]));
  }

  @Test
  void everyBoundarySplitsWithoutOffByOne() {
    for (int n = 1; n < 10000; n++) {
      long start = ExperiencePoints.atLevel(n);
      assertEquals(n, ExperiencePoints.split(start).level());
      assertEquals(n - 1, ExperiencePoints.split(start - 1).level());
      assertEquals(n, ExperiencePoints.split(start + 1).level());
    }
  }

  @Test
  void largePointsRemainPositiveAndReconstructExactly() {
    for (long n : new long[] {10000000000L, 1000000000000000L, 4500000000000000000L}) {
      var b = ExperiencePoints.split(n);
      assertEquals(n, ExperiencePoints.atLevel(b.level()) + b.points());
      assertTrue(b.points() >= 0);
      assertTrue(b.points() < b.width());
    }
  }

  @Test
  void maximumInputsHaveCanonicalBoundedRepresentations() {
    assertTrue(ExperiencePoints.next(Integer.MAX_VALUE) > 0);
    assertTrue(ExperiencePoints.atLevel(Integer.MAX_VALUE) > 0);
    var b = ExperiencePoints.split(Long.MAX_VALUE);
    assertEquals(ExperiencePoints.MAX_LEVEL, b.level());
    assertEquals(ExperiencePoints.MAX_POINTS, ExperiencePoints.atLevel(b.level()) + b.points());
    assertTrue(b.points() < b.width());
  }

  @Test
  void negativeInputsClamp() {
    assertEquals(0, ExperiencePoints.atLevel(-5));
    assertEquals(0, ExperiencePoints.split(-100).level());
    assertEquals(0, ExperiencePoints.split(-100).points());
  }
}
