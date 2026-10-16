# RPG Playable

Standalone Minecraft Java 1.21.10 RPG project in `Fupez/RPG-mod-lite`.
The first playable slice adds Warrior selection, RPG XP from hostile mobs,
leveling, and Strength allocation with a real melee-damage bonus. Vanilla XP is
unchanged. Other class kits and abilities are not implemented yet.
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
./gradlew runGameTest -PrpgGameTests=true
./gradlew runClient
```

For persistence testing across client restarts, use a stable development name:
`./gradlew runClient --args='--username RPGDeveloper'`. Minecraft's default
development name changes between launches, which creates different offline player
identities. Keep the same name when checking an existing development save.

On Windows use `gradlew.bat`. The installable mod will be
`build/libs/rpg-playable-0.1.0.jar`; the sources JAR is not an installable mod.
Client startup requires a display, OpenGL and downloaded Minecraft assets.
Look for `RPG Playable initialized` and `RPG Playable client initialized`, then
confirm the title screen opens and closes normally. Compilation alone does not
prove that startup works. Do not commit generated worlds or runtime files.

Unit tests cover progression boundaries, allocation, config/save validation,
request payloads and client acknowledgement/lifecycle state. Native server
GameTests exercise hostile-kill rewards, actual attribute effects, save/rejoin,
packet codecs, and death/respawn. CI runs the unit/native checks and builds using
Java 21 and a Gradle dependency cache. Test classes are excluded from the
installable JAR. Graphical acceptance is separate from these checks.

Use `./gradlew build --refresh-dependencies` only to investigate broken
dependency caches, not as the default build command.

## Play the first slice

1. Join a Survival world and press **K** (rebindable under Controls → RPG Playable).
2. Choose **Warrior**. Selection is permanent for this initial slice.
3. Defeat hostile mobs. Default tuning grants 25 RPG XP per kill; four kills
   reach level 2 and award one stat point.
4. Open K and allocate Strength. Each point adds 0.5 vanilla attack damage by
   default; the screen displays the server's actual bonus.

The HUD shows XP/level/Strength; level-up feedback includes sound and particles.
Normal gameplay uses the screen rather than commands or chat. XP, class and points
survive save/rejoin and death. Creative/spectator kills, passive mobs and players
without a selected class receive no RPG XP. The level cap is 100; excess XP at cap
is discarded. This cap bounds even exceptionally large XP grants.

Server tuning is created once in `config/rpgplayable.properties`:

```properties
baseXp=100
xpStep=50
killXp=25
strengthDamage=0.5
```

Restart the server to apply edits. Invalid tuning fails startup with an explicit
error and does not replace the file. Reducing XP requirements normalizes banked
XP at next join/reward; already earned levels and allocations remain. The native
Strength modifier is recomputed from saved RPG state, never stored twice.

## Architecture and scope

One Gradle module and one mod ID, `rpgplayable`, under
`dev.fupez.rpgplayable`. `src/main/java` is common/server-safe;
`src/client/java` holds the client initializer and later visual/input code.
Loom compiles the source sets separately and packages both into one mod JAR.
Shared metadata lives in `src/main/resources/fabric.mod.json`.

The slice uses one versioned persistent Fabric attachment, two typed payloads,
and one server mutation boundary. There are no custom mixins, ability frameworks
or animation libraries. Keep editable assets in
`asset-sources/` and exported assets in `src/main/resources/assets/rpgplayable/`.
Editable source folders are provided for Blockbench, animations, UI, icons and
textures. Add future packages only when they contain real code.

`Fupez/RPG-mod` is strictly read-only reference material. Consult
[the focused reference map](docs/reference/reference-map.md) before further audits;
do not copy its four-module architecture, balance formulas or class catalog.

See [the slice contract and manual checks](docs/development/first-playable-slice.md)
for scope and remaining acceptance work. Mage, Rogue, the other four primary
stats and abilities remain future slices; they have no placeholder gameplay.
