package plugin.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubReleaseLookup implements RemoteReleaseLookup {

  public static final URI DEFAULT_RELEASES_ENDPOINT =
      URI.create("https://api.github.com/repos/flowmari/TreasureRun/releases?per_page=30");

  private static final Pattern TAG_NAME_PATTERN = Pattern.compile(
      "\"tag_name\"\\s*:\\s*\"([^\"]+)\""
  );

  private final HttpClient httpClient;
  private final URI endpoint;
  private final Duration requestTimeout;

  public GitHubReleaseLookup(Duration connectTimeout, Duration requestTimeout) {
    this(
        HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
        DEFAULT_RELEASES_ENDPOINT,
        requestTimeout
    );
  }

  GitHubReleaseLookup(
      HttpClient httpClient,
      URI endpoint,
      Duration requestTimeout
  ) {
    this.httpClient = httpClient;
    this.endpoint = endpoint;
    this.requestTimeout = requestTimeout;
  }

  @Override
  public List<String> fetchReleaseTags() throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(endpoint)
        .timeout(requestTimeout)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "TreasureRun-update-notifier")
        .GET()
        .build();

    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new IOException(
          "Release lookup returned HTTP " + response.statusCode() + "."
      );
    }

    List<String> tags = new ArrayList<>();
    Matcher matcher = TAG_NAME_PATTERN.matcher(response.body());
    while (matcher.find()) {
      String tag = matcher.group(1).trim();
      if (!tag.isEmpty()) {
        tags.add(tag);
      }
    }

    if (tags.isEmpty()) {
      throw new IOException("Release lookup returned no release tags.");
    }
    return List.copyOf(tags);
  }
}
