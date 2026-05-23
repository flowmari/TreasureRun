package plugin.rank.event;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable event representing one terminal gameplay outcome applied to ranking aggregates.
 *
 * <p>The event id is allocated at the gameplay boundary for one live game run.
 * When the same terminal callback is processed twice with the same event id,
 * the outbox uniqueness constraint prevents duplicate ranking aggregation.</p>
 *
 * <p>This type does not claim crash recovery or exactly-once processing across
 * server restarts. Durable run recovery is a separate future concern.</p>
 */
public record GameResultRecorded(
    UUID eventId,
    Instant occurredAt,
    long seasonId,
    UUID playerUuid,
    String playerName,
    String outcome,
    int scoreDelta,
    int winDelta,
    Long bestTimeMs,
    String langCode
) {

  private static final Set<String> ALLOWED_OUTCOMES =
      Set.of("SUCCESS", "TIME_UP", "QUIT");

  public static GameResultRecorded create(
      UUID eventId,
      Instant occurredAt,
      long seasonId,
      UUID playerUuid,
      String playerName,
      String outcome,
      int scoreDelta,
      int winDelta,
      Long bestTimeMs,
      String langCode
  ) {
    String normalizedOutcome = Objects.requireNonNull(outcome, "outcome")
        .trim()
        .toUpperCase(Locale.ROOT);

    if (!ALLOWED_OUTCOMES.contains(normalizedOutcome)) {
      throw new IllegalArgumentException("Unsupported outcome: " + outcome);
    }

    String normalizedLang =
        (langCode == null || langCode.isBlank()) ? "ja" : langCode;

    return new GameResultRecorded(
        Objects.requireNonNull(eventId, "eventId"),
        Objects.requireNonNull(occurredAt, "occurredAt"),
        seasonId,
        Objects.requireNonNull(playerUuid, "playerUuid"),
        Objects.requireNonNull(playerName, "playerName"),
        normalizedOutcome,
        scoreDelta,
        winDelta,
        bestTimeMs,
        normalizedLang
    );
  }

  public String aggregateType() {
    return "PLAYER_RANKING";
  }

  public String aggregateId() {
    return playerUuid.toString();
  }

  public String eventType() {
    return "GameResultRecorded";
  }

  /**
   * Produces a compact JSON payload without coupling the domain event to
   * Bukkit, JDBC, MyBatis, or a JSON framework.
   */
  public String payloadJson() {
    return "{"
        + "\"eventId\":" + jsonString(eventId.toString()) + ","
        + "\"eventType\":" + jsonString(eventType()) + ","
        + "\"seasonId\":" + seasonId + ","
        + "\"playerUuid\":" + jsonString(playerUuid.toString()) + ","
        + "\"playerName\":" + jsonString(playerName) + ","
        + "\"outcome\":" + jsonString(outcome) + ","
        + "\"scoreDelta\":" + scoreDelta + ","
        + "\"winDelta\":" + winDelta + ","
        + "\"bestTimeMs\":" + (bestTimeMs == null ? "null" : bestTimeMs) + ","
        + "\"langCode\":" + jsonString(langCode)
        + "}";
  }

  private static String jsonString(String value) {
    StringBuilder escaped = new StringBuilder("\"");

    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);

      switch (c) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (c < 0x20) {
            escaped.append(String.format("\\u%04x", (int) c));
          } else {
            escaped.append(c);
          }
        }
      }
    }

    return escaped.append('"').toString();
  }
}
