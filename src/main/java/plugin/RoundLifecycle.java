package plugin;

import java.util.Objects;

/**
 * Legacy /gamestart compatibility facade backed by the authoritative coordinator.
 *
 * <p>This class owns no mutable lifecycle state or cleanup claim.</p>
 */
public final class RoundLifecycle {
  private final ServerHostedRoundCoordinator coordinator;

  public RoundLifecycle() { this(new ServerHostedRoundCoordinator()); }

  RoundLifecycle(ServerHostedRoundCoordinator coordinator) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
  }

  public synchronized RoundState state() {
    return switch (coordinator.stateFor(
        ServerHostedRoundCoordinator.OwnershipMode.LEGACY_GAMESTART_COMPATIBILITY)) {
      case IDLE -> RoundState.IDLE;
      case STARTING -> RoundState.PREPARING;
      case COUNTDOWN -> RoundState.COUNTDOWN;
      case RUNNING -> RoundState.RUNNING;
      case RESETTING -> RoundState.RESETTING;
      case WAITING -> throw new IllegalStateException("Legacy compatibility cannot own WAITING.");
    };
  }

  public synchronized boolean isActive() { return state() != RoundState.IDLE; }
  public synchronized boolean isRunning() { return state() == RoundState.RUNNING; }
  public synchronized boolean isResetting() { return state() == RoundState.RESETTING; }

  public synchronized boolean tryBeginPreparation() { return coordinator.beginLegacyPreparation(); }

  public synchronized boolean beginCountdown() {
    return is(RoundState.PREPARING)
        && coordinator.beginCountdown() == ServerHostedRoundCoordinator.TransitionCode.TRANSITIONED;
  }

  public synchronized boolean beginRunning() {
    return is(RoundState.COUNTDOWN)
        && coordinator.beginRunning() == ServerHostedRoundCoordinator.TransitionCode.TRANSITIONED;
  }

  public synchronized boolean beginReset() {
    if (!isActive() || isResetting()) return false;
    return coordinator.requestReset(ServerHostedRoundCoordinator.ResetCause.LEGACY_RUNTIME_RESET).code()
        == ServerHostedRoundCoordinator.ResetCode.RESETTING;
  }

  public synchronized boolean claimCleanup() {
    return isResetting() && coordinator.claimCleanup().isPresent();
  }

  public synchronized void completeReset() { coordinator.completeReset(); }

  public synchronized boolean is(RoundState expected) {
    return state() == Objects.requireNonNull(expected, "expected");
  }
}
