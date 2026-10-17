---
name: minecraft-model-animation
description: Plan, create, review or integrate player-animation, model, texture or animation assets for RPG Mod Lite; use when a task touches Blockbench, GeckoLib, render assets or exported animation files.
---

# Lite model and animation workflow

1. Read the Lite asset and animation rules in \`AGENTS.md\`. Preserve the standalone, gameplay-first design and keep the read-only \`Fupez/RPG-mod\` project unmodified.
2. Inspect the current client animation and rendering code on the active Lite branch before proposing another system. Prefer the existing player-pose approach when it fits.
3. Keep editable Blockbench sources under \`asset-sources/blockbench/\` and \`asset-sources/animations/\`; keep runtime exports under \`src/main/resources/assets/\`.
4. Treat Blockbench as a separately installed authoring app. Codex may help prepare or inspect exported files; a person must preview/export in Blockbench unless a supported connector is present.
5. Verify GeckoLib's exact 1.21.10 Fabric support before proposing the dependency. The official matrix currently lists 5.3-Alpha-3; do not make it a default dependency. If a feature requires GeckoLib, keep its integration focused and validate client and dedicated-server loading.
6. Do not claim animation acceptance from source inspection or compilation alone. Report whether Blockbench preview and in-game client review were completed.

See [the Lite toolchain guide](../../../docs/development/codex-toolchain.md) for setup and compatibility pointers.
