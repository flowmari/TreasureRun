package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoundLifecycleTest {

  @Test
  void followsTheSingleRoundHappyPath() {
    RoundLifecycle lifecycle = new RoundLifecycle();

    assertEquals(RoundState.IDLE, lifecycle.state());
    assertTrue(lifecycle.tryBeginPreparation());
    assertEquals(RoundState.PREPARING, lifecycle.state());
    assertTrue(lifecycle.beginCountdown());
    assertEquals(RoundState.COUNTDOWN, lifecycle.state());
    assertTrue(lifecycle.beginRunning());
    assertTrue(lifecycle.isRunning());
    assertTrue(lifecycle.beginReset());
    assertEquals(RoundState.RESETTING, lifecycle.state());
    assertTrue(lifecycle.claimCleanup());
    lifecycle.completeReset();

    assertEquals(RoundState.IDLE, lifecycle.state());
    assertFalse(lifecycle.isActive());
  }

  @Test
  void rejectsSecondStartFromPreparationUntilResetCompletes() {
    RoundLifecycle lifecycle = new RoundLifecycle();

    assertTrue(lifecycle.tryBeginPreparation());
    assertFalse(lifecycle.tryBeginPreparation());
    assertTrue(lifecycle.beginCountdown());
    assertFalse(lifecycle.tryBeginPreparation());
    assertTrue(lifecycle.beginRunning());
    assertFalse(lifecycle.tryBeginPreparation());
    assertTrue(lifecycle.beginReset());
    assertFalse(lifecycle.tryBeginPreparation());

    assertTrue(lifecycle.claimCleanup());
    lifecycle.completeReset();
    assertTrue(lifecycle.tryBeginPreparation());
  }

  @Test
  void cleanupCanBeClaimedOnlyOnce() {
    RoundLifecycle lifecycle = new RoundLifecycle();

    assertTrue(lifecycle.tryBeginPreparation());
    assertTrue(lifecycle.beginReset());
    assertTrue(lifecycle.claimCleanup());
    assertFalse(lifecycle.claimCleanup());
    lifecycle.completeReset();
    assertThrows(IllegalStateException.class, lifecycle::completeReset);
  }

  @Test
  void invalidForwardTransitionsDoNotChangeState() {
    RoundLifecycle lifecycle = new RoundLifecycle();

    assertFalse(lifecycle.beginCountdown());
    assertFalse(lifecycle.beginRunning());
    assertFalse(lifecycle.beginReset());
    assertEquals(RoundState.IDLE, lifecycle.state());

    assertTrue(lifecycle.tryBeginPreparation());
    assertFalse(lifecycle.beginRunning());
    assertEquals(RoundState.PREPARING, lifecycle.state());
  }
}
