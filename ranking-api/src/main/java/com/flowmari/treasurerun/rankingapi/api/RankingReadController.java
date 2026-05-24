package com.flowmari.treasurerun.rankingapi.api;

import com.flowmari.treasurerun.rankingapi.readmodel.RankingReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rankings")
public class RankingReadController {

  private final RankingReadService service;

  public RankingReadController(RankingReadService service) {
    this.service = service;
  }

  @GetMapping("/all-time")
  public List<LeaderboardEntryResponse> getAllTimeLeaderboard(
      @RequestParam(defaultValue = "10") int limit
  ) {
    return service.getAllTimeLeaderboard(limit);
  }
}
