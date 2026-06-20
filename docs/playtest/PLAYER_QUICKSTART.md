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

## 6. Try the wandering-trader custom trade

The prepared demo world includes a wandering-trader bonus interaction.

Watch the wandering-trader custom trade demo on YouTube:

https://youtu.be/OQbwYl85oRw

The clearest custom-trade path to test is:

```text
5 Special Emeralds -> 1 Golden Apple
```

Important: test this while a run is active. If the game has already ended and you see a “Time is up” or “Game Over” message, start a new run before testing the trade.

To prepare the trade items quickly, run:

```text
/give @s diamond 15
/craftspecialemerald
/craftspecialemerald
/craftspecialemerald
/craftspecialemerald
/craftspecialemerald
```

Each `/craftspecialemerald` command converts 3 diamonds into 1 Special Emerald. After five successful crafts, you should have 5 Special Emeralds.

Before opening the trader UI, hold one of the Special Emeralds in your hand and run:

```text
/checktreasureemerald
```

If the item is recognised, continue with the trade.

To complete the trade:

1. make sure a treasure run is active;
2. right-click the Treasure Shop wandering trader;
3. select the trade that exchanges 5 Special Emeralds for 1 Golden Apple;
4. place the 5 Special Emeralds into the left input slot;
5. check whether the Golden Apple appears in the result slot;
6. take the Golden Apple from the result slot;
7. confirm that the trade triggers visible and audible feedback.

If the result slot stays empty, check these first:

- the run may have already ended;
- you may have fewer than 5 Special Emeralds;
- the item may not be recognised as a TreasureRun Special Emerald;
- the selected trade may not match the item you placed in the input slot.

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

## 9. Good feedback from a first-time tester

Useful alpha feedback does not need to be a deep code review.

The most helpful notes are:

- whether the README made the game understandable before the i18n details;
- whether the local server startup worked;
- whether joining `localhost:25575` worked;
- whether the start commands were clear;
- whether the treasure hunt felt playable;
- whether it was clear what to do after `/gamestart`;
- whether the wandering-trader trade path was understandable;
- whether the custom trade demo matched what happened in-game;
- what was confusing, missing, or too much.

TreasureRun is currently an alpha project targeting Spigot 1.20.1. Please report only what you actually tried and observed.
