package plugin.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateVersionComparatorTest {

  private final UpdateVersionComparator comparator =
      new UpdateVersionComparator();

  @Test
  void comparesTreasureRunAlphaVersionsNumerically() {
    assertTrue(comparator.isNewer("v0.2.1-alpha", "0.2.0-alpha"));
    assertTrue(comparator.isNewer("v0.10.0-alpha", "v0.9.9-alpha"));
  }

  @Test
  void stableReleaseSortsAfterItsPrerelease() {
    assertTrue(comparator.isNewer("v0.2.0", "v0.2.0-alpha"));
    assertFalse(comparator.isNewer("v0.2.0-alpha", "v0.2.0"));
  }

  @Test
  void unsupportedTagsDoNotProduceFalseUpdates() {
    assertFalse(comparator.isNewer("nightly-main", "v0.2.0-alpha"));
    assertFalse(comparator.isSupported("nightly-main"));
  }
}
