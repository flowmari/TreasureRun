# Controlled Alpha Tester Post

This document contains a small-scope alpha tester post for TreasureRun.

It is intended for a controlled first round of feedback, such as:

- GitHub Discussions;
- a trusted Java / Minecraft developer friend;
- a small Discord or private community;
- one or two technical reviewers.

It is **not** intended as a SpigotMC / Paper community release post yet.

## Recommended short post

Hi everyone — I’m looking for a small number of alpha testers for **TreasureRun**, an open-source Java / Spigot 1.20.1 Minecraft plugin.

TreasureRun is a treasure-hunt mini-game, but the main engineering focus is a platform-boundary i18n architecture. Minecraft UI text is split across plugin messages, server-to-client packets, ResourcePack language assets, and client-side language state, so a Spigot plugin alone cannot honestly control every surface. This project explores that boundary instead of hiding it.

What I’m trying to test first:

- whether a fresh clone can start the local contributor runtime cleanly;
- whether the setup guide is understandable for another person;
- whether the local Spigot 1.20.1 server starts correctly through Docker;
- whether the ResourcePack prompt and language-related notes are clear;
- whether the project feels approachable for first-time contributors.

Current verified status:

- Fresh-clone QuickStart: PASS in a local measurement.
- Startup command completed successfully.
- Measured startup time: 39 seconds on the tested machine.
- Good first issues are open for translation wording, docs clarity, gameplay config review, and alpha feedback.
- The project currently targets Spigot 1.20.1.

Important boundaries:

- This is not a ready for live/public servers server release.
- This has not been separately validated on Paper yet.
- This has not been published to SpigotMC yet.
- Translation wording quality is still a review/improvement area.
- I’m looking for setup feedback first, not large-scale public adoption.

If you’re willing to try it, the best starting point is:

1. Clone the repository.
2. Follow the local setup guide.
3. Run the contributor startup command.
4. Try joining the local server.
5. Open an issue with your environment and what happened.

Useful feedback includes:

- “It worked on my machine.”
- “The setup guide confused me here.”
- “Docker failed at this step.”
- “Minecraft connected but the ResourcePack prompt was unclear.”
- “This would be easier if the README explained X earlier.”

Repository:

`https://github.com/flowmari/TreasureRun`

Best issue for feedback:

`Collect alpha tester feedback for the local Spigot setup`

Thank you — even one clean external setup report is extremely useful at this stage.

## Shorter DM version

Hi! I’m doing a very small alpha test for TreasureRun, my open-source Java / Spigot 1.20.1 Minecraft plugin.

It’s a treasure-hunt mini-game, but the technical focus is Minecraft platform-boundary i18n: plugin messages, packet text, ResourcePack language assets, and optional client-side language sync are separate boundaries, so the project documents and tests that split instead of pretending one layer controls everything.

I’m not looking for a big public launch yet. I only want to know whether another person can clone it, run the local Docker-based setup, join the server, and understand the setup guide.

Known boundaries:

- Spigot 1.20.1 target only.
- No separate Paper validation claim yet.
- Not ready for live/public servers.
- Translation wording still needs review.

Would you be willing to try the QuickStart and tell me where it works or breaks?

## Recruiter-safe summary

TreasureRun is an open-source Java / Spigot Minecraft plugin that demonstrates platform-boundary i18n. A Spigot plugin alone cannot control every Minecraft UI surface, so the project separates plugin-owned YAML messages, ProtocolLib packet boundaries, ResourcePack language assets, optional Fabric runtime sync, and pure Java localization logic. The repository includes CI checks, Docker-based contributor startup, ResourcePack checksum verification, Fresh Clone QuickStart evidence, and beginner-friendly issues for external feedback.

## Do not say yet

Do not say:

- “validated on Paper”
- “ready for live/public servers”
- “used by many people”
- “external contributors are active”
- “native-quality translation for every language”
- “official SpigotMC release”

until those claims have separate evidence.

## First alpha success criteria

The first alpha round is successful if at least one external person provides one of the following:

- a successful fresh-clone setup report;
- a failed setup report with environment details;
- a documentation correction;
- a ResourcePack prompt / language feedback issue;
- a small translation wording review;
- a first-time contributor question.

The goal is not popularity. The goal is credible external feedback.
