package com.sanhiruzu.autoturgy.host;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class AspectReadingTest {
  @Test
  void nodeExtremaUseFullnessRatherThanAbsoluteAmounts() {
    var reading =
        new ThaumaturgeIntegration.Snapshot(
            List.of(
                new ThaumaturgeIntegration.AspectReading("a", 80, 100),
                new ThaumaturgeIntegration.AspectReading("b", 9, 10),
                new ThaumaturgeIntegration.AspectReading("c", 0, 20)),
            true,
            true,
            true);
    assertEquals(14, ThaumaturgeIntegration.signal(reading, 1, 0, false, 0));
    assertEquals(0, ThaumaturgeIntegration.signal(reading, 2, 0, false, 0));
    assertEquals(11, ThaumaturgeIntegration.signal(reading, 0, 0, false, 0));
  }

  @Test
  void invalidTargetCannotBecomePoweredThroughInversion() {
    assertEquals(
        0, ThaumaturgeIntegration.signal(ThaumaturgeIntegration.Snapshot.invalid(), 0, 0, true, 0));
  }

  @Test
  void manualCapacityAspectSelectionAndCountAreBounded() {
    var reading =
        new ThaumaturgeIntegration.Snapshot(
            List.of(
                new ThaumaturgeIntegration.AspectReading("a", 32, 64),
                new ThaumaturgeIntegration.AspectReading("b", 64, 64)),
            false,
            true,
            true);
    assertEquals(8, ThaumaturgeIntegration.signal(reading, 0, 1, false, 0));
    assertEquals(15, ThaumaturgeIntegration.signal(reading, 0, 0, false, 64));
    assertEquals(2, ThaumaturgeIntegration.signal(reading, 3, 1, false, 4));
  }

  @Test
  void countIgnoresZeroAmountsSelectionAndCapacity() {
    var reading =
        new ThaumaturgeIntegration.Snapshot(
            List.of(
                new ThaumaturgeIntegration.AspectReading("empty", 0, 100),
                new ThaumaturgeIntegration.AspectReading("full", 100, 100)),
            true,
            true,
            true);
    assertEquals(2, ThaumaturgeIntegration.signal(reading, 3, 1, false, 1));
    assertEquals(13, ThaumaturgeIntegration.signal(reading, 3, "missing", true, 1));
  }

  @Test
  void selectedAspectStaysBoundToIdWhenEntriesChange() {
    var reading =
        new ThaumaturgeIntegration.Snapshot(
            List.of(new ThaumaturgeIntegration.AspectReading("b", 64, 64)), false, true, true);
    assertEquals(15, ThaumaturgeIntegration.signal(reading, 0, "b", false, 0));
    assertEquals(0, ThaumaturgeIntegration.signal(reading, 0, "a", false, 0));
  }
}
