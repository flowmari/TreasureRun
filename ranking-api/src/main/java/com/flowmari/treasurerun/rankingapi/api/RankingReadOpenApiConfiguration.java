package com.flowmari.treasurerun.rankingapi.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RankingReadOpenApiConfiguration {

  @Bean
  public OpenAPI rankingReadOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("TreasureRun Ranking Read API")
            .version("v1")
            .description(
                "Read-only HTTP boundary for leaderboard projections persisted by TreasureRun."
            ));
  }
}
