# TreasureRun — Treasure Hunt Mini-Game (Spigot 1.20.1)

[![CI](https://github.com/flowmari/TreasureRun/actions/workflows/ci.yml/badge.svg)](https://github.com/flowmari/TreasureRun/actions/workflows/ci.yml)

A Minecraft Spigot mini-game plugin featuring **19-language i18n** with a **CI quality gate** to prevent missing/partial translations.

**Quality gate (CI):** On every push / pull request, GitHub Actions runs `./check_keys.sh` (fails if any i18n key is missing across 19 language packs) and then builds the plugin with Gradle (`shadowJar`).

> **i18n-first / keys-only in Java:** user-facing strings are externalized into `src/main/resources/languages/*.yml`, while Java code references keys only. CI enforces **zero missing keys** before changes can land.

---

## Overview

TreasureRun is a treasure-hunt mini-game for Minecraft (Spigot). Players search for chests within a time limit, earn loot and score, and view rankings with in-game effects.

---

## Requirements

- Minecraft: **Spigot 1.20.1**
- Java: **17**

---

## Build

```bash
./gradlew clean shadowJar
