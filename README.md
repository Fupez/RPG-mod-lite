# RPG Playable

A standalone Minecraft Java 1.21.10 Fabric RPG. Choose Warrior, Mage or Rogue,
fight hostile mobs, gain RPG XP, improve five stats, and use three abilities per
class. Vanilla XP and crafting remain available. This release covers the complete
three-class gameplay loop; `Fupez/RPG-mod` is reference material, not a dependency.

## Install and play

Requires **Java 21**, **Minecraft 1.21.10**, **Fabric Loader 0.19.5+**, and
**Fabric API 0.138.4+1.21.10**. Use compatible versions for Minecraft 1.21.10.

1. Install Fabric Loader for Minecraft 1.21.10 in your launcher.
2. Put Fabric API and `rpg-playable-0.1.0.jar` into the instance's `mods/` folder.
   Use the normal mod JAR, not the `-sources.jar`.
3. Enter a Survival or Adventure world. Press **K** to choose a class.
4. Use **R / Z / X** for its three abilities. Open **K → Abilities** for the
   in-game guide and **Key Binds** to change controls.
5. Defeat hostile mobs to earn RPG XP. Each level grants one point; spend it in
   **K** on Strength, Vitality, Agility, Intelligence or Spirit.

| Class | First ability (R) | Second (Z) | Third (X) |
| --- | --- | --- | --- |
| Warrior | Heavy Strike | Charge | Ground Slam |
| Mage | Firebolt | Arcane Burst | Blink |
| Rogue | Dash | Backstab | Fan of Knives |

Class choice is permanent per player save. Abilities target hostile mobs, consume
mana or stamina, and have a wind-up, recovery and cooldown. Movement abilities
require a clear route and safe landing; Charge and Ground Slam require the ground.
Creative and Spectator do not earn RPG XP or use abilities. Default hostile kills
give 25 XP, with level 2 at 100 XP. Levels and counters have no fixed cap;
native stat effects use safe diminishing returns at high values.

Strength improves melee damage and mining; Vitality adds health; Agility improves
movement and attack speed; Intelligence improves magic and mana; Spirit improves
resource regeneration and support. The HUD shows health, the class resource,
XP, level, unspent points and ability state. Combat uses native Minecraft sounds,
particles, item icons and player poses without an animation-library dependency.

## Multiplayer and saves

Install the same mod and Fabric API on the **server and each player's client**.
Use an ordinary Fabric dedicated server; the mod requires no external service.
The server validates class/stat requests, targeting, costs, cooldowns and damage.
Clients send input intent. Player animations and ability effects are visible to
nearby players.

Class, XP, level and all allocated stats survive death, logout and server restart.
Resources refill on death or server restart. Reconnecting during the same server
session retains resources and cooldowns; disconnecting or changing dimensions
interrupts a pending cast. Short cooldowns reset when the server restarts.
Existing schema-1 Warrior saves migrate automatically, including old level-100
saves. **Back up worlds before updating a modded installation.** In offline local
development, keep the same username to retain identity; test different classes in
fresh worlds or with separate multiplayer player saves.

Server tuning is created in `config/rpgplayable.properties` (XP and stat effects)
and `config/rpgplayable-abilities.properties` (cost, cooldown, wind-up, recovery,
base damage). Timings are in server ticks: 20 ticks is one second at normal TPS.
Restart the server after editing. Existing files are preserved; omitted settings
use defaults. Invalid values fail startup with an error rather than silently
changing balance. Client-only config edits do not tune a remote server.

## Develop and verify

Use a Java 21 **JDK** and the checked-in wrapper; global Gradle is unnecessary:

```sh
java -version
./gradlew --version
./gradlew check
./gradlew runGameTest -PrpgGameTests=true
./gradlew build
./gradlew runClient --args='--username RPGDeveloper'
```

Use `gradlew.bat` on Windows. JARs appear in `build/libs/`. CI runs checks, native
server tests and the build, and retains the installable JAR and unit-test reports.
The prepared cloud shell uses `source .gradle/cloud-env.sh` before Gradle.
Graphical clients need a display and OpenGL; headless server tests cannot assess
sound or subjective combat feel.

This is one Gradle module and one mod ID, `rpgplayable`. Common/server code lives
in `src/main/java`, presentation in `src/client/java`, unit tests in `src/test`,
and native server tests in `src/gametest`. Tests are excluded from the installable
JAR. Keep editable art in `asset-sources/` and exported assets in
`src/main/resources/assets/rpgplayable/`. See the
[focused reference map](docs/reference/reference-map.md); older slice documents
are historical.

Release 0.1.0 verification: Java 21, wrapper, `check` and `build` passed; 48 unit
tests and 17 native checks passed (16 RPG tests plus the runner baseline). Three
real clients exercised class selection, abilities and progression on an isolated
Fabric dedicated server. The minimum 320×240 GUI, remote player/armor poses,
packaged client/server JARs, and saved progression after server restart were
checked in-game. Cloud testing used local offline identities and software OpenGL;
authenticated online sessions and audible output could not be checked here.
