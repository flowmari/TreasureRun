# Player Quickstart

This is the shortest path from a local TreasureRun checkout to a useful first playtest.

The goal is simple:

```text
Can a first-time player start the local server, join the game, start a treasure run, and understand what to do next?
```

The localisation and platform-boundary architecture are documented separately. Start here if you want to experience the game first.

## 1. Start the local test server

From the repository root, run:

```bash
./scripts/contributor-up.sh YourMinecraftName
```

Example:

```bash
./scripts/contributor-up.sh flowmari
```

The script builds the plugin, starts an isolated local Spigot 1.20.1 server, starts MySQL through Docker Compose, installs the current TreasureRun plugin JAR, and grants operator permissions to the player name you passed in.

## 2. Join from Minecraft Java Edition 1.20.1

Connect to:

```text
localhost:25575
```

Use Minecraft Java Edition 1.20.1. Other client versions are not the verified target for this alpha setup.

Accept the ResourcePack prompt if Minecraft shows one.

## 3. Start a basic treasure run

The fastest first test path is:

```text
/lang en
/gamestart normal
/gameRank weekly
```

You can also try:

```text
/lang ja
/gamestart easy
/gamestart hard
```

If no language has been saved yet, `/gamestart normal` may open the language selection GUI first. Choose a language, then continue into the run.

## 3.1 Optional sound check

Before the run, make sure Minecraft **Options > Music & Sounds > Players** is audible. If you are an operator, run:

```text
/heartbeatTest
```

You should hear a short heartbeat cue. This checks the same sound category used by the in-game heartbeat feedback.

## 4. What to do after `/gamestart`

During a run, the player should:

1. follow the treasure-proximity cues;
2. search the stage for treasure chests;
3. open treasure chests to gain score and rewards;
4. watch for staged visual and sound feedback;
5. check the ranking after the run with `/gameRank weekly`.

For a first playtest, do not try to review every system. The most useful question is:

```text
Was it clear what the player should do next?
```

## 5. Optional: use the prepared demo world

A freshly generated local world is enough for a basic startup and command test.

To test the same kind of staged environment shown in the README demo, use the prepared demo-world setup described in:

```text
docs/external/ALPHA_TESTER_SETUP_GUIDE.md
```

The prepared demo world includes a UFO encounter, a Treasure Shop wandering trader, two trader llamas, a moving safe zone, visual trail effects, proximity sound cues, and outcome messages.

For the tester-facing world guide, see:

```text
docs/demo-world/WHAT_TO_LOOK_FOR.md
```

## 6. Try the Treasure Shop secret trade

The prepared demo world includes a Treasure Shop wandering trader bonus interaction.

Watch the Treasure Shop custom-trade demo on YouTube:

https://youtu.be/eARQ0AHZNoI

The clearest secret-trade path to test is:

```text
5 Special Emeralds -> 1 Golden Apple
```

Important: test this while a run is active. If the game has already ended and you see a “Time is up” or “Game Over” message, start a new run before testing the trade.

To prepare the trade items quickly with the default demo configuration, run:

```text
/give @s diamond 15
/craftspecialemerald
/craftspecialemerald
/craftspecialemerald
/craftspecialemerald
/craftspecialemerald
```

By default, each `/craftspecialemerald` command converts 3 diamonds into 1 Special Emerald. The required diamond amount is configurable through `craftSpecialEmerald.requiredDiamonds`.

After five successful crafts, you should have 5 Special Emeralds.

Before testing the Treasure Shop interaction, hold one of the Special Emeralds in your hand and run:

```text
/checktreasureemerald
```

If the item is recognised, continue with the secret trade.

To complete the trade:

1. make sure a treasure run is active;
2. make sure you have 5 TreasureRun Special Emeralds in your inventory;
3. right-click the Treasure Shop wandering trader;
4. confirm that 5 Special Emeralds are consumed;
5. confirm that 1 Golden Apple is added to your inventory;
6. confirm that the trade triggers visible and audible feedback.

Do not manually place the Special Emeralds into the vanilla trader input slots. TreasureRun handles this secret trade directly when you interact with the Treasure Shop trader.

If the Golden Apple is not added, check these first:

- the run may have already ended;
- you may have fewer than 5 Special Emeralds;
- the item may not be recognised as a TreasureRun Special Emerald;
- your inventory may be full.

For normal playtesters, use `/craftspecialemerald`. The `/givespecialemerald` command is intended for operator/admin testing only.

## 7. Useful player commands

```text
/lang en
/lang ja
/lang list
/lang current
/gamestart easy
/gamestart normal
/gamestart hard
/gameMenu
/gameRank weekly
/gameRank monthly
/gameRank all
/gameEnd
/craftspecialemerald
/checktreasureemerald
```

The full command reference is here:

```text
docs/COMMANDS.md
```

## 8. Stop or reset the local runtime

Stop the local runtime while keeping its world and database volumes:

```bash
./scripts/contributor-down.sh
```

Reset the local runtime completely:

```bash
./scripts/contributor-down.sh --volumes
```

## 9. Share first-time feedback

Useful alpha feedback does not need to be a deep code review.

After trying the quickstart, please report only what you actually tried and observed, especially:

- whether the local server startup worked;
- whether joining `localhost:25575` worked;
- whether it was clear what to do after `/gamestart`;
- whether the treasure hunt and wandering-trader trade path were understandable;
- what was confusing, missing, or too much.

TreasureRun is currently an alpha project targeting Spigot 1.20.1.
