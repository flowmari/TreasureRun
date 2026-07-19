# Crowdin setup scope

This note outlines a small documentation-only setup scope for Crowdin before any configuration or translation-file changes are made.

## Goal

Prepare a reviewable path for future translation review while keeping repository behavior stable.

## First step

Start with documentation only.

The first public step should explain the intended Crowdin setup scope before adding configuration files or changing implementation files.

## Why start with documentation

- It is easier to review than a configuration change.
- It keeps the first step reversible.
- It avoids changing runtime behavior.
- It gives contributors a clear review path before setup is added.

## Out of scope for the first step

- Java changes
- YAML changes
- translation-file changes
- ResourcePack changes
- Fabric changes
- ranking-api changes
- release changes
- release asset changes
- issue body changes
- issue comments

## Next step

Review this scope before adding configuration or changing implementation files.
