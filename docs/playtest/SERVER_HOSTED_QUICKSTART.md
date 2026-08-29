# Server-hosted Quickstart

This is the shortest smoke test for the v0.2.0-alpha server-hosted TreasureRun flow.

Download the v0.2.0-alpha release JAR:
[`TreasureRun-0.2.0-alpha-all.jar`](https://github.com/flowmari/TreasureRun/releases/download/v0.2.0-alpha/TreasureRun-0.2.0-alpha-all.jar)

Expected SHA-256:
`b3acc8f573c988fc48b32e1448e558d74d8add09d108f5f52460a81a3e8a52c1`

Use a disposable or backed-up Spigot 1.20.1 test server with Java 17, one operator/admin, and
2–8 test players. Database/ranking checks, ProtocolLib packet localisation, and a locally generated
ResourcePack are outside this smoke test.

## Arena / map requirement

You do not need a DeathRun map or any other external map for this server-hosted smoke test.
TreasureRun uses its dedicated `treasurerun_arena` world and builds the current test stage there.

Use a disposable or backed-up test server, and do not use an unrelated existing world named
`treasurerun_arena`. Custom/prebuilt arena maps are not part of the current one-arena flow; that
belongs to the later ArenaRegistry / multi-arena work.

## Six-step smoke test

1. Stop the test server.
2. Put `TreasureRun-0.2.0-alpha-all.jar` in `plugins/` and remove any older TreasureRun JAR from
   that test server.
3. Start the server and join with 2–8 test players.
4. As an operator/admin, run `/treasurerun create`.
5. Each participating player runs `/treasurerun join`. `/treasurerun status` should show the
   participant count and report the waiting session as ready once at least 2 players have joined.
6. As an operator/admin, run `/treasurerun start`. The roster is locked, preparation is requested,
   and the shared round uses a 10-second countdown before RUNNING.

During an active server-hosted round, `/treasurerun stop` requests the shared cleanup path.
On stop, disconnect abort, or normal round cleanup, online participants should be returned through
the same recovery boundary; pending offline returns remain recoverable when the player reconnects.

## Useful boundary checks

- A ninth player must not be able to join.
- Starting with fewer than 2 players must be rejected.
- A second create/start request must not create a second round.
- Players must not be able to join after the waiting roster is locked.
- After cleanup, `/treasurerun status` should return to the idle state.

For the existing first-playtest route, keep using
[`PLAYER_QUICKSTART.md`](PLAYER_QUICKSTART.md).
