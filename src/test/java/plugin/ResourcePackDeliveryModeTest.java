package plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourcePackDeliveryModeTest {

  @Test
  void standardDeliveryTakesPrecedenceWhenBothPathsAreEnabled() {
    assertEquals(
        ResourcePackDeliveryMode.STANDARD,
        ResourcePackDeliveryMode.select(true, true)
    );
  }

  @Test
  void selectsOnlyTheConfiguredDeliveryPath() {
    assertEquals(
        ResourcePackDeliveryMode.STANDARD,
        ResourcePackDeliveryMode.select(true, false)
    );
    assertEquals(
        ResourcePackDeliveryMode.FALLBACK,
        ResourcePackDeliveryMode.select(false, true)
    );
    assertEquals(
        ResourcePackDeliveryMode.DISABLED,
        ResourcePackDeliveryMode.select(false, false)
    );
  }
}
