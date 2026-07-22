package plugin;

import java.util.Objects;

public final class RoundLifecycle {
  private RoundState state = RoundState.IDLE;
  private boolean cleanupClaimed;

  public synchronized RoundState state() {
    return state;
  }

  public synchronized boolean isActive() {
    return state != RoundState.IDLE;
  }

  public synchronized boolean isRunning() {
    return state == RoundState.RUNNING;
  }

  public synchronized boolean isResetting() {
    return state == RoundState.RESETTING;
  }

  public synchronized boolean tryBeginPreparation() {
    if (state != RoundState.IDLE) return false;
    state = RoundState.PREPARING;
    cleanupClaimed = false;
    return true;
  }

  public synchronized boolean beginCountdown() {
    if (state != RoundState.PREPARING) return false;
    state = RoundState.COUNTDOWN;
    return true;
  }

  public synchronized boolean beginRunning() {
    if (state != RoundState.COUNTDOWN) return false;
    state = RoundState.RUNNING;
    return true;
  }

  public synchronized boolean beginReset() {
    if (state == RoundState.IDLE || state == RoundState.RESETTING) return false;
    state = RoundState.RESETTING;
    cleanupClaimed = false;
    return true;
  }

  public synchronized boolean claimCleanup() {
    if (state != RoundState.RESETTING || cleanupClaimed) return false;
    cleanupClaimed = true;
    return true;
  }

  public synchronized void completeReset() {
    if (state != RoundState.RESETTING || !cleanupClaimed) {
      throw new IllegalStateException("Cleanup must be claimed before reset completes.");
    }
    state = RoundState.IDLE;
    cleanupClaimed = false;
  }

  public synchronized boolean is(RoundState expected) {
    return state == Objects.requireNonNull(expected, "expected");
  }
}
