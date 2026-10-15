package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArenaSourceTemplateModelTest {
  private static final String SHA = "a".repeat(64);
  private static final TemplateLifecycle.References ZERO =
      new TemplateLifecycle.References(0, 0, 0, 0);

  private static ArenaTemplate template(String version) {
    return new ArenaTemplate("T1", version,
        new ArenaSource(ArenaSource.Kind.OPERATOR_PROVIDED, "operator-template-id"), SHA);
  }

  @Test
  void twoSourceKindsHaveDistinctImmutableValues() {
    ArenaSource generated = new ArenaSource(ArenaSource.Kind.GENERATED, "generator-revision");
    ArenaSource operator = new ArenaSource(ArenaSource.Kind.OPERATOR_PROVIDED, "operator-template-id");
    assertNotEquals(generated, operator);
    assertEquals(new ArenaSource(ArenaSource.Kind.GENERATED, "generator-revision"), generated);
  }

  @Test
  void invalidSourceOrTemplateIdentityIsRejected() {
    assertThrows(NullPointerException.class, () -> new ArenaSource(null, "id"));
    assertThrows(NullPointerException.class,
        () -> new ArenaSource(ArenaSource.Kind.GENERATED, null));
    assertThrows(IllegalArgumentException.class,
        () -> new ArenaSource(ArenaSource.Kind.OPERATOR_PROVIDED, " \t"));
    assertThrows(IllegalArgumentException.class,
        () -> new ArenaTemplate(" ", "v1", new ArenaSource(ArenaSource.Kind.GENERATED, "g"), SHA));
    assertThrows(IllegalArgumentException.class,
        () -> new ArenaTemplate("T", "", new ArenaSource(ArenaSource.Kind.GENERATED, "g"), SHA));
    assertThrows(NullPointerException.class, () -> new ArenaTemplate("T", "v1", null, SHA));
    assertThrows(IllegalArgumentException.class,
        () -> new ArenaTemplate("T", "v1", new ArenaSource(ArenaSource.Kind.GENERATED, "g"), "bad"));
  }

  @Test
  void reimportIsNewImmutableTemplateVersion() {
    ArenaTemplate first = template("v1");
    ArenaTemplate next = template("v2");
    assertNotEquals(first, next);
    assertEquals("v1", first.templateVersion());
    assertEquals("v2", next.templateVersion());
  }

  @Test
  void manifestDigestCanonicalizesCaseOnly() {
    ArenaTemplate upper = new ArenaTemplate("T1", "v1", template("v0").source(),
        SHA.toUpperCase());
    assertEquals(SHA, upper.expectedManifestSha256());
  }

  @Test
  void onlyActiveTemplateStateIsEligibleInIsolation() {
    for (TemplateLifecycle.State state : TemplateLifecycle.State.values()) {
      TemplateLifecycle.Snapshot s = new TemplateLifecycle.Snapshot(template("v1"), state, ZERO);
      assertEquals(state == TemplateLifecycle.State.ACTIVE,
          TemplateLifecycle.stateEligibleForProvision(s));
    }
  }

  @Test
  void trustedFingerprintMismatchMarksCorruptAndDisablesProvisionState() {
    TemplateLifecycle.Snapshot active = new TemplateLifecycle.Snapshot(
        template("v1"), TemplateLifecycle.State.ACTIVE, ZERO);
    TemplateLifecycle.Snapshot corrupt =
        TemplateLifecycle.markCorruptOnAuthoritativeFingerprintMismatch(active);
    assertEquals(TemplateLifecycle.State.CORRUPT, corrupt.state());
    assertFalse(TemplateLifecycle.stateEligibleForProvision(corrupt));
    assertTrue(TemplateLifecycle.stateEligibleForProvision(active));
    assertEquals(active.template(), corrupt.template());
  }

  @Test
  void retirementCannotSkipPendingEvenWithZeroReferences() {
    TemplateLifecycle.Snapshot active = new TemplateLifecycle.Snapshot(
        template("v1"), TemplateLifecycle.State.ACTIVE, ZERO);
    TemplateLifecycle.Snapshot pending = TemplateLifecycle.requestRetirement(active);
    assertEquals(TemplateLifecycle.State.RETIRED_PENDING, pending.state());
    assertThrows(IllegalStateException.class, () -> TemplateLifecycle.completeRetirement(active));
    assertEquals(TemplateLifecycle.State.RETIRED,
        TemplateLifecycle.completeRetirement(pending).state());
    assertEquals(TemplateLifecycle.State.ACTIVE, active.state());
  }

  @Test
  void everyOutstandingReferenceTypeBlocksRetirement() {
    TemplateLifecycle.References[] kinds = {
      new TemplateLifecycle.References(1, 0, 0, 0),
      new TemplateLifecycle.References(0, 1, 0, 0),
      new TemplateLifecycle.References(0, 0, 1, 0),
      new TemplateLifecycle.References(0, 0, 0, 1)
    };
    for (TemplateLifecycle.References refs : kinds) {
      TemplateLifecycle.Snapshot pending = new TemplateLifecycle.Snapshot(
          template("v1"), TemplateLifecycle.State.RETIRED_PENDING, refs);
      assertThrows(IllegalStateException.class,
          () -> TemplateLifecycle.completeRetirement(pending));
      assertThrows(IllegalArgumentException.class,
          () -> new TemplateLifecycle.Snapshot(template("v1"),
              TemplateLifecycle.State.RETIRED, refs));
    }
  }

  @Test
  void invalidReferenceCountsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> new TemplateLifecycle.References(-1, 0, 0, 0));
    assertThrows(IllegalArgumentException.class,
        () -> new TemplateLifecycle.References(0, -1, 0, 0));
    assertThrows(IllegalArgumentException.class,
        () -> new TemplateLifecycle.References(0, 0, -1, 0));
    assertThrows(IllegalArgumentException.class,
        () -> new TemplateLifecycle.References(0, 0, 0, -1));
  }

  @Test
  void corruptMayRequestRetirementButNotProvision() {
    TemplateLifecycle.Snapshot corrupt = new TemplateLifecycle.Snapshot(
        template("v1"), TemplateLifecycle.State.CORRUPT, ZERO);
    assertFalse(TemplateLifecycle.stateEligibleForProvision(corrupt));
    assertEquals(TemplateLifecycle.State.RETIRED_PENDING,
        TemplateLifecycle.requestRetirement(corrupt).state());
  }

  @Test
  void independentReimportVersionIsNotMutatedByOldVersionRetirement() {
    TemplateLifecycle.Snapshot old = new TemplateLifecycle.Snapshot(
        template("v1"), TemplateLifecycle.State.ACTIVE, ZERO);
    TemplateLifecycle.Snapshot next = new TemplateLifecycle.Snapshot(
        template("v2"), TemplateLifecycle.State.ACTIVE, ZERO);
    TemplateLifecycle.Snapshot retiring = TemplateLifecycle.requestRetirement(old);
    assertEquals("v1", retiring.template().templateVersion());
    assertEquals(TemplateLifecycle.State.ACTIVE, next.state());
  }

  @Test
  void unspecifiedFingerprintTransitionFromRetiredStateFailsClosed() {
    TemplateLifecycle.Snapshot retired = new TemplateLifecycle.Snapshot(
        template("v1"), TemplateLifecycle.State.RETIRED, ZERO);
    assertThrows(IllegalStateException.class,
        () -> TemplateLifecycle.markCorruptOnAuthoritativeFingerprintMismatch(retired));
  }

  @Test
  void fingerprintMismatchOnReferencedPendingRetirementMarksCorrupt() {
    TemplateLifecycle.References refs =
        new TemplateLifecycle.References(0, 1, 0, 0);
    TemplateLifecycle.Snapshot active =
        new TemplateLifecycle.Snapshot(
            template("v1"), TemplateLifecycle.State.ACTIVE, refs);
    TemplateLifecycle.Snapshot pending =
        TemplateLifecycle.requestRetirement(active);
    assertEquals(TemplateLifecycle.State.RETIRED_PENDING, pending.state());
    TemplateLifecycle.Snapshot corrupt =
        TemplateLifecycle.markCorruptOnAuthoritativeFingerprintMismatch(pending);
    assertEquals(TemplateLifecycle.State.CORRUPT, corrupt.state());
    assertEquals(pending.template(), corrupt.template());
    assertEquals(refs, corrupt.references());
    assertFalse(TemplateLifecycle.stateEligibleForProvision(corrupt));
    assertEquals(TemplateLifecycle.State.RETIRED_PENDING, pending.state());
    TemplateLifecycle.Snapshot pendingAgain =
        TemplateLifecycle.requestRetirement(corrupt);
    assertEquals(TemplateLifecycle.State.RETIRED_PENDING, pendingAgain.state());
    assertEquals(refs, pendingAgain.references());
    assertThrows(IllegalStateException.class,
        () -> TemplateLifecycle.completeRetirement(pendingAgain));
  }
}
