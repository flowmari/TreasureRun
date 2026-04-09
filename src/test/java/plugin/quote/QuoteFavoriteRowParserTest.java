package plugin.quote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuoteFavoriteRowParserTest {

    @Test
    void parse_fullRow_parsesAllFields() {
        String row = "id: 12\n"
                + "outcome: SUCCESS\n"
                + "quote: Treasure favors the bold.\n"
                + "time: 2026-01-23 12:34";

        QuoteFavoriteRow result = QuoteFavoriteRowParser.parse(row);

        assertEquals(12, result.id);
        assertEquals("SUCCESS", result.outcome);
        assertEquals("Treasure favors the bold.", result.quote);
        assertEquals("2026-01-23 12:34", result.timestamp);
    }

    @Test
    void parse_missingOutcome_defaultsToOther() {
        String row = "id: 7\n"
                + "quote: Stay calm and keep searching.";

        QuoteFavoriteRow result = QuoteFavoriteRowParser.parse(row);

        assertEquals(7, result.id);
        assertEquals("OTHER", result.outcome);
        assertEquals("Stay calm and keep searching.", result.quote);
    }

    @Test
    void parse_nullRow_returnsDefaultRow() {
        QuoteFavoriteRow result = QuoteFavoriteRowParser.parse(null);

        assertEquals(0, result.id);
        assertEquals(null, result.quote);
        assertEquals(null, result.timestamp);
        assertEquals(null, result.outcome);
    }

    @Test
    void parse_unstructuredText_fallsBackToWholeRowAsQuote() {
        String row = "just free text without key value format";

        QuoteFavoriteRow result = QuoteFavoriteRowParser.parse(row);

        assertEquals(0, result.id);
        assertEquals("OTHER", result.outcome);
        assertEquals("just free text without key value format", result.quote);
    }

    @Test
    void parse_invalidId_returnsZero() {
        String row = "id: abc\n"
                + "outcome: TIME_UP\n"
                + "quote: Not every run succeeds.";

        QuoteFavoriteRow result = QuoteFavoriteRowParser.parse(row);

        assertEquals(0, result.id);
        assertEquals("TIME_UP", result.outcome);
        assertEquals("Not every run succeeds.", result.quote);
    }
}
