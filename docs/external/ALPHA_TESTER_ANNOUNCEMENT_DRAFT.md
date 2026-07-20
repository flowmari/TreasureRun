# Alpha Tester Announcement Draft

> [!NOTE]
> Historical pre-publication document. TreasureRun was subsequently published on SpigotMC with `v0.1.9-alpha`. This file is retained as process history and must not be used as the current project status.

This is a draft for a small, careful alpha call-for-testers.

It is intentionally kept as a repository draft first. Do not publish it to SpigotMC, PaperMC, Reddit, Discord, or other public communities until the maintainer has reviewed the packaging boundary, Paper compatibility status, and support capacity.

## Recommended first publication channel

Use this first in a controlled place, such as:

- a GitHub Discussion;
- a GitHub issue comment;
- a personal developer post;
- a small trusted Minecraft / Java developer circle.

Do not present TreasureRun as production-ready.

## Short version

I’m looking for a few early testers for **TreasureRun**, an open-source Java / Spigot 1.20.1 Minecraft plugin.

TreasureRun is a treasure-hunt mini-game, but the main engineering focus is platform-boundary i18n. Minecraft text is split across server-side plugin messages, server-to-client packets, ResourcePack language assets, and client-side language state, so the project documents which parts a Spigot plugin can control and which parts require another layer.

The local contributor setup has now been measured from a fresh clone. On my machine, the documented startup command completed successfully in about 39 seconds.

I’m especially looking for feedback on:

- whether the setup guide is clear;
- whether the server starts successfully on another machine;
- whether the first-join flow is understandable;
- whether the ResourcePack prompt is clear;
- whether the documentation explains the current limitations honestly.

Current scope:

- target: Spigot 1.20.1;
- status: early alpha;
- not production-ready;
- no Paper compatibility claim yet;
- no native-level translation-quality claim for every language yet.

Setup guide:

- `docs/external/ALPHA_TESTER_SETUP_GUIDE.md`

Fresh-clone evidence:

- `docs/external/FRESH_CLONE_QUICKSTART_EVIDENCE.md`

Good first issues are also available for small documentation, translation wording, and gameplay-configuration review tasks.

## Longer version

Hi everyone,

I’m looking for a few early testers for **TreasureRun**, an open-source Java / Spigot 1.20.1 Minecraft plugin.

TreasureRun is a treasure-hunt mini-game, but its main engineering goal is to demonstrate a realistic platform-boundary i18n design in Minecraft. A Spigot plugin alone cannot honestly control every Minecraft UI text surface, because some text belongs to plugin messages, some appears in server-to-client packets, some comes from ResourcePack language assets, and some depends on client-side language state.

Instead of pretending that one layer can control everything, TreasureRun separates the system into several responsibilities:

- Spigot plugin messages for gameplay-owned text;
- ProtocolLib packet-boundary handling for reachable translatable packet content;
- ResourcePack language assets for Minecraft standard translation keys;
- optional Fabric runtime language sync for client-side language-switching experiments;
- pure Java localization logic that can be tested without Bukkit, ProtocolLib, Fabric, or Minecraft runtime imports.

The project now has a contributor-first local setup path. A fresh-clone measurement has been recorded, and the documented local startup command completed successfully on my machine.

What I would like testers to check:

- Can you follow the alpha tester setup guide?
- Does the local server start successfully?
- Can you connect to `localhost:25575` from Minecraft Java Edition 1.20.1?
- Is the ResourcePack prompt understandable?
- Are the project limitations explained clearly enough?
- Is there any step where a new contributor would get stuck?

Important boundaries:

- TreasureRun currently targets Spigot 1.20.1.
- Paper may work, but Paper compatibility is not claimed yet.
- This is not a production server release.
- Translation key coverage and i18n architecture are the focus; native-quality wording for every language is still a review/improvement area.
- External alpha feedback is still being collected.

If you try it, please report your OS, Docker version, Minecraft version, whether the startup command completed, whether you could join the local server, and any logs or screenshots that help reproduce issues.

Thank you for taking a look.

## Ultra-short social version

I’m preparing early alpha testing for TreasureRun, an open-source Java / Spigot 1.20.1 Minecraft plugin.

It’s a treasure-hunt mini-game, but the engineering focus is platform-boundary i18n: Spigot messages, ProtocolLib packet handling, ResourcePack language assets, optional Fabric runtime sync, and testable pure Java localization logic.

Fresh-clone local startup is now measured and documented. I’m looking for a few testers to check whether the setup guide works on another machine.

Not production-ready. No Paper compatibility claim yet. Feedback welcome.
