# RPG Playable

Standalone Minecraft Java 1.21.10 RPG project in `Fupez/RPG-mod-lite`.
This foundation loads the common and client initializers and changes no gameplay.
It requires Fabric Loader 0.19.5 or later and Fabric API 0.138.4+1.21.10
or a compatible later release for Minecraft 1.21.10. It does not depend on
`Fupez/RPG-mod` or any of that project's mod JARs.

## Development

Use a **Java 21 JDK** (`java -version` and `javac -version`), not only a JRE.
Use the checked-in Gradle 9.6.0 wrapper; no global Gradle is needed.
All Minecraft, mappings, Loader, Fabric API and Loom versions are in
`gradle.properties`. The wrapper distribution has a pinned SHA-256 checksum.

From this repository:

```sh
./gradlew --version
./gradlew check
./gradlew build
./gradlew runClient
```

On Windows use `gradlew.bat`. The installable mod will be
`build/libs/rpg-playable-0.1.0.jar`; the sources JAR is not an installable mod.
Client startup requires a display, OpenGL and downloaded Minecraft assets.
Look for `RPG Playable initialized` and `RPG Playable client initialized`, then
confirm the title screen opens and closes normally. Compilation alone does not
prove that startup works. Do not commit generated worlds or runtime files.

`check` currently has no gameplay unit tests: no gameplay logic exists yet.
JUnit Jupiter is configured for future tests under `src/test/java`.
CI runs `check` and `build` on pushes and pull requests using Java 21 and a
Gradle dependency cache. Add deterministic tests with the first gameplay slice. Use
`./gradlew build --refresh-dependencies` only to investigate broken dependency
caches, not as the default build command.

## Architecture and scope

One Gradle module and one mod ID, `rpgplayable`, under
`dev.fupez.rpgplayable`. `src/main/java` is common/server-safe;
`src/client/java` holds the client initializer and later visual/input code.
Loom compiles the source sets separately and packages both into one mod JAR.
Shared metadata lives in `src/main/resources/fabric.mod.json`.

There are no mixins, packets, player state, registrations, animation libraries,
content frameworks or gameplay features in this foundation. Add each only when
its first playable feature needs it. Keep future editable assets in
`asset-sources/` and exported assets in `src/main/resources/assets/rpgplayable/`.
Editable source folders are provided for Blockbench, animations, UI, icons and
textures. Future common packages (`player`, `stats`, `classes`, `abilities`,
`networking`, `persistence`, `registry`) and client packages (`hud`, `screens`,
`animation`, `particles`, `input`) should be added when they contain real code.

`Fupez/RPG-mod` is strictly read-only reference material. Consult
[the focused reference map](docs/reference/reference-map.md) before further audits;
do not copy its four-module architecture, balance formulas or class catalog.

First recommended slice: Warrior selection through a real screen, vanilla mob
combat granting visible RPG XP, one level awarding a point, and a simple Strength
allocation with a visible effect. Include server authority, save/rejoin, snapshots,
GUI-scale checks and level-up feedback. Defer the other class kits and ability
framework until this loop works in game.
