# RPG Playable

A standalone Minecraft Java 1.21.10 Fabric RPG. Choose Warrior, Mage or Rogue,
fight hostile mobs, gain RPG XP, improve five stats, and use three abilities per
class. Vanilla XP and crafting remain available. This release covers the complete
three-class gameplay loop; `Fupez/RPG-mod` is reference material, not a dependency.

## Install and play

Requires **Java 21**, **Minecraft 1.21.10**, **Fabric Loader 0.19.5+**, and
**Fabric API 0.138.4+1.21.10**. Use compatible versions for Minecraft 1.21.10.

1. Install Fabric Loader for Minecraft 1.21.10 in your launcher.
2. Put Fabric API and `rpg-playable-0.2.0.jar` into the instance's `mods/` folder.
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

Version 0.2.0 adds a shared fantasy UI treatment, responsive class cards and stat
inspection with server-supplied effects, short menu/tab transitions, smooth dual
resource/health/XP bars, a damage trail, and restrained ability/level-up notices.
Ability particles now form timed, class-specific arcs, rings, spirals, projectile
traces and impact layers. See [visual effects and budgets](docs/visual-effects.md).
Use **the same mod version on server and clients**; 0.2.0 extends presentation
networking while retaining the existing progression save format.

Verification for this visual update: `check` and `build` passed with 75 unit tests;
17 native checks passed (16 RPG tests plus the runner baseline). Real clients
were launched against a dedicated Fabric server to inspect the wide/minimum GUI,
class confirmation, derived effects, stat feedback, health/resource/cooldown bars,
level-up presentation and ability effects. Audio balance and FPS on varied player
hardware still require local testing; cloud rendering uses software OpenGL.

## Local visual check

Run `./gradlew runClient` and use fresh worlds or separate multiplayer saves for
class choices. The normal RPG loop uses **K** and the rebound ability keys.

- Resize between minimum GUI, 16:9 and ultrawide: vitals, deck, native hearts and
  menus should remain readable. Inspect all five stat rows and derived effects;
  allocate a point and watch the value highlight and point deduction.
- Preview/confirm each class; open Abilities & Help, switch tabs, scroll Basics,
  and check short opening/hover transitions. Confirm rebound keys are displayed.
- Check mana/stamina drain and recovery, HP damage trail/low-health pulse,
  cooldown fill/numbers, ready pulse, and rejected-input/resource feedback.
- Level up: check the XP fill/reset, off-center notice and world ring/spiral.
- Warrior: Heavy Strike arc/hit sparks; Charge direction/material trail; Ground
  Slam expanding shockwave, debris and four-block perimeter.
- Mage: Firebolt visible lead/trail and wall impact; Arcane Burst gather/rings;
  Blink distinct departure/arrival and short spiral.
- Rogue: Dash narrow streaks; Backstab compact precision hit; Fan of Knives five
  separate traveling traces and wall/entity impacts.
- Check startup/impact/recovery against gameplay, first/third-person and observer
  views, daylight/night visibility, and All/Decreased/Minimal particles. Compare
  FPS in crowded combat; keep the core cues clear without covering the camera.
