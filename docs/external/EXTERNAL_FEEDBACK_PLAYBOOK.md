# External Feedback Playbook

This playbook is for getting one realistic outside playtest from people who do not already know the project.

The goal is not a big launch. The goal is one useful external report.

## The narrow ask

Ask people to check one thing:

```text
Can a first-time tester start the local server, join it, start a treasure run, and understand what to do next?
```

Do not ask for a full code review first. Do not ask for production-readiness feedback. Do not ask people to judge the whole i18n architecture before they can understand the game.

A small, concrete ask gets more useful feedback than a broad launch post.

## Recommended public post

Hi everyone — I’m looking for one or two people to sanity-check the first-time player path for **TreasureRun**, an open-source Java / Spigot 1.20.1 treasure-hunt mini-game.

The ask is intentionally small:

1. clone the repo;
2. run `./scripts/contributor-up.sh YourMinecraftName`;
3. join `localhost:25575` from Minecraft Java Edition 1.20.1;
4. try `/lang en` and `/gamestart normal`;
5. tell me where the README, setup flow, or gameplay instructions become confusing.

There is also a short wandering-trader custom trade demo, but that part is optional.

A failed setup report is just as useful as a successful one. I’m not asking for a production-server review, Paper compatibility testing, or a full code review yet.

Best starting point:

```text
docs/playtest/PLAYER_QUICKSTART.md
```

Current boundaries:

- Spigot 1.20.1 only;
- early alpha;
- not production-ready;
- no Paper compatibility claim yet;
- feedback should be based only on what you actually tried.

Repository:

```text
https://github.com/flowmari/TreasureRun
```

## Short direct message

Hi — I’m looking for one small first-time playtest for TreasureRun, my open-source Spigot 1.20.1 treasure-hunt plugin.

The check is narrow: clone it, run `./scripts/contributor-up.sh YourMinecraftName`, join `localhost:25575`, try `/lang en` and `/gamestart normal`, and tell me where you get stuck or where the instructions become unclear.

There is also a short wandering-trader custom trade demo if you want to check that path, but the basic first-time setup test is already useful.

A failed setup report is still useful. I’m not asking for a full code review or production-server review.

The best starting point is:

```text
docs/playtest/PLAYER_QUICKSTART.md
```

## Where to ask first

Start small and specific:

- a GitHub issue or discussion in this repository;
- a small Minecraft plugin development community;
- a small Java developer community;
- one personal developer post;
- one direct message to someone who has already shown interest.

Do not spam large communities. One careful request with a narrow ask is better than ten broad posts.

## What not to claim yet

Do not say:

- production-ready;
- validated on Paper;
- ready for a public server;
- widely used;
- native-quality translations in every language;
- full control over every Minecraft client UI surface.

The strongest honest framing is:

```text
TreasureRun is an alpha Spigot 1.20.1 treasure-hunt plugin with a documented first-time local playtest path and a platform-boundary i18n architecture.
```
