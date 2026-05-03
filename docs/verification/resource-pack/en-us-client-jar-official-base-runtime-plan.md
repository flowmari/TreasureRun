# en_us official client-jar base rebuild

Date: 2026-05-04 05:06:58

TreasureRun rebuilt the English-base resource pack files using the official Minecraft 1.20.1 client jar:

- extracted `assets/minecraft/lang/en_us.json`
- rebuilt `en_us.json`
- rebuilt English-derived custom language files:
  - `la_la.json`
  - `sa_in.json`
  - `asl_us.json`
- retained TreasureRun custom standard-message overrides
- added custom language metadata to `pack.mcmeta`

Resource pack SHA1:

`891770c1ad775938565afd668eaf09f7864be8f8`

Scope:

This expands post-join Minecraft standard-message localization coverage using
Mojang official Minecraft 1.20.1 language assets plus TreasureRun custom overrides.
It does not claim absolute control over pre-login, authentication, client settings,
or purely client-local UI.
