# External-feedback Follow-up Evidence

This document records how external first-impression feedback was converted into small, reviewable repository changes.

It is intentionally factual. It does not claim broad adoption, production readiness, or independent external tester success.

## External feedback themes

The external review raised five concrete points:

1. The project could read like two things shipped together: a gameplay plugin and an i18n adapter/library.
2. The README explained i18n more strongly than the actual gameplay path.
3. The localisation section did not make the implemented layers clear enough.
4. The gameplay demo should be watchable in YouTube instead of requiring an MP4 download.
5. The public contributor setup should pass the Minecraft player name as a script argument, not as an environment variable.

## Repository response

The follow-up work was split into small PRs instead of one broad rewrite.

| Area | Repository response |
|---|---|
| Gameplay-first README | Game design, player quickstart, command reference, and YouTube demos are now near the top of the README. |
| YouTube demo path | First-time readers can watch gameplay in the browser through YouTube links and the optional demo playlist. |
| Argument-first setup | The public setup path is now `./scripts/contributor-up.sh YourMinecraftName`. |
| Localisation explanation | The README describes plugin messages, player language storage, locale mapping, ResourcePack assets, packet JSON localisation, optional Fabric sync, and Spigot/client boundaries. |
| i18n architecture boundary | Pure packet JSON localisation logic was extracted into the internal `treasurerun-i18n-core` Gradle module. |
| ADR | The i18n-core extraction decision is recorded in `docs/architecture/adr/0001-extract-packet-i18n-core.md`. |

## Current boundary

TreasureRun remains a gameplay plugin first.

The i18n/platform-boundary work is a supporting engineering layer behind the gameplay plugin, not a competing public plugin or standalone library.

The current code boundary is:

```text
treasurerun-i18n-core
  pure Java packet JSON localisation logic

main TreasureRun plugin
  Bukkit / ProtocolLib adapter
  gameplay integration
```

## Why this matters

This follow-up shows the OSS workflow the project is trying to make visible:

```text
external feedback
-> issue/PR-sized changes
-> merged implementation/docs
-> explicit architecture boundary
-> release-ready alpha state
```

The next release tag should describe this as an alpha follow-up release, not as production readiness.
