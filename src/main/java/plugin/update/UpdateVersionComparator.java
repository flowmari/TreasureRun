package plugin.update;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateVersionComparator implements Comparator<String> {

  private static final Pattern VERSION_PATTERN = Pattern.compile(
      "^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?$"
  );

  public boolean isSupported(String version) {
    return parse(version).isPresent();
  }

  public boolean isNewer(String candidate, String current) {
    Optional<Version> candidateVersion = parse(candidate);
    Optional<Version> currentVersion = parse(current);
    return candidateVersion.isPresent()
        && currentVersion.isPresent()
        && candidateVersion.orElseThrow().compareTo(currentVersion.orElseThrow()) > 0;
  }

  @Override
  public int compare(String left, String right) {
    Optional<Version> leftVersion = parse(left);
    Optional<Version> rightVersion = parse(right);

    if (leftVersion.isEmpty() && rightVersion.isEmpty()) {
      return normalize(left).compareTo(normalize(right));
    }
    if (leftVersion.isEmpty()) return -1;
    if (rightVersion.isEmpty()) return 1;
    return leftVersion.orElseThrow().compareTo(rightVersion.orElseThrow());
  }

  private Optional<Version> parse(String raw) {
    if (raw == null) return Optional.empty();
    Matcher matcher = VERSION_PATTERN.matcher(raw.trim());
    if (!matcher.matches()) return Optional.empty();

    try {
      return Optional.of(new Version(
          Integer.parseInt(matcher.group(1)),
          Integer.parseInt(matcher.group(2)),
          Integer.parseInt(matcher.group(3)),
          parsePrerelease(matcher.group(4))
      ));
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }

  private List<Identifier> parsePrerelease(String raw) {
    if (raw == null || raw.isBlank()) return List.of();

    List<Identifier> identifiers = new ArrayList<>();
    for (String token : raw.split("\\.")) {
      identifiers.add(Identifier.of(token));
    }
    return List.copyOf(identifiers);
  }

  private String normalize(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
  }

  private record Version(
      int major,
      int minor,
      int patch,
      List<Identifier> prerelease
  ) implements Comparable<Version> {

    @Override
    public int compareTo(Version other) {
      int value = Integer.compare(major, other.major);
      if (value != 0) return value;

      value = Integer.compare(minor, other.minor);
      if (value != 0) return value;

      value = Integer.compare(patch, other.patch);
      if (value != 0) return value;

      if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0;
      if (prerelease.isEmpty()) return 1;
      if (other.prerelease.isEmpty()) return -1;

      int length = Math.max(prerelease.size(), other.prerelease.size());
      for (int index = 0; index < length; index++) {
        if (index >= prerelease.size()) return -1;
        if (index >= other.prerelease.size()) return 1;

        value = prerelease.get(index).compareTo(other.prerelease.get(index));
        if (value != 0) return value;
      }
      return 0;
    }
  }

  private record Identifier(
      boolean numeric,
      long numericValue,
      String text
  ) implements Comparable<Identifier> {

    static Identifier of(String raw) {
      if (raw.matches("\\d+")) {
        try {
          return new Identifier(true, Long.parseLong(raw), "");
        } catch (NumberFormatException ignored) {
          // Oversized numeric identifiers remain deterministic as text.
        }
      }
      return new Identifier(false, 0L, raw.toLowerCase(Locale.ROOT));
    }

    @Override
    public int compareTo(Identifier other) {
      if (numeric && other.numeric) {
        return Long.compare(numericValue, other.numericValue);
      }
      if (numeric != other.numeric) {
        return numeric ? -1 : 1;
      }
      return text.compareTo(other.text);
    }
  }
}
