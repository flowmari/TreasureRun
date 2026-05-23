package plugin.rank.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameResultRecordedTest {

  @Test
  void preservesEventIdNormalizesLanguageAndEscapesPayload() {
    UUID eventId = UUID.fromString("10000000-0000-0000-0000-000000000001");

    GameResultRecorded event = GameResultRecorded.create(
        eventId,
        Instant.parse("2026-05-23T00:00:00Z"),
        7L,
        UUID.fromString("00000000-0000-0000-0000-000000000007"),
        "flow\"mari\\test",
        "success",
        80,
        1,
        null,
        " "
    );

    assertEquals(eventId, event.eventId());
    assertEquals("SUCCESS", event.outcome());
    assertEquals("ja", event.langCode());
    assertEquals("PLAYER_RANKING", event.aggregateType());
    assertEquals("GameResultRecorded", event.eventType());
    assertTrue(event.payloadJson().contains("\"eventId\":\"" + eventId + "\""));
    assertTrue(event.payloadJson().contains("\"playerName\":\"flow\\\"mari\\\\test\""));
    assertTrue(event.payloadJson().contains("\"outcome\":\"SUCCESS\""));
    assertTrue(event.payloadJson().contains("\"bestTimeMs\":null"));
  }

  @Test
  void rejectsUnsupportedOutcome() {
    assertThrows(IllegalArgumentException.class, () -> GameResultRecorded.create(
        UUID.fromString("10000000-0000-0000-0000-000000000002"),
        Instant.parse("2026-05-23T00:00:00Z"),
        7L,
        UUID.fromString("00000000-0000-0000-0000-000000000007"),
        "flowmari",
        "UNKNOWN",
        80,
        0,
        null,
        "en"
    ));
  }
}
