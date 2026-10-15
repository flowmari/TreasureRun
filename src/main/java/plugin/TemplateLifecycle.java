package plugin;

import java.util.Objects;

/**
 * Immutable, PURE state-transition rules (TPL_004/005/007/008/011).
 * Not a durable catalog, reference authority, or root-deletion implementation.
 * Retirement is a LOGICAL status; physical template deletion remains disallowed
 * without separately verified Section 19 provider root-entry capability.
 */
final class TemplateLifecycle {
  private TemplateLifecycle() { }

  enum State { ACTIVE, CORRUPT, RETIRED_PENDING, RETIRED }

  record References(int definitions, int instances, int createIntents,
                    int unresolvedAuthorities) {
    References {
      if (definitions < 0 || instances < 0 || createIntents < 0 || unresolvedAuthorities < 0) {
        throw new IllegalArgumentException("Reference counts must be nonnegative");
      }
    }
    boolean none() {
      return definitions == 0 && instances == 0 && createIntents == 0
          && unresolvedAuthorities == 0;
    }
  }

  record Snapshot(ArenaTemplate template, State state, References references) {
    Snapshot {
      Objects.requireNonNull(template, "template");
      Objects.requireNonNull(state, "state");
      Objects.requireNonNull(references, "references");
      if (state == State.RETIRED && !references.none()) {
        throw new IllegalArgumentException("RETIRED may have no unresolved references");
      }
    }
  }

  /** State-only eligibility; actual provisioning requires ALL external proofs. */
  static boolean stateEligibleForProvision(Snapshot s) {
    Objects.requireNonNull(s, "snapshot");
    return s.state() == State.ACTIVE;
  }

  static Snapshot markCorruptOnAuthoritativeFingerprintMismatch(Snapshot s) {
    Objects.requireNonNull(s, "snapshot");
    if (s.state() != State.ACTIVE && s.state() != State.CORRUPT
        && s.state() != State.RETIRED_PENDING) {
      throw new IllegalStateException("Unspecified fingerprint transition: fail closed");
    }
    return new Snapshot(s.template(), State.CORRUPT, s.references());
  }

  static Snapshot requestRetirement(Snapshot s) {
    Objects.requireNonNull(s, "snapshot");
    return switch (s.state()) {
      case ACTIVE, CORRUPT -> new Snapshot(s.template(), State.RETIRED_PENDING, s.references());
      case RETIRED_PENDING, RETIRED -> s;
    };
  }

  /** Caller must independently prove the supplied reference snapshot is current and fenced. */
  static Snapshot completeRetirement(Snapshot s) {
    Objects.requireNonNull(s, "snapshot");
    if (s.state() != State.RETIRED_PENDING || !s.references().none()) {
      throw new IllegalStateException("Retirement needs PENDING and zero current references");
    }
    return new Snapshot(s.template(), State.RETIRED, s.references());
  }
}
