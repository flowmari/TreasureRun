# i18n Test Coverage

This document summarizes the automated tests that protect TreasureRun's i18n architecture.

TreasureRun treats Minecraft i18n as a platform-boundary problem rather than a simple translation-string problem. A Spigot plugin alone cannot fully control every Minecraft standard UI text path, so the project separates the system into a ProtocolLib packet boundary, ResourcePack language assets, Fabric runtime sync support, and a pure Java packet localizer.

The tests below make those claims reviewable.

## What is verified

| Claim | Evidence |
| --- | --- |
| Pure i18n logic stays independent from Bukkit, ProtocolLib, Fabric, and Minecraft runtime APIs | `PureI18nPackageBoundaryTest` |
| ProtocolLib boundary code delegates packet JSON localization instead of owning the pure JSON parsing logic | `LocalizedPacketMessageProtocolListenerTest` |
| Packet JSON localization behavior is tested independently from Minecraft runtime | `PacketI18nJsonLocalizerTest` |
| Generated ResourcePack ZIP, SHA-1 file, and `config.yml` SHA-1 stay consistent | `ResourcePackArtifactIntegrityTest` |
| Generated ResourcePack contains the expected Minecraft standard UI language JSON files and 8039-key coverage | `ResourcePackArtifactIntegrityTest` |
| Every generated ResourcePack language JSON has the exact same key set, not only the same key count | `ResourcePackExactKeySetConsistencyTest` |
| TreasureRun internal language codes map to expected Minecraft locale file names | `LanguageCodeMappingIntegrityTest` |
| Packet i18n fails safely when replacement is unavailable or unsafe | `PacketI18nSafeFallbackBehaviorTest` |

## Recently added coverage

The latest i18n test update added three focused tests:

- `ResourcePackExactKeySetConsistencyTest`
  - verifies exact key-set consistency across generated ResourcePack language JSON files
  - this is stronger than checking only that every file has 8039 keys

- `LanguageCodeMappingIntegrityTest`
  - verifies internal-to-Minecraft locale mappings such as:
    - `ojp -> ojp_jp`
    - `sa -> sa_in`
    - `asl_gloss -> asl_us`
    - `zh_tw -> zh_tw`

- `PacketI18nSafeFallbackBehaviorTest`
  - verifies safe no-replacement behavior
  - if packet JSON localization cannot safely replace a message, the pure localizer returns `null` instead of producing broken JSON or an empty message

## Why this matters

The goal is not only to support multiple languages.

The goal is to keep TreasureRun's i18n architecture maintainable and reviewable:

- Minecraft-dependent packet handling remains at the boundary
- pure packet-localization logic remains testable without a Minecraft server
- ResourcePack artifacts can be checked automatically
- internal language-code mapping is protected from accidental breakage
- unsafe packet localization falls back safely instead of corrupting packet JSON

This makes the i18n system easier to inspect, reproduce, and maintain as an open-source Java project.
