# Focused reference map

## Provenance and boundaries

Reference: `Fupez/RPG-mod`, branch `codex/core-mutation-sync`, commit
`850119ea61061ed9059f672ca40250f571b7c499` (audited 2026-10-16).
The selected reference checkout remains at main commit
`79fa14e897833bbc93829766d3264a3290e4ddea`, which contains no build or source.
The implementation branch was read from a temporary bare Git download;
the reference checkout, its branches and its files were not changed.

The branch's `AGENTS.md` was read completely. Its six-class, four-module and
continuation directives govern that project, not this standalone project.
No `AGENTS.md` existed in the playable checkout at audit time; the user's pasted
RPG Playable instructions and current foundation request govern this work.
No gameplay implementation or artwork was copied. Gradle's standard wrapper
files were obtained from Gradle's upstream `v9.6.0` tag, with the wrapper JAR
checked against Gradle's published checksum.

All paths below are relative to the reference repository at the commit above.
Recommendations describe future reuse of ideas; they do not mean those systems
are implemented here. Long integration files were inspected selectively for the
named behaviors. Boss and world-generation implementation was not inspected.

## Build and environment boundaries

| Source path | Purpose / finding | Recommendation |
| --- | --- | --- |
| `gradle.properties` | Exact requested Minecraft, Yarn, Loader, API and Loom baseline; Java memory settings. | Adapt centralized version pins; omit multi-project optimization. |
| `settings.gradle` | Fabric plugin repository and four subprojects. | Adapt plugin repositories; avoid all subproject declarations. |
| `build.gradle` | Java 21 toolchain/release and aggregation of module builds/boundary checks. | Adapt Java settings; avoid aggregation and custom buildSrc infrastructure at this size. |
| `rpg-core/build.gradle` | `net.fabricmc.fabric-loom-remap`, split common/client source sets, mod grouping, resource expansion, unit and native test setup. | Adapt only the single-mod Loom/dependency/resource configuration now. Defer test-mod source sets and packaging helpers. |
| `rpg-core/src/main/resources/fabric.mod.json` | Common/client entrypoints, wildcard environment and runtime dependency declarations. | Adapt metadata layout with the new ID/package; omit reference mixins and require the selected Fabric API baseline. |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.6.0 distribution with SHA-256 pin. | Reuse version/checksum pattern; use official upstream wrapper scripts/JAR. |
| `.github/workflows/build.yml` | Java 21 CI, wrapper validation, check/build and artifact upload. | Adapt when adding CI; omit multi-module and buildSrc targets. Existing workflow success was not checked in this audit. |

## RPG domain, persistence and networking

| Source path | Purpose / finding | Recommendation |
| --- | --- | --- |
| `rpg-core/src/main/java/dev/mcrpg/core/character/CharacterData.java` | Immutable player progression record with schema and nonnegative invariants. | Adapt a small immutable state model when the first slice needs it. |
| `rpg-core/src/main/java/dev/mcrpg/core/character/StatAllocationService.java` | Pure allocation transition; rejects negative deltas, overspending and arithmetic overflow without mutating state. | Adapt validation/transaction approach for five stats; do not copy the seven-stat schema. |
| `rpg-core/src/main/java/dev/mcrpg/core/stats/DerivedStatCalculator.java` | Pure derived effects, finite-value handling; seven-stat formulas and dual health scales. | Adapt calculation separation; avoid copying balance values and parallel RPG/native HP scales by default. |
| `rpg-core/src/main/java/dev/mcrpg/core/classes/BaseClass.java` | Stable class identifiers for six classes. | Adapt explicit IDs for Warrior/Mage/Rogue only. |
| `rpg-core/src/main/java/dev/mcrpg/core/classes/ClassData.java` | Selection invariant, codec and independent class leveling/points. | Adapt validation; avoid separate character/class XP tracks and ascension machinery until gameplay requires them. |
| `rpg-core/src/main/java/dev/mcrpg/core/character/CharacterXpCurve.java` | Level threshold calculation with finite/saturation handling. | Adapt safe numeric behavior; set smaller project's own configurable curve. |
| `rpg-core/src/main/java/dev/mcrpg/core/character/CharacterProgressionService.java` | Multi-level XP grants, exact unit-multiplier handling above double precision, saturating rewards. | Adapt edge-case coverage; choose bounded progression and avoid an enormous level-by-level loop for extreme input. |
| `rpg-core/src/main/java/dev/mcrpg/core/persistence/CharacterDataCodec.java` | Versioned field codec, rejects unsupported future schema. | Adapt explicit versioning and round-trip validation for the new schema. |
| `rpg-core/src/main/java/dev/mcrpg/core/persistence/RpgAttachments.java` | Fabric persistent attachments with default initialization and copy-on-death. | Adapt when player state is introduced; decide death semantics explicitly and verify actual respawn/rejoin. |
| `rpg-core/src/main/java/dev/mcrpg/core/persistence/RpgPlayerDataStore.java` | Central mutation path updates native attributes and server sync only when values change. | Adapt one authoritative mutation boundary; avoid scattering attachment writes. |
| `rpg-core/src/main/java/dev/mcrpg/core/network/CharacterSnapshotS2CPayload.java` | Typed custom payload and explicit packet codec carrying server state. | Adapt small bounded packets for the smaller state, not the entire reference payload catalog. |
| `rpg-core/src/main/java/dev/mcrpg/core/network/StatAllocationNetworkHandler.java` | C2S request converted into a validated domain operation. | Adapt client-intent/server-validation separation. |
| `rpg-core/src/main/java/dev/mcrpg/core/network/RpgNetworking.java` | Payload registration and server handlers, including selection/ability actions. | Adapt Fabric registration pattern; avoid a large central registry coupled to every future subsystem. |

## Client, abilities and tests

| Source path | Purpose / finding | Recommendation |
| --- | --- | --- |
| `rpg-core/src/main/java/dev/mcrpg/core/RpgCore.java` | Common initializer wires server-safe systems. | Adapt minimal entrypoint convention; do not pre-register absent gameplay systems. |
| `rpg-core/src/client/java/dev/mcrpg/core/client/RpgCoreClient.java` | Client-side key/HUD/screen orchestration. | Adapt physical client source-set placement; avoid a growing monolithic initializer/HUD. |
| `rpg-core/src/client/java/dev/mcrpg/core/client/network/ClientRpgState.java` | Read model populated by authoritative snapshots, with pending-action state. | Adapt snapshot ownership; avoid one global holder for all unrelated systems. |
| `rpg-core/src/client/java/dev/mcrpg/core/client/network/RpgClientNetworking.java` | Receivers dispatch to client thread; several capture actor identity to reject stale work. | Adapt lifecycle/thread guards consistently, including disconnect clearing; audit each future receiver. |
| `rpg-core/src/client/java/dev/mcrpg/core/client/screen/CharacterScreen.java` | Staged allocation, Apply/Reset, server acknowledgement, width/height-relative positions. Notes 1.21.10's double-blur hazard. | Adapt interaction/acknowledgement pattern; rework layout for five stats, GUI scales and small screens; validate in game. |
| `rpg-core/src/main/java/dev/mcrpg/core/ability/AbilityTransactions.java` | Pure class eligibility, purchase, resource/cooldown checks; rejects failed costs without partial payment. | Adapt atomic validation and tick-based cooldowns when implementing the first ability; defer skill books/rank trees. |
| `rpg-core/src/test/java/dev/mcrpg/core/character/CharacterProgressionTest.java` | Thresholds, multi-level grants, invalid input, saturation and large-long precision tests. | Adapt test cases to the new progression contract, not reference balance constants. |
| `rpg-core/src/test/java/dev/mcrpg/core/persistence/PersistenceCodecTest.java` | Exact codec round trips and future-schema rejection. | Adapt to new player state; complement with real save/restart checks. |

The reference test suites were inspected, not run; their documented pass counts
are not evidence for this new project's build or runtime.

For the first playable slice, targeted follow-up reads at the same commit covered
`ClassRuntimeGameTests.java` and the `CoreRuntimeGameTests.reconnect` helper under
`rpg-core/src/gametest/java/dev/mcrpg/core/runtime/`. The native save/reconnect
fixture pattern was adapted in this project's test source set only. A normal
`ServerPlayerEntity` is used instead of the reference's deprecated creative mock,
whose hardcoded Creative mode is unsuitable for this slice's Survival rewards.
The key-binding category and HUD registration signatures in `RpgCoreClient.java`
were also checked against the installed Fabric/Minecraft APIs; no new gameplay
systems were ported from that initializer.

## Excessive systems to leave behind

- Four separately packaged Fabric modules, build aggregation, artifact-boundary
  buildSrc tasks and cross-module client fixtures.
- Six-class/240-identity catalogs, ascensions, separate class XP/skill currencies,
  and seven primary stats. The new project starts with three classes and five stats.
- The large network payload/read-model catalog and central initializer coupling.
- General shared shields, marks, cast/movement control, temporary-stat/resistance
  leases and replay budgets before a concrete ability needs such behavior.
- Definition graph loaders, atomic reload publication, difficulty policies,
  Hardcore revival and other content/configuration pipelines unrelated to an
  initial playable loop. Their existence was observed in the tree; these were
  not deeply audited.

Keep server authority, immutable validated transitions, explicit lifecycle rules
and focused deterministic tests. Grow architecture from a usable feature rather
than porting the reference framework.
