# Alpha Outreach Boundaries

This document defines what TreasureRun may and may not claim during early alpha outreach.

## Allowed claims

TreasureRun may currently claim:

- it is an open-source Java / Spigot 1.20.1 Minecraft plugin;
- it includes a playable treasure-hunt mini-game;
- it demonstrates a platform-boundary i18n architecture;
- it separates plugin messages, packet-boundary handling, ResourcePack language assets, optional Fabric runtime sync, and pure Java localization logic;
- it has CI checks for build and i18n safety;
- it has ResourcePack checksum / integrity verification;
- it has optional Docker-backed MySQL / ranking-boundary evidence;
- it has a measured fresh-clone local contributor startup result;
- it has beginner-safe GitHub issues for first-time contributors.

## Claims to avoid

TreasureRun must not currently claim:

- production server readiness;
- Paper compatibility;
- SpigotMC publication readiness;
- native-level translation quality for every language;
- broad external adoption;
- external contributor traction before real external activity exists;
- full control over every Minecraft client UI surface from Spigot alone.

## Recommended next outreach sequence

1. Publish the alpha call in a small, controlled channel first.
2. Ask one or two trusted testers to follow the setup guide.
3. Collect environment details and failure logs.
4. Convert concrete feedback into GitHub issues.
5. Run a dedicated Paper compatibility test before claiming Paper support.
6. Only after that, prepare a SpigotMC Resource page draft.

## Career-facing interpretation

For recruiter or foreign-company review, the strongest honest framing is:

TreasureRun demonstrates that the maintainer can identify a real platform boundary, split responsibilities across appropriate layers, verify behavior with automated and runtime evidence, and prepare the project for external contributors without overstating maturity.
