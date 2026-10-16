package plugin;

import java.util.Objects;

/**
 * An immutable, logical source identity. A source ID is not a filesystem path,
 * a verified ownership marker, or permission to mutate the operator's world.
 * Actual path/manifest validation belongs to a later PLAN_02 import slice.
 */
record ArenaSource(Kind kind, String sourceId) {
  enum Kind { GENERATED, OPERATOR_PROVIDED }

  ArenaSource {
    Objects.requireNonNull(kind, "kind");
    sourceId = nonBlank(sourceId, "sourceId");
  }

  static String nonBlank(String value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
