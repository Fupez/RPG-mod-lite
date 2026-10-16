# RPG Mod Lite — Codex Instructions

## Purpose

Build a standalone, gameplay-first RPG mod for Minecraft Java Edition 1.21.10 using Fabric.

Priority order:
1. Playability
2. Combat feel
3. UI/UX quality
4. Player animations
5. Particles, sound and visual feedback
6. Stable RPG mechanics
7. Simple maintainable architecture
8. Future expansion

Do not overengineer systems for hypothetical future requirements.

## Reference project

`Fupez/RPG-mod` is READ-ONLY reference material.

You may inspect its code, branches, tests, docs and assets to understand prior RPG systems and reuse good ideas or implementation patterns.

Never modify it. Do not edit, commit, branch, merge, rebase, push, clean up or otherwise alter `Fupez/RPG-mod`.

Only `Fupez/RPG-mod-lite` may be modified.

Do not blindly copy the large project's architecture. Prefer a simpler implementation when it achieves the same gameplay result.

## Technical baseline

- Minecraft: 1.21.10
- Java: 21
- Yarn: 1.21.10+build.1
- Fabric Loader: 0.19.5
- Fabric API: 0.138.4+1.21.10
- Fabric Loom: 1.17.21
- Mod ID: `rpgmodlite`
- Base package: `dev.fupez.rpgmodlite`

Keep versions centralized in `gradle.properties` and use the repository Gradle wrapper.

## Architecture

Prefer a single-module project unless a real current requirement proves otherwise.

Keep common/server-safe code separate from client-only code. Common code must not import client-only Minecraft classes.

Visual systems belong in client code, including screens, HUD rendering, animations, particles, client key handling and screen transitions.

Authoritative player RPG state belongs in common/server-safe code where appropriate. Never trust the client for authoritative progression or combat state.

Prefer focused classes, clear interfaces, deterministic RPG logic and minimal abstraction.

Avoid speculative frameworks, unnecessary multi-module structure, duplicate systems and giant manager classes.

## Gameplay-first rule

Implement features vertically. A backend mechanic is not complete until the player can actually use and understand it.

Examples:
- XP includes persistent state, networking, HUD progress and level-up feedback.
- Stats include persistent values, derived effects, allocation rules and a proper character screen.
- Abilities include gameplay behavior, validation, resource cost, cooldown, networking, icon, input, animation, particles, sound and readable feedback.

## Initial gameplay loop

Choose class -> fight enemies -> gain XP -> level up -> gain stat points -> improve stats -> use abilities -> become stronger -> repeat.

Protect this loop from unnecessary complexity.

## Initial scope

Classes:
- Warrior
- Mage
- Rogue

Stats:
- Strength
- Vitality
- Agility
- Intelligence
- Spirit

Do not add more classes until these three have a complete usable loop.

## UI

UI is a core feature, not final polish.

Do not use commands/chat as the primary normal RPG interface.

Build proper interfaces for class selection, character stats, XP/level, health, mana/stamina, abilities, cooldowns and progression.

Support different Minecraft GUI scales and common resolutions. Prefer responsive positioning instead of hardcoded absolute coordinates.

## Animation

Player animation is a core system. Combat actions should communicate startup, impact/cast point and recovery.

Synchronize gameplay effects with animation timing where practical, but animation must never become authoritative gameplay logic.

## Assets

Keep editable sources under `asset-sources/` and runtime exports under `src/main/resources/assets/`.

Use source folders such as:
- `asset-sources/blockbench/`
- `asset-sources/animations/`
- `asset-sources/ui/`
- `asset-sources/icons/`
- `asset-sources/textures/`

Never overwrite editable source artwork with generated/exported runtime files.

## Reference discovery

Do not repeatedly rescan all of `Fupez/RPG-mod`.

Record useful discoveries in `docs/reference/reference-map.md` with repository, branch/commit if relevant, path, purpose, and reuse/adapt/avoid recommendation.

Future work should consult that map before another broad reference search.

## Testing and verification

Prefer deterministic automated tests for XP thresholds, large XP values, leveling, stat allocation, invalid allocation, cooldowns, resource costs, persistence, class restrictions and derived stat calculations.

For a bounded task, run focused tests after the final changes and repeat only for failures or new changes.

Before a milestone is complete, run:
- `./gradlew check`
- `./gradlew build`

Use `./gradlew runClient` when a graphical client environment is available. A successful compile is not proof that UI or animation behavior is correct.

Do not repeatedly run expensive full builds after trivial edits.

## Git/work style

Keep changes scoped. Do not mix unrelated refactors with gameplay work.

For each bounded task:
1. inspect only relevant files and the reference map
2. implement the complete slice
3. run focused verification
4. fix failures
5. run final relevant verification
6. publish one concise checkpoint

Avoid excessive status narration and repeated project-history summaries.

## Definition of done

A feature is complete when its gameplay behavior works, relevant tests pass, client/server ownership is correct, persistence/networking work where required, the player can actually use it, the UI communicates its state, appropriate feedback exists, and relevant build checks pass.

Visual features also require manual in-game review.
