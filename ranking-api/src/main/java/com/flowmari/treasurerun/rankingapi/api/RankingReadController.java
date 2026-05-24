package com.flowmari.treasurerun.rankingapi.api;

import com.flowmari.treasurerun.rankingapi.readmodel.RankingReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

  @Operation(
      summary = "List all-time leaderboard entries",
      description = "Returns ranked all-time leaderboard projections without exposing player UUID values."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Ordered leaderboard entries",
          content = @Content(
              array = @ArraySchema(schema = @Schema(implementation = LeaderboardEntryResponse.class))
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "The requested limit is outside the supported public boundary"
      )
  })
  @GetMapping("/all-time")
  public List<LeaderboardEntryResponse> getAllTimeLeaderboard(
      @Parameter(
          description = "Maximum number of leaderboard entries to return.",
          schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "10")
      )
      @RequestParam(defaultValue = "10") int limit
  ) {
    return service.getAllTimeLeaderboard(limit);
  }
}
