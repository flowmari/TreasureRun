package plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoundRuntimeContextTest {

  @Test
  void legacyContextPreservesThePublishedSinglePlayerCompatibilityPath() {
    UUID player = UUID.randomUUID();
    RoundRuntimeContext context = RoundRuntimeContext.legacy(player);

    assertEquals(RoundRuntimeContext.Mode.LEGACY_SINGLE_PLAYER, context.mode());
    assertEquals(List.of(player), context.participants());
    assertEquals(player, context.primaryParticipant());
    assertTrue(context.contains(player));
  }

  @Test
  void serverHostedContextAcceptsTheExactTwoToEightPlayerBoundary() {
    List<UUID> two = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<UUID> eight = java.util.stream.IntStream.range(0, 8)
        .mapToObj(ignored -> UUID.randomUUID())
        .toList();

    assertEquals(two, RoundRuntimeContext.serverHosted(two).participants());
    assertEquals(eight, RoundRuntimeContext.serverHosted(eight).participants());
  }

  @Test
  void serverHostedContextRejectsOneAndNinePlayers() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RoundRuntimeContext.serverHosted(List.of(UUID.randomUUID()))
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> RoundRuntimeContext.serverHosted(
            java.util.stream.IntStream.range(0, 9)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList()
        )
    );
  }

  @Test
  void contextRejectsDuplicateParticipants() {
    UUID player = UUID.randomUUID();
    assertThrows(
        IllegalArgumentException.class,
        () -> RoundRuntimeContext.serverHosted(List.of(player, player))
    );
  }

  @Test
  void participantSnapshotIsDefensivelyCopiedAndImmutable() {
    List<UUID> mutable = new ArrayList<>();
    mutable.add(UUID.randomUUID());
    mutable.add(UUID.randomUUID());

    RoundRuntimeContext context = RoundRuntimeContext.serverHosted(mutable);
    List<UUID> frozen = context.participants();
    mutable.clear();

    assertEquals(2, frozen.size());
    assertThrows(UnsupportedOperationException.class, () -> frozen.add(UUID.randomUUID()));
  }
}
