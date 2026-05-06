# GitHub Actions CI/CD Verification

This document records GitHub Actions evidence for the TreasureRun i18n / ResourcePack automation pipeline.

## Verified commit

Latest verified commit:

```text
ci: add 4-layer i18n CI/CD — 6-gate coverage regression guard, auto-regenerate ResourcePack ZIP+SHA1 on lang file changes, validate Fabric mod mapping completeness across all 20 languages
```

## GitHub Actions result

The latest `main` push completed successfully across the major workflows:

```text
completed success ... i18n-expansion-ci main push
completed success ... CI main push
completed success ... i18n-check main push
completed success ... i18n-ci main push
```

Detailed GitHub CLI evidence:

```text
STATUS  TITLE                                                                   WORKFLOW           BRANCH  EVENT  ID           ELAPSED  AGE
✓       ci: add 4-layer i18n CI/CD — 6-gate coverage regression guard, auto-r…  i18n-expansion-ci  main    push   25402925700  46s      about 34 minutes ago
✓       ci: add 4-layer i18n CI/CD — 6-gate coverage regression guard, auto-r…  CI                 main    push   25402925649  42s      about 34 minutes ago
✓       ci: add 4-layer i18n CI/CD — 6-gate coverage regression guard, auto-r…  i18n-check         main    push   25402925697  1m5s     about 34 minutes ago
✓       ci: add 4-layer i18n CI/CD — 6-gate coverage regression guard, auto-r…  i18n-ci            main    push   25402925636  1m2s     about 34 minutes ago
```

## 4-layer i18n expansion check evidence

```text
--- new 4-layer i18n expansion checks ---
Server-sendable keys: 5375
  ✓ asl_gloss: 75.1% (threshold: 73%)
  ✓ de: 86.6% (threshold: 85%)
  ✓ en: 75.1% (threshold: 73%)
  ✓ es: 86.6% (threshold: 85%)
  ✓ fi: 86.6% (threshold: 85%)
  ✓ fr: 86.6% (threshold: 85%)
  ✓ hi: 86.6% (threshold: 85%)
  ✓ is: 86.6% (threshold: 85%)
  ✓ it: 86.6% (threshold: 85%)
  ✓ ja: 86.6% (threshold: 85%)
  ✓ ko: 86.6% (threshold: 85%)
  ✓ la: 75.1% (threshold: 73%)
  ✓ lzh: 86.6% (threshold: 85%)
  ✓ nl: 86.6% (threshold: 85%)
  ✓ ojp: 86.6% (threshold: 85%)
  ✓ pt: 86.4% (threshold: 85%)
  ✓ ru: 86.6% (threshold: 85%)
  ✓ sa: 75.1% (threshold: 73%)
  ✓ sv: 86.6% (threshold: 85%)
  ✓ zh_tw: 86.6% (threshold: 85%)

PASS: No regression.
✓ All 20 langs have switch cases
  ✓ en -> en_us.json
  ✓ ja -> ja_jp.json
  ✓ de -> de_de.json
  ✓ fr -> fr_fr.json
  ✓ es -> es_es.json
  ✓ it -> it_it.json
  ✓ ko -> ko_kr.json
  ✓ ru -> ru_ru.json
  ✓ zh_tw -> zh_tw.json
  ✓ pt -> pt_br.json
  ✓ nl -> nl_nl.json
  ✓ fi -> fi_fi.json
  ✓ sv -> sv_se.json
  ✓ is -> is_is.json
  ✓ la -> la_la.json
  ✓ hi -> hi_in.json
  ✓ sa -> sa_in.json
  ✓ lzh -> lzh_hant.json
  ✓ ojp -> ojp_jp.json
  ✓ asl_gloss -> asl_us.json
PASS: Fabric mapping complete.
```

## What this proves

This verification shows that TreasureRun is not only a Minecraft plugin feature project.

It also includes CI/CD quality control around the platform-boundary i18n architecture:

- standard plugin build verification
- i18n YAML validation
- missing-key regression checks
- suspicious display-text checks
- 4-layer i18n expansion checks
- Fabric Mod language mapping verification
- ResourcePack ZIP / SHA1 automation for language asset changes

## Portfolio interpretation

This supports the engineering story that TreasureRun treats Minecraft standard-message i18n as a system-maintenance problem:

- platform constraints are analyzed rather than ignored
- heavy language assets are separated from lightweight runtime payloads
- ResourcePack / Fabric Mod / Spigot / ProtocolLib layers are validated through CI
- new language-file changes can be checked automatically
- the project shows operational quality control, not only feature implementation
