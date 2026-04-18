# Batch 02 — LangCommand fallback literal removal

## Scope
- Remove player-facing fallback literals from LangCommand.java
- Keep locale YAML content unchanged
- Keep LanguageSelectGui as audit-only for this batch

## Why this batch is strong
- Direct-string-zero migration in Java
- No locale text rewrite
- Very low blast radius
- Easy to review
- Easy to gate and runtime-check
