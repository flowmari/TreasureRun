# ang/non Runtime Final Evidence

## Summary

Old English and Old Norse are integrated into TreasureRun's platform-boundary i18n system.

Target locales:

- `ang -> ang_gb`: Old English / Ænglisc
- `non -> non_is`: Old Norse / Dǫnsk tunga

## Verified results

- ResourcePack SHA1 synchronized
- ResourcePack loaded successfully
- Fabric client received lang payload
- `ang` mapped to `ang_gb`
- `non` mapped to `non_is`
- `options.txt` persisted `lang:non_is`
- ESC menu ResourcePack-resolved keys verified
- 8039-key exact consistency preserved
- Docker runtime config has no malformed SHA1 residue

## Evidence

### Client runtime evidence

```text
67:[20:23:44] [Render thread/INFO]: [TreasureRun i18n] 22 languages loaded from lang-map.yml
97:[20:24:36] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='non' minecraft='non_is'
98:[20:24:36] [Render thread/INFO]: [System] [CHAT] [TreasureRun] Resource pack accepted. Loading...
99:[20:24:36] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='non' minecraft='non_is'
107:[20:24:36] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='non' minecraft='non_is'
111:[20:24:37] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='non' minecraft='non_is'
115:[20:24:37] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='non' minecraft='non_is'
136:[20:24:38] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='non' minecraft='non_is'
140:[20:24:38] [Render thread/INFO]: [System] [CHAT] [TreasureRun] Resource pack loaded. Hybrid i18n layer is active.
141:[20:24:39] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='non' minecraft='non_is'
145:[20:24:40] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='non' minecraft='non_is'
169:[20:24:42] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: non_is
171:[20:24:42] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: non_is
173:[20:24:42] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: non_is
175:[20:24:42] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: non_is
177:[20:24:42] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: non_is
179:[20:24:42] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: non_is
181:[20:24:42] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: non_is
183:[20:24:42] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: non_is
184:[20:24:48] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='ang' minecraft='ang_gb'
191:[20:24:48] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='ang' minecraft='ang_gb'
213:[20:24:49] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: ang_gb
214:[20:24:50] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='ang' minecraft='ang_gb'
225:[20:24:52] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='ang' minecraft='ang_gb'
242:[20:24:52] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: ang_gb
244:[20:24:52] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: ang_gb
265:[20:24:55] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: ang_gb
266:[20:25:07] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='non' minecraft='non_is'
273:[20:25:07] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='non' minecraft='non_is'
295:[20:25:08] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: non_is
296:[20:25:09] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='non' minecraft='non_is'
302:[20:25:11] [Netty Client IO #4/INFO]: [TreasureRun i18n] lang sync received rawBytes=3 treasureRun='non' minecraft='non_is'
324:[20:25:12] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: non_is
326:[20:25:12] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: non_is
347:[20:25:15] [Render thread/INFO]: [System] [CHAT] §a[TreasureRun i18n] Client language applied: non_is
48:lang:non_is
```

### Docker/server runtime evidence

```text
[11:24:35] [Server thread/INFO]: [TreasureRun] [ResourcePack] sent multilingual pack to flowmari force=true sha1=058be76f24f41cb3f4c4cfe1d0e2a69035294aeb
[11:24:35] [Server thread/WARN]: [TreasureRun] [ResourcePackFallback] No pack for lang=non
[11:24:35] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=non bytes=3 reason=join-resync timing=now duplicate=false channel=treasurerun:lang
[11:24:36] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=non bytes=3 reason=join-resync timing=delay_10t duplicate=true channel=treasurerun:lang
[11:24:36] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=non bytes=3 reason=join:auto-sync timing=now duplicate=true channel=treasurerun:lang
[11:24:37] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=non bytes=3 reason=join:auto-sync timing=delay_10t duplicate=true channel=treasurerun:lang
[11:24:37] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=non bytes=3 reason=join-resync timing=delay_40t duplicate=true channel=treasurerun:lang
[11:24:38] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=non bytes=3 reason=join:auto-sync timing=delay_40t duplicate=true channel=treasurerun:lang
[11:24:38] [Server thread/INFO]: [TreasureRun] [ResourcePack][STATUS] player=flowmari uuid=470d7dc8-8ce4-48d7-a0e1-fa8a1acf30b5 status=SUCCESSFULLY_LOADED
[11:24:39] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=non bytes=3 reason=join-resync timing=delay_80t duplicate=true channel=treasurerun:lang
[11:24:40] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=non bytes=3 reason=join:auto-sync timing=delay_80t duplicate=true channel=treasurerun:lang
[11:24:48] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=ang bytes=3 reason=command:/lang timing=now duplicate=false channel=treasurerun:lang
[11:24:48] [Server thread/WARN]: [TreasureRun] [ResourcePackFallback] No pack for lang=ang
[11:24:48] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=ang bytes=3 reason=command:/lang timing=delay_10t duplicate=true channel=treasurerun:lang
[11:24:50] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=ang bytes=3 reason=command:/lang timing=delay_40t duplicate=true channel=treasurerun:lang
[11:24:52] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=ang bytes=3 reason=command:/lang timing=delay_80t duplicate=true channel=treasurerun:lang
[11:25:07] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=non bytes=3 reason=command:/lang timing=now duplicate=false channel=treasurerun:lang
[11:25:07] [Server thread/WARN]: [TreasureRun] [ResourcePackFallback] No pack for lang=non
[11:25:07] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=non bytes=3 reason=command:/lang timing=delay_10t duplicate=true channel=treasurerun:lang
[11:25:09] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=non bytes=3 reason=command:/lang timing=delay_40t duplicate=true channel=treasurerun:lang
[11:25:11] [Server thread/INFO]: [TreasureRun] [LanguageSync] sent selected lang only player=flowmari lang=non bytes=3 reason=command:/lang timing=delay_80t duplicate=true channel=treasurerun:lang
OK: no malformed SHA1 residue
```

### ResourcePack integrity and key consistency

```text
actual ZIP sha1: 058be76f24f41cb3f4c4cfe1d0e2a69035294aeb
recorded .sha1: 058be76f24f41cb3f4c4cfe1d0e2a69035294aeb
actual == recorded: True
config sha1 count: 23
config sha1 all match actual: True
ResourcePack language JSON count: 23
ang_gb keys: 8039
non_is keys: 8039
ang_gb exact same key set as en_us: True
non_is exact same key set as en_us: True

ang_gb ESC menu ResourcePack-resolved keys:
menu.game: Plega
menu.returnToGame: Tō plegan gecyrran
gui.advancements: Forðgangas
gui.stats: Getalu
menu.sendFeedback: Andswaru sendan
menu.reportBugs: Unriht gemeldian
menu.options: Stillingas...
menu.playerReporting: Plegere gemeldung
menu.disconnect: Tōslītan

non_is ESC menu ResourcePack-resolved keys:
menu.game: Leikr
menu.returnToGame: Aptr til leiks
gui.advancements: Framfarir
gui.stats: Tǫlur
menu.sendFeedback: Senda umsǫgn
menu.reportBugs: Tilkynna villur
menu.options: Stillingar...
menu.playerReporting: Leikmannaskýrsla
menu.disconnect: Rjúfa samband

```

## Honest boundary

This does not mean Spigot controls every Minecraft UI path.

It proves ResourcePack-resolved and server-observable language paths:

- ResourcePack-resolved Minecraft translation keys
- server-observable ResourcePack delivery status
- Fabric runtime client language switching
- plugin-message-driven language sync through `treasurerun:lang`
- SHA1 integrity validation

It does not claim:

- full control of every Minecraft UI text path
- pre-login UI control
- fully client-owned UI control by Spigot alone
