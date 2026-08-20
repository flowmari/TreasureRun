package plugin.update;

import java.util.Objects;

public record UpdateCheckResult(
    Status status,
    String currentVersion,
    String newestVersion,
    String detail
) {

  public enum Status {
    UPDATE_AVAILABLE,
    CURRENT,
    UNAVAILABLE,
    DISABLED
  }

  public UpdateCheckResult {
    Objects.requireNonNull(status, "status");
    currentVersion = currentVersion == null ? "" : currentVersion;
    newestVersion = newestVersion == null ? "" : newestVersion;
    detail = detail == null ? "" : detail;
  }

  public static UpdateCheckResult disabled(String currentVersion) {
    return new UpdateCheckResult(
        Status.DISABLED,
        currentVersion,
        "",
        "Update checks are disabled."
    );
  }

  public static UpdateCheckResult unavailable(String currentVersion, String detail) {
    return new UpdateCheckResult(Status.UNAVAILABLE, currentVersion, "", detail);
  }

  public static UpdateCheckResult current(String currentVersion, String newestVersion) {
    return new UpdateCheckResult(Status.CURRENT, currentVersion, newestVersion, "");
  }

  public static UpdateCheckResult updateAvailable(String currentVersion, String newestVersion) {
    return new UpdateCheckResult(
        Status.UPDATE_AVAILABLE,
        currentVersion,
        newestVersion,
        ""
    );
  }

  public boolean hasUpdate() {
    return status == Status.UPDATE_AVAILABLE;
  }

  public String render(String template, String link) {
    String value = template == null ? "" : template;
    return value
        .replace("%current_version%", currentVersion)
        .replace("%new_version%", newestVersion)
        .replace("%link%", link == null ? "" : link);
  }
}
