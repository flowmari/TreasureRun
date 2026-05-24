package com.flowmari.treasurerun.rankingapi.readmodel;

import com.flowmari.treasurerun.rankingapi.api.LeaderboardEntryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RankingReadService {

  private final RankingReadRepository repository;

  public RankingReadService(RankingReadRepository repository) {
    this.repository = repository;
  }

  public List<LeaderboardEntryResponse> getAllTimeLeaderboard(int limit) {
    if (limit < 1 || limit > 100) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "limit must be between 1 and 100"
      );
    }

    return repository.findAllTimeLeaders(limit);
  }
}
