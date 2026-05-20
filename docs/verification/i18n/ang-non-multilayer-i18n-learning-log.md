# ang/non Multi-layer i18n Learning Log

Generated at: `2026-05-19 18:22:29`

## Purpose

This log records evidence that TreasureRun's experimental historical Germanic locales are integrated into the same multi-layer i18n foundation as the existing language set.

Target locales:

- `ang -> ang_gb`: Old English / Ænglisc
- `non -> non_is`: Old Norse / Dǫnsk tunga

## Current Git State

```text
80d2e13 docs(i18n): add historical Germanic locale evidence
```

Working tree:

```text
(clean)
```

## 1. allowedLanguages includes ang/non

`src/main/resources/config.yml`

```text
allowedLanguages count: 22
contains ang: True
contains non: True
allowedLanguages:
- ja
- en
- de
- it
- sv
- es
- la
- is
- fi
- nl
- fr
- ru
- ko
- zh_tw
- sa
- pt
- hi
- lzh
- ojp
- asl_gloss
- ang
- non
```

## 2. Server-side language mapping

`src/main/resources/lang-map.yml`

```text
ang mapping: None
non mapping: None
```

## 3. Fabric-side language mapping

`fabric-i18n-mod/src/main/resources/lang-map.yml`

```text
ang mapping: None
non mapping: None
```

## 4. ResourcePack ZIP contains ang_gb/non_is

`resourcepacks/generated/treasurerun-i18n-pack.zip`

```text
ZIP path: resourcepacks/generated/treasurerun-i18n-pack.zip
language JSON count: 23
contains assets/minecraft/lang/ang_gb.json: True
contains assets/minecraft/lang/non_is.json: True
```

Language JSON entries:

```text
assets/minecraft/lang/ang_gb.json
assets/minecraft/lang/asl_us.json
assets/minecraft/lang/de_de.json
assets/minecraft/lang/en_us.json
assets/minecraft/lang/es_es.json
assets/minecraft/lang/fi_fi.json
assets/minecraft/lang/fr_fr.json
assets/minecraft/lang/hi_in.json
assets/minecraft/lang/is_is.json
assets/minecraft/lang/it_it.json
assets/minecraft/lang/ja_jp.json
assets/minecraft/lang/ko_kr.json
assets/minecraft/lang/la_la.json
assets/minecraft/lang/lzh_hant.json
assets/minecraft/lang/nl_nl.json
assets/minecraft/lang/non_is.json
assets/minecraft/lang/ojp_jp.json
assets/minecraft/lang/pt_br.json
assets/minecraft/lang/pt_pt.json
assets/minecraft/lang/ru_ru.json
assets/minecraft/lang/sa_in.json
assets/minecraft/lang/sv_se.json
assets/minecraft/lang/zh_tw.json
```

## 5. 8039-key coverage and exact key-set consistency

Reference: `assets/minecraft/lang/en_us.json`

```text
assets/minecraft/lang/ang_gb.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/asl_us.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/de_de.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/en_us.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/es_es.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/fi_fi.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/fr_fr.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/hi_in.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/is_is.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/it_it.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/ja_jp.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/ko_kr.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/la_la.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/lzh_hant.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/nl_nl.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/non_is.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/ojp_jp.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/pt_br.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/pt_pt.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/ru_ru.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/sa_in.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/sv_se.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/zh_tw.json: keys=8039, exact_same_as_en_us=True
```

Focused historical Germanic locale check:

```text
assets/minecraft/lang/ang_gb.json: keys=8039, exact_same_as_en_us=True
assets/minecraft/lang/non_is.json: keys=8039, exact_same_as_en_us=True
```

## 6. Visible UI sample values

### Old English / ang_gb

```text
language.name: Ænglisc
language.region: Englaland
menu.singleplayer: Āna plegian
menu.multiplayer: Mid ōðrum plegian
menu.options: Stillingas...
options.language: Sprǣc...
selectWorld.title: Woruld geceosan
```

### Old Norse / non_is

```text
language.name: Dǫnsk tunga
language.region: Norðrlǫnd
menu.singleplayer: Leika einn
menu.multiplayer: Leika með ǫðrum
menu.options: Stillingar...
options.language: Tunga...
selectWorld.title: Velja heim
```

## 7. SHA1 validation

```text
actual ZIP sha1:   4b12c7bed6d3f491601b623058079dda46a88cb5
recorded .sha1:    4b12c7bed6d3f491601b623058079dda46a88cb5
config sha1:       4b12c7bed6d3f491601b623058079dda46a88cb5
actual == recorded: True
actual == config:   True
```

## 8. GitHub Actions snapshot

```text
completed	success	docs(i18n): add historical Germanic locale evidence	i18n-check	main	push	26086988090	1m29s	2026-05-19T08:56:45Z
completed	success	docs(i18n): add historical Germanic locale evidence	CI	main	push	26086988085	1m10s	2026-05-19T08:56:45Z
completed	success	docs(i18n): add historical Germanic locale evidence	i18n-ci	main	push	26086988047	1m22s	2026-05-19T08:56:45Z
completed	success	fix(i18n): sync ResourcePack SHA1 after rebase	CI	main	push	26085462700	1m11s	2026-05-19T08:25:27Z
completed	success	fix(i18n): sync ResourcePack SHA1 after rebase	i18n-expansion-ci	main	push	26085462522	1m12s	2026-05-19T08:25:27Z
completed	success	fix(i18n): sync ResourcePack SHA1 after rebase	i18n-ci	main	push	26085462258	1m10s	2026-05-19T08:25:27Z
completed	success	fix(i18n): sync ResourcePack SHA1 after rebase	i18n-check	main	push	26085462253	1m26s	2026-05-19T08:25:27Z
completed	success	fix(i18n): sync ResourcePack SHA1 after rebase	resourcepack-sha1-autoupdate	main	push	26085461788	40s	2026-05-19T08:25:26Z
completed	failure	feat(i18n): add experimental Old English and Old Norse locale support	i18n-check	main	push	26082564712	58s	2026-05-19T07:22:01Z
completed	success	feat(i18n): add experimental Old English and Old Norse locale support	i18n-ci	main	push	26082564622	1m23s	2026-05-19T07:22:01Z
```

## Learning summary

The evidence above supports this precise statement:

> Old English and Old Norse are integrated into TreasureRun's same multi-layer i18n foundation: Plugin YAML, server-side language mapping, Fabric runtime language mapping, ResourcePack language JSON assets, SHA1 artifact validation, and CI/build checks. They extend Minecraft standard translation-key coverage to the ResourcePack-resolved/client language-key layer, while remaining experimental historical locales rather than native-quality complete historical translations.

## Honest limitation

This does not mean TreasureRun fully controls every Minecraft UI text path.

The accurate boundary is:

- ResourcePack-resolved Minecraft translation keys: supported
- server-observable / plugin-message-driven language sync: supported
- fully client-local UI and pre-login/client-owned screens: outside pure Spigot control
