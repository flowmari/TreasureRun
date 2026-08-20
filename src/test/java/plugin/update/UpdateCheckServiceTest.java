package plugin.update;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UpdateCheckServiceTest {

  @Test
  void disabledCheckerPerformsNoRemoteLookup() {
    AtomicInteger calls = new AtomicInteger();

    try (UpdateCheckService service = service(
        false,
        () -> {
          calls.incrementAndGet();
          return List.of("v9.0.0");
        },
        new MutableClock()
    )) {
      UpdateCheckResult result = service.checkAsync().join();

      assertEquals(UpdateCheckResult.Status.DISABLED, result.status());
      assertEquals(0, calls.get());
    }
  }

  @Test
  void cachePreventsRepeatedRemoteLookupWithinInterval() {
    AtomicInteger calls = new AtomicInteger();
    MutableClock clock = new MutableClock();

    try (UpdateCheckService service = service(
        true,
        () -> {
          calls.incrementAndGet();
          return List.of("v0.2.1-alpha", "v0.2.0-alpha");
        },
        clock
    )) {
      assertEquals(
          UpdateCheckResult.Status.UPDATE_AVAILABLE,
          service.checkAsync().join().status()
      );
      assertEquals(
          UpdateCheckResult.Status.UPDATE_AVAILABLE,
          service.checkAsync().join().status()
      );
      assertEquals(1, calls.get());

      clock.advance(Duration.ofHours(7));

      assertEquals(
          UpdateCheckResult.Status.UPDATE_AVAILABLE,
          service.checkAsync().join().status()
      );
      assertEquals(2, calls.get());
    }
  }

  @Test
  void remoteFailureIsNonFatal() {
    try (UpdateCheckService service = service(
        true,
        () -> {
          throw new IOException("offline");
        },
        new MutableClock()
    )) {
      assertEquals(
          UpdateCheckResult.Status.UNAVAILABLE,
          service.checkAsync().join().status()
      );
    }
  }

  @Test
  void choosesHighestSupportedReleaseInsteadOfResponseOrder() {
    try (UpdateCheckService service = service(
        true,
        () -> List.of(
            "v0.2.0-alpha",
            "nightly-main",
            "v0.3.0-alpha",
            "v0.2.2-alpha"
        ),
        new MutableClock()
    )) {
      UpdateCheckResult result = service.checkAsync().join();

      assertEquals(UpdateCheckResult.Status.UPDATE_AVAILABLE, result.status());
      assertEquals("v0.3.0-alpha", result.newestVersion());
    }
  }

  @Test
  void substitutesAllRequestedMessagePlaceholders() {
    UpdateCheckResult result =
        UpdateCheckResult.updateAvailable(
            "0.2.0-alpha",
            "v0.2.1-alpha"
        );

    assertEquals(
        "0.2.0-alpha -> v0.2.1-alpha https://example.invalid/releases",
        result.render(
            "%current_version% -> %new_version% %link%",
            "https://example.invalid/releases"
        )
    );
  }

  private UpdateCheckService service(
      boolean enabled,
      RemoteReleaseLookup lookup,
      Clock clock
  ) {
    return new UpdateCheckService(
        enabled,
        "0.2.0-alpha",
        Duration.ofHours(6),
        lookup,
        new UpdateVersionComparator(),
        Runnable::run,
        clock,
        () -> {}
    );
  }

  private static final class MutableClock extends Clock {

    private Instant instant = Instant.parse("2026-08-19T00:00:00Z");

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
