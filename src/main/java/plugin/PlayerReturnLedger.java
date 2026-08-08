package plugin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

/**
 * Durable write-before-teleport ledger for pending player returns.
 *
 * <p>The file is intentionally a strict internal line format rather than user configuration.
 * Presence of a record is the pending state; there is no separately persisted completed flag.</p>
 */
public final class PlayerReturnLedger {
  static final String HEADER = "TREASURERUN_PLAYER_RETURN_LEDGER\t1";

  public enum OpenCode {
    AVAILABLE_EMPTY,
    AVAILABLE_LOADED,
    UNAVAILABLE_CORRUPT,
    UNAVAILABLE_IO
  }

  public enum PutCode {
    SAVED,
    ALREADY_PENDING,
    CONFLICT,
    STORAGE_UNAVAILABLE
  }

  public enum PutBatchCode {
    SAVED,
    ALREADY_PENDING,
    CONFLICT,
    DUPLICATE_PLAYER,
    DUPLICATE_RECOVERY_ID,
    EMPTY_BATCH,
    STORAGE_UNAVAILABLE
  }

  public enum CompleteCode {
    CLEARED,
    NO_PENDING,
    RECOVERY_ID_MISMATCH,
    STORAGE_UNAVAILABLE
  }

  public record OpenResult(OpenCode code, Optional<Path> quarantinePath, String detail) {
    public OpenResult {
      code = Objects.requireNonNull(code, "code");
      quarantinePath = Objects.requireNonNull(quarantinePath, "quarantinePath");
      detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean available() {
      return code == OpenCode.AVAILABLE_EMPTY || code == OpenCode.AVAILABLE_LOADED;
    }
  }

  public record PutResult(PutCode code, Optional<PlayerReturnRecord> record, String detail) {
    public PutResult {
      code = Objects.requireNonNull(code, "code");
      record = Objects.requireNonNull(record, "record");
      detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean accepted() {
      return code == PutCode.SAVED || code == PutCode.ALREADY_PENDING;
    }
  }

  public record PutBatchResult(
      PutBatchCode code,
      List<PlayerReturnRecord> records,
      String detail
  ) {
    public PutBatchResult {
      code = Objects.requireNonNull(code, "code");
      records = List.copyOf(Objects.requireNonNull(records, "records"));
      detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean accepted() {
      return code == PutBatchCode.SAVED || code == PutBatchCode.ALREADY_PENDING;
    }
  }

  public record CompleteResult(CompleteCode code, String detail) {
    public CompleteResult {
      code = Objects.requireNonNull(code, "code");
      detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean cleared() {
      return code == CompleteCode.CLEARED || code == CompleteCode.NO_PENDING;
    }
  }

  @FunctionalInterface
  interface SnapshotWriter {
    void write(Path target, byte[] bytes) throws IOException;
  }

  private final Path ledgerPath;
  private final Clock clock;
  private final SnapshotWriter snapshotWriter;
  private final Map<UUID, PlayerReturnRecord> pending = new LinkedHashMap<>();
  private boolean available;
  private String unavailableDetail = "Ledger has not been opened yet.";

  public PlayerReturnLedger(Path ledgerPath) {
    this(ledgerPath, Clock.systemUTC(), PlayerReturnLedger::writeAtomically);
  }

  PlayerReturnLedger(Path ledgerPath, Clock clock, SnapshotWriter snapshotWriter) {
    this.ledgerPath = Objects.requireNonNull(ledgerPath, "ledgerPath").toAbsolutePath().normalize();
    this.clock = Objects.requireNonNull(clock, "clock");
    this.snapshotWriter = Objects.requireNonNull(snapshotWriter, "snapshotWriter");
  }

  public synchronized OpenResult open() {
    pending.clear();
    available = false;

    if (!Files.exists(ledgerPath)) {
      available = true;
      unavailableDetail = "";
      return new OpenResult(OpenCode.AVAILABLE_EMPTY, Optional.empty(), "No pending return ledger exists yet.");
    }

    try {
      byte[] bytes = Files.readAllBytes(ledgerPath);
      Map<UUID, PlayerReturnRecord> loaded = parse(bytes);
      pending.putAll(loaded);
      available = true;
      unavailableDetail = "";
      return new OpenResult(
          OpenCode.AVAILABLE_LOADED,
          Optional.empty(),
          "Loaded " + loaded.size() + " pending player-return record(s)."
      );
    } catch (LedgerFormatException formatException) {
      Optional<Path> quarantine = quarantineCorruptFile();
      unavailableDetail = "Corrupt player-return ledger: " + formatException.getMessage();
      return new OpenResult(OpenCode.UNAVAILABLE_CORRUPT, quarantine, unavailableDetail);
    } catch (IOException ioException) {
      unavailableDetail = "Unable to read player-return ledger: " + ioException.getMessage();
      return new OpenResult(OpenCode.UNAVAILABLE_IO, Optional.empty(), unavailableDetail);
    }
  }

  public synchronized boolean isAvailable() {
    return available;
  }

  public synchronized String unavailableDetail() {
    return unavailableDetail;
  }

  public synchronized Optional<PlayerReturnRecord> pendingRecord(UUID playerId) {
    return Optional.ofNullable(pending.get(Objects.requireNonNull(playerId, "playerId")));
  }

  public synchronized List<PlayerReturnRecord> pendingRecords() {
    return pending.values().stream()
        .sorted(Comparator.comparing(record -> record.playerId().toString()))
        .toList();
  }

  public synchronized PutResult putPending(PlayerReturnRecord candidate) {
    Objects.requireNonNull(candidate, "candidate");
    if (!available) {
      return new PutResult(PutCode.STORAGE_UNAVAILABLE, Optional.empty(), unavailableDetail);
    }

    PlayerReturnRecord existing = pending.get(candidate.playerId());
    if (existing != null) {
      if (existing.hasSameDestination(candidate)) {
        return new PutResult(
            PutCode.ALREADY_PENDING,
            Optional.of(existing),
            "The same return destination is already pending."
        );
      }
      return new PutResult(
          PutCode.CONFLICT,
          Optional.of(existing),
          "A different return destination is already pending for this player."
      );
    }

    Map<UUID, PlayerReturnRecord> next = new LinkedHashMap<>(pending);
    next.put(candidate.playerId(), candidate);
    try {
      persist(next);
      pending.clear();
      pending.putAll(next);
      return new PutResult(PutCode.SAVED, Optional.of(candidate), "Pending return persisted.");
    } catch (IOException ioException) {
      markUnavailable("Unable to persist pending return: " + ioException.getMessage());
      return new PutResult(PutCode.STORAGE_UNAVAILABLE, Optional.empty(), unavailableDetail);
    }
  }

  /**
   * Atomically adds every participant return obligation in one durable snapshot.
   *
   * <p>No in-memory candidate becomes visible unless the complete snapshot write succeeds.
   * Existing identical destinations are idempotent; any conflict rejects the whole batch.</p>
   */
  public synchronized PutBatchResult putPendingBatch(List<PlayerReturnRecord> candidates) {
    Objects.requireNonNull(candidates, "candidates");
    List<PlayerReturnRecord> copy = List.copyOf(candidates);
    if (copy.isEmpty()) {
      return new PutBatchResult(PutBatchCode.EMPTY_BATCH, List.of(), "No pending returns supplied.");
    }
    if (!available) {
      return new PutBatchResult(
          PutBatchCode.STORAGE_UNAVAILABLE,
          List.of(),
          unavailableDetail
      );
    }

    Set<UUID> candidatePlayers = new HashSet<>();
    Set<UUID> candidateRecoveryIds = new HashSet<>();
    for (PlayerReturnRecord candidate : copy) {
      Objects.requireNonNull(candidate, "candidate");
      if (!candidatePlayers.add(candidate.playerId())) {
        return new PutBatchResult(
            PutBatchCode.DUPLICATE_PLAYER,
            List.of(),
            "The batch contains the same player more than once."
        );
      }
      if (!candidateRecoveryIds.add(candidate.recoveryId())) {
        return new PutBatchResult(
            PutBatchCode.DUPLICATE_RECOVERY_ID,
            List.of(),
            "The batch contains the same recovery id more than once."
        );
      }
    }

    Set<UUID> existingRecoveryIds = new HashSet<>();
    for (PlayerReturnRecord record : pending.values()) {
      existingRecoveryIds.add(record.recoveryId());
    }

    Map<UUID, PlayerReturnRecord> next = new LinkedHashMap<>(pending);
    List<PlayerReturnRecord> accepted = new java.util.ArrayList<>();
    boolean added = false;

    for (PlayerReturnRecord candidate : copy) {
      PlayerReturnRecord existing = pending.get(candidate.playerId());
      if (existing != null) {
        if (!existing.hasSameDestination(candidate)) {
          return new PutBatchResult(
              PutBatchCode.CONFLICT,
              List.of(),
              "A different return destination is already pending for "
                  + candidate.playerId() + "."
          );
        }
        accepted.add(existing);
        continue;
      }

      if (existingRecoveryIds.contains(candidate.recoveryId())) {
        return new PutBatchResult(
            PutBatchCode.DUPLICATE_RECOVERY_ID,
            List.of(),
            "A recovery id in the batch is already pending."
        );
      }

      next.put(candidate.playerId(), candidate);
      accepted.add(candidate);
      added = true;
    }

    if (!added) {
      return new PutBatchResult(
          PutBatchCode.ALREADY_PENDING,
          accepted,
          "Every return destination is already pending."
      );
    }

    try {
      persist(next);
      pending.clear();
      pending.putAll(next);
      return new PutBatchResult(
          PutBatchCode.SAVED,
          accepted,
          "All participant returns were persisted in one durable snapshot."
      );
    } catch (IOException ioException) {
      markUnavailable(
          "Unable to persist participant return batch: " + ioException.getMessage()
      );
      return new PutBatchResult(
          PutBatchCode.STORAGE_UNAVAILABLE,
          List.of(),
          unavailableDetail
      );
    }
  }

  public synchronized CompleteResult complete(UUID playerId, UUID recoveryId) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(recoveryId, "recoveryId");

    PlayerReturnRecord existing = pending.get(playerId);
    if (existing == null) {
      return new CompleteResult(CompleteCode.NO_PENDING, "No pending return exists for this player.");
    }
    if (!existing.recoveryId().equals(recoveryId)) {
      return new CompleteResult(
          CompleteCode.RECOVERY_ID_MISMATCH,
          "The completion token does not match the current pending return."
      );
    }
    if (!available) {
      return new CompleteResult(CompleteCode.STORAGE_UNAVAILABLE, unavailableDetail);
    }

    Map<UUID, PlayerReturnRecord> next = new LinkedHashMap<>(pending);
    next.remove(playerId);
    try {
      persist(next);
      pending.clear();
      pending.putAll(next);
      return new CompleteResult(CompleteCode.CLEARED, "Pending return was durably cleared.");
    } catch (IOException ioException) {
      markUnavailable("Player returned, but durable ledger removal failed: " + ioException.getMessage());
      return new CompleteResult(CompleteCode.STORAGE_UNAVAILABLE, unavailableDetail);
    }
  }

  private void markUnavailable(String detail) {
    available = false;
    unavailableDetail = Objects.requireNonNull(detail, "detail");
  }

  private void persist(Map<UUID, PlayerReturnRecord> snapshot) throws IOException {
    snapshotWriter.write(ledgerPath, serialize(snapshot));
  }

  private Optional<Path> quarantineCorruptFile() {
    if (!Files.exists(ledgerPath)) return Optional.empty();
    Path parent = ledgerPath.getParent();
    if (parent == null) return Optional.empty();

    long stamp = clock.instant().toEpochMilli();
    for (int suffix = 0; suffix < 1000; suffix++) {
      String extra = suffix == 0 ? "" : "-" + suffix;
      Path quarantine = parent.resolve(ledgerPath.getFileName() + ".corrupt-" + stamp + extra);
      if (Files.exists(quarantine)) continue;
      try {
        Files.copy(ledgerPath, quarantine);
        return Optional.of(quarantine);
      } catch (IOException ignored) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  static byte[] serialize(Map<UUID, PlayerReturnRecord> records) {
    StringBuilder output = new StringBuilder(HEADER).append('\n');
    records.values().stream()
        .sorted(Comparator.comparing(record -> record.playerId().toString()))
        .forEach(record -> output.append(serializeRecord(record)).append('\n'));
    return output.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String serializeRecord(PlayerReturnRecord record) {
    String worldName = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(record.worldName().getBytes(StandardCharsets.UTF_8));
    return String.join(
        "\t",
        Integer.toString(record.schemaVersion()),
        record.recoveryId().toString(),
        record.playerId().toString(),
        record.worldId().toString(),
        worldName,
        Double.toString(record.x()),
        Double.toString(record.y()),
        Double.toString(record.z()),
        Float.toString(record.yaw()),
        Float.toString(record.pitch()),
        Long.toString(record.createdAt().toEpochMilli())
    );
  }

  static Map<UUID, PlayerReturnRecord> parse(byte[] bytes) throws LedgerFormatException {
    String content = new String(Objects.requireNonNull(bytes, "bytes"), StandardCharsets.UTF_8);
    String[] lines = content.split("\\R", -1);
    if (lines.length == 0 || !HEADER.equals(lines[0])) {
      throw new LedgerFormatException("Missing or unsupported ledger header.");
    }

    Map<UUID, PlayerReturnRecord> parsed = new LinkedHashMap<>();
    Set<UUID> recoveryIds = new HashSet<>();
    for (int index = 1; index < lines.length; index++) {
      String line = lines[index];
      if (line.isEmpty()) {
        if (index == lines.length - 1) continue;
        throw new LedgerFormatException("Unexpected blank line at " + (index + 1) + ".");
      }
      String[] fields = line.split("\\t", -1);
      if (fields.length != 11) {
        throw new LedgerFormatException("Expected 11 fields at line " + (index + 1) + ".");
      }

      try {
        int schemaVersion = Integer.parseInt(fields[0]);
        UUID recoveryId = UUID.fromString(fields[1]);
        UUID playerId = UUID.fromString(fields[2]);
        UUID worldId = UUID.fromString(fields[3]);
        String worldName = new String(Base64.getUrlDecoder().decode(fields[4]), StandardCharsets.UTF_8);
        PlayerReturnRecord record = new PlayerReturnRecord(
            schemaVersion,
            recoveryId,
            playerId,
            worldId,
            worldName,
            Double.parseDouble(fields[5]),
            Double.parseDouble(fields[6]),
            Double.parseDouble(fields[7]),
            Float.parseFloat(fields[8]),
            Float.parseFloat(fields[9]),
            Instant.ofEpochMilli(Long.parseLong(fields[10]))
        );
        if (parsed.putIfAbsent(playerId, record) != null) {
          throw new IllegalArgumentException("Duplicate player UUID");
        }
        if (!recoveryIds.add(recoveryId)) {
          throw new IllegalArgumentException("Duplicate recovery UUID");
        }
      } catch (IllegalArgumentException exception) {
        throw new LedgerFormatException(
            "Invalid field at line " + (index + 1) + ": " + exception.getMessage()
        );
      }
    }
    return parsed;
  }

  private static void writeAtomically(Path target, byte[] bytes) throws IOException {
    Path parent = target.getParent();
    if (parent == null) throw new IOException("Ledger path has no parent directory.");
    Files.createDirectories(parent);

    Path temporary = parent.resolve(target.getFileName() + ".tmp-" + UUID.randomUUID());
    boolean moved = false;
    try {
      try (FileChannel channel = FileChannel.open(
          temporary,
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE
      )) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
        channel.force(true);
      }

      try {
        Files.move(
            temporary,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        );
      } catch (AtomicMoveNotSupportedException unsupported) {
        throw new IOException("Atomic replacement is not supported for " + target, unsupported);
      }
      moved = true;
    } finally {
      if (!moved) Files.deleteIfExists(temporary);
    }
  }

  static final class LedgerFormatException extends IOException {
    LedgerFormatException(String message) {
      super(message);
    }
  }
}
