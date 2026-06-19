# README Boundary Clarity

This note tracks a small documentation change made after external first-impression feedback.

## Feedback theme

The README could make TreasureRun look like two separate things:

- a gameplay plugin
- an i18n / localisation engineering project

The intended boundary is:

- TreasureRun is a gameplay plugin
- the i18n / platform-boundary work is an internal supporting layer
- the localisation details should explain what is implemented without becoming the primary product description

## Change made

- Kept gameplay first in the README
- Reworded the introduction so i18n is not described as a second project
- Added a concise architecture boundary near the localisation section
- Kept the existing implementation details for:
  - Spigot plugin messages
  - ResourcePack language assets
  - packet-boundary text handling
  - optional Fabric client sync
  - language-key consistency checks

## Design note

The i18n adapter is not being split into a separate plugin or library in this change.

That would be a larger design decision involving API boundaries, distribution, versioning, tests, setup flow, and documentation structure. If needed, it should be evaluated separately.
