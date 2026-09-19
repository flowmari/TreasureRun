package plugin.rank;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Lifecycle-scoped authority for coalesced ranking-window requests. */
public final class RankingRequestCoordinator {

    private final Set<UUID> waiters = new LinkedHashSet<>();
    private long generation;
    private long nextFlightId;
    private boolean active;
    private FlightToken currentFlight;

    public synchronized void activate() {
        generation++;
        active = true;
        currentFlight = null;
        waiters.clear();
    }

    public synchronized void deactivate() {
        generation++;
        active = false;
        currentFlight = null;
        waiters.clear();
    }

    public synchronized RequestDecision request(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!active) {
            return new RequestDecision(false, false, null);
        }
        if (!waiters.add(playerId)) {
            return new RequestDecision(true, false, null);
        }
        if (currentFlight != null) {
            return new RequestDecision(true, true, null);
        }
        currentFlight = new FlightToken(generation, ++nextFlightId);
        return new RequestDecision(true, true, currentFlight);
    }

    public synchronized boolean isAuthoritative(FlightToken flight) {
        Objects.requireNonNull(flight, "flight");
        return active && flight.equals(currentFlight);
    }

    public synchronized Resolution complete(FlightToken flight) {
        return resolve(flight);
    }

    public synchronized Resolution abort(FlightToken flight) {
        return resolve(flight);
    }

    private Resolution resolve(FlightToken flight) {
        Objects.requireNonNull(flight, "flight");
        if (!active || !flight.equals(currentFlight)) {
            return Resolution.stale();
        }
        List<UUID> recipients = List.copyOf(waiters);
        waiters.clear();
        currentFlight = null;
        return new Resolution(true, recipients);
    }

    public record FlightToken(long generation, long flightId) {}

    public record RequestDecision(
            boolean lifecycleActive, boolean waiterAdded, FlightToken flightToStart) {}

    public record Resolution(boolean authoritative, List<UUID> waiters) {
        public Resolution {
            waiters = List.copyOf(waiters);
        }
        private static Resolution stale() {
            return new Resolution(false, List.of());
        }
    }
}
