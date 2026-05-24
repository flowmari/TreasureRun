package com.flowmari.treasurerun.rankingapi.api;

public record LeaderboardEntryResponse(
    String playerName,
    int score,
    int wins,
    Long bestTimeMs,
    String languageCode
) {
}
