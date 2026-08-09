package plugin;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Framework-independent shared gameplay/timer/result data for one RUNNING server-hosted round.
 *
 * <p>This runtime deliberately owns no lifecycle transitions, Bukkit tasks, arena state, player
 * locations, persistence, messages, or release metadata. {@link ServerHostedRoundCoordinator}
 * remains the only authoritative lifecycle owner. The runtime consumes the coordinator's locked
 * immutable participant snapshot and provides one shared monotonic gameplay clock plus
 * participant-scoped score/result data.</p>
 */
public final class ServerHostedSharedRoundRuntime {

  public enum ScoreCode {
    RECORDED,
    ROUND_NOT_RUNNING,
    NOT_PARTICIPANT,
    SCORE_OVERFLOW
  }

  public record ParticipantResult(UUID participantId, int score) {
    public ParticipantResult {
      participantId = Objects.requireNonNull(participantId, "participantId");
    }
  }

  public record Snapshot(
      UUID roundId,
      List<UUID> participants,
      long elapsedMillis,
      long remainingMillis,
      boolean timeExpired,
      List<ParticipantResult> results
  ) {
    public Snapshot {
      roundId = Objects.requireNonNull(roundId, "roundId");
      participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
      results = List.copyOf(Objects.requireNonNull(results, "results"));
    }
  }

  private final ServerHostedRoundCoordinator coordinator;
  private final RoundRuntimeContext context;
  private final LongSupplier nanoTime;
  private final long durationNanos;
  private final long startedAtNanos;
  private final Map<UUID, Integer> scores = new LinkedHashMap<>();

  public static ServerHostedSharedRoundRuntime begin(
      ServerHostedRoundCoordinator coordinator,
      RoundRuntimeContext context,
      Duration duration
  ) {
    return begin(coordinator, context, duration, System::nanoTime);
  }

  static ServerHostedSharedRoundRuntime begin(
      ServerHostedRoundCoordinator coordinator,
      RoundRuntimeContext context,
      Duration duration,
      LongSupplier nanoTime
  ) {
    return new ServerHostedSharedRoundRuntime(coordinator, context, duration, nanoTime);
  }

  private ServerHostedSharedRoundRuntime(
      ServerHostedRoundCoordinator coordinator,
      RoundRuntimeContext context,
      Duration duration,
      LongSupplier nanoTime
  ) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    this.context = Objects.requireNonNull(context, "context");
    this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    Objects.requireNonNull(duration, "duration");

    if (context.mode() != RoundRuntimeContext.Mode.SERVER_HOSTED) {
      throw new IllegalArgumentException("Shared runtime requires a SERVER_HOSTED context.");
    }
    if (coordinator.ownershipMode()
        != ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED
        || coordinator.stateFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)
            != ServerHostedRoundState.RUNNING) {
      throw new IllegalStateException("The authoritative server-hosted round is not RUNNING.");
    }
    if (!coordinator.participantsFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)
        .equals(context.participants())) {
      throw new IllegalArgumentException(
          "Runtime participants must exactly match the coordinator's locked snapshot."
      );
    }

    long nanos;
    try {
      nanos = duration.toNanos();
    } catch (ArithmeticException overflow) {
      throw new IllegalArgumentException("duration is too large", overflow);
    }
    if (nanos <= 0L) {
      throw new IllegalArgumentException("duration must be positive");
    }
    durationNanos = nanos;
    startedAtNanos = nanoTime.getAsLong();

    for (UUID participant : context.participants()) {
      scores.put(participant, 0);
    }
  }

  public RoundRuntimeContext context() {
    return context;
  }

  public List<UUID> participants() {
    return context.participants();
  }

  public synchronized int score(UUID participantId) {
    Objects.requireNonNull(participantId, "participantId");
    Integer score = scores.get(participantId);
    if (score == null) {
      throw new IllegalArgumentException("Not a round participant: " + participantId);
    }
    return score;
  }

  /** Adds a signed score delta only while the authoritative coordinator still owns RUNNING. */
  public synchronized ScoreCode addScore(UUID participantId, int delta) {
    Objects.requireNonNull(participantId, "participantId");
    if (coordinator.ownershipMode()
        != ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED
        || coordinator.stateFor(ServerHostedRoundCoordinator.OwnershipMode.SERVER_HOSTED)
            != ServerHostedRoundState.RUNNING) {
      return ScoreCode.ROUND_NOT_RUNNING;
    }

    Integer current = scores.get(participantId);
    if (current == null) return ScoreCode.NOT_PARTICIPANT;

    final int updated;
    try {
      updated = Math.addExact(current, delta);
    } catch (ArithmeticException overflow) {
      return ScoreCode.SCORE_OVERFLOW;
    }

    scores.put(participantId, updated);
    return ScoreCode.RECORDED;
  }

  public long elapsedMillis() {
    return Duration.ofNanos(elapsedNanos()).toMillis();
  }

  public long remainingMillis() {
    long remaining = durationNanos - elapsedNanos();
    return remaining <= 0L ? 0L : Duration.ofNanos(remaining).toMillis();
  }

  public boolean timeExpired() {
    return elapsedNanos() >= durationNanos;
  }

  /**
   * Captures a stable result view without changing coordinator state. This remains available while
   * RESETTING so cleanup/result adapters can consume the last shared-round values.
   */
  public synchronized Snapshot snapshot() {
    long elapsed = elapsedNanos();
    long remaining = Math.max(0L, durationNanos - elapsed);
    List<ParticipantResult> results = context.participants().stream()
        .map(participant -> new ParticipantResult(participant, scores.get(participant)))
        .toList();

    return new Snapshot(
        context.roundId(),
        context.participants(),
        Duration.ofNanos(elapsed).toMillis(),
        Duration.ofNanos(remaining).toMillis(),
        elapsed >= durationNanos,
        results
    );
  }

  private long elapsedNanos() {
    long now = nanoTime.getAsLong();
    if (now <= startedAtNanos) return 0L;
    long elapsed = now - startedAtNanos;
    return elapsed < 0L ? Long.MAX_VALUE : elapsed;
  }
}
