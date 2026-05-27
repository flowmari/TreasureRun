# Contributing to TreasureRun

Thank you for your interest in TreasureRun.

TreasureRun is a Minecraft Spigot 1.20.1 mini-game plugin focused on maintainable Java architecture, internationalization, ResourcePack-based standard-message localization, Fabric runtime language synchronization, and CI-backed verification.

## Good first contribution areas

Good first contribution areas include:

- improving documentation
- reviewing or improving translations
- reporting language issues
- testing ResourcePack behavior
- testing Fabric runtime language synchronization
- reporting reproducible bugs

## Translation contributions

When contributing translations, please include:

- target language or locale
- affected file, UI text, or translation key
- current wording
- proposed wording
- reason for the change
- screenshot or reproduction steps if the change affects visible UI

Please keep translations consistent with the existing language style.

## Development setup

Expected environment:

- Java 17
- Gradle
- Spigot 1.20.1 API
- ProtocolLib for packet-level verification
- Docker-based local server testing when needed

Useful checks:

- `./gradlew test`
- `./gradlew clean build`
- ResourcePack ZIP / SHA verification
- i18n verification scripts under `tools/`

## Pull request expectations

Before opening a pull request:

- keep the change focused
- avoid unrelated formatting changes
- run the relevant tests
- explain what changed and why
- include screenshots or terminal evidence when the change affects runtime behavior

## Architecture principle

TreasureRun keeps Minecraft-dependent runtime code at the boundary and moves reusable logic into smaller, testable components where possible.

For PacketI18n, this means:

- ProtocolLib listener = Minecraft packet boundary adapter
- PacketI18nJsonLocalizer = pure-Java JSON localization logic
- ResourcePack / Fabric layers = client-side standard-message support
