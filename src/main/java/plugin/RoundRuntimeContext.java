package plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable identity and participant snapshot for one production gameplay round.
 *
 * <p>This object owns no lifecycle transitions. {@link ServerHostedRoundCoordinator} remains the
 * only authoritative lifecycle state owner. Legacy /gamestart uses a one-player context while the
 * server-hosted path requires the coordinator's immutable 2-8 player snapshot.</p>
 */
public record RoundRuntimeContext(
    UUID roundId,
    Mode mode,
    List<UUID> participants
) {
  public enum Mode {
    LEGACY_SINGLE_PLAYER,
    SERVER_HOSTED
  }

  public RoundRuntimeContext {
    roundId = Objects.requireNonNull(roundId, "roundId");
    mode = Objects.requireNonNull(mode, "mode");
    participants = List.copyOf(Objects.requireNonNull(participants, "participants"));

    int minimum = mode == Mode.SERVER_HOSTED
        ? ServerHostedRoundCoordinator.MIN_PLAYERS
        : 1;
    if (participants.size() < minimum
        || participants.size() > ServerHostedRoundCoordinator.MAX_PLAYERS) {
      throw new IllegalArgumentException(
          "Participant count must be between " + minimum + " and "
              + ServerHostedRoundCoordinator.MAX_PLAYERS + " for " + mode + "."
      );
    }

    Set<UUID> unique = new HashSet<>();
    for (UUID participant : participants) {
      if (participant == null) {
        throw new IllegalArgumentException("participants must not contain null");
      }
      if (!unique.add(participant)) {
        throw new IllegalArgumentException("participants must be unique");
      }
    }
  }

  public static RoundRuntimeContext legacy(UUID playerId) {
    return new RoundRuntimeContext(
        UUID.randomUUID(),
        Mode.LEGACY_SINGLE_PLAYER,
        List.of(Objects.requireNonNull(playerId, "playerId"))
    );
  }

  public static RoundRuntimeContext serverHosted(List<UUID> participants) {
    return new RoundRuntimeContext(
        UUID.randomUUID(),
        Mode.SERVER_HOSTED,
        participants
    );
  }

  public boolean contains(UUID playerId) {
    return participants.contains(Objects.requireNonNull(playerId, "playerId"));
  }

  public UUID primaryParticipant() {
    return participants.get(0);
  }
}
