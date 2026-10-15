package plugin;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable template-version descriptor. expectedManifestSha256 is DATA, not
 * evidence that an import or promotion has passed validation. This type alone
 * NEVER authorizes template use, filesystem mutation, world load, or cleanup.
 */
record ArenaTemplate(
    String templateId,
    String templateVersion,
    ArenaSource source,
    String expectedManifestSha256) {

  ArenaTemplate {
    templateId = ArenaSource.nonBlank(templateId, "templateId");
    templateVersion = ArenaSource.nonBlank(templateVersion, "templateVersion");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(expectedManifestSha256, "expectedManifestSha256");
    if (!expectedManifestSha256.matches("(?i)[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Expected manifest SHA-256 must have 64 hex characters");
    }
    expectedManifestSha256 = expectedManifestSha256.toLowerCase(Locale.ROOT);
  }
}
