package plugin;

/**
 * Framework-independent lifecycle states for one server-hosted TreasureRun round.
 */
public enum ServerHostedRoundState {
  IDLE,
  WAITING,
  STARTING,
  COUNTDOWN,
  RUNNING,
  RESETTING
}
