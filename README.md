# RPG Playable alpha

Standalone Minecraft Java 1.21.10 Fabric mod. Choose Warrior, Mage or Rogue,
earn RPG XP from hostile kills, allocate Strength, Vitality, Agility,
Intelligence and Spirit, and use three abilities per class. Vanilla XP is unchanged.
Level, XP and stat counters use arbitrary precision; native combat effects have
safe diminishing returns. Class choice is permanent for the alpha.

Requires Java 21, Fabric Loader 0.19.5+ and Fabric API 0.138.4+1.21.10
(or compatible later versions for Minecraft 1.21.10). No RPG-mod dependency.

## Develop and install

Use a Java 21 **JDK** and the checked-in Gradle wrapper:

```sh
./gradlew check
./gradlew build
./gradlew runGameTest -PrpgGameTests=true
./gradlew runClient --args='--username RPGDeveloper'
```

Use `gradlew.bat` on Windows. Install `build/libs/rpg-playable-0.1.0.jar`,
not the sources JAR. Keep the same development username when checking saved
progress: changing names creates a different offline player identity. The cloud
shell uses `source .gradle/cloud-env.sh` before Gradle. Graphical play requires a
display, OpenGL and Minecraft assets; server tests do not verify visual feel.

## Local acceptance checklist

- In a fresh Survival world press **K** and choose a class. Check Warrior, Mage
  and Rogue in separate fresh worlds (or separate multiplayer player saves). Reopening K must show the selected class.
- Defeat hostile mobs: default rewards are 25 RPG XP, with level 2 at 100 XP.
  Confirm level-up feedback, one point per level and unchanged vanilla XP rules.
  Passive mobs and Creative/spectator kills must not award RPG XP.
- Allocate all five stats. Strength improves melee/mining, Vitality increases
  health, Agility improves movement/attack speed, Intelligence improves magic
  and mana, and Spirit improves regeneration/support. Confirm the displayed
  effects and that points cannot be spent twice.
- Use **R / Z / X** (all rebindable): Warrior **Heavy Strike / Charge / Ground
  Slam**; Mage **Firebolt / Arcane Burst / Blink**; Rogue **Dash / Backstab /
  Fan of Knives**. Check impact timing, hand animation, sounds, particles,
  movement collision and hostile targeting. Firebolt must visibly travel.
- Check mana/stamina spending, regeneration, cooldown countdown and recovery.
  Spam keys, attempt casts without resources, and cast toward walls: the server
  must reject unavailable casts and movement must stay outside solid blocks.
- Check the HUD and K screen at normal and minimum GUI sizes: health,
  resources, XP/level, all three class slots, costs and cooldowns remain readable.
  Check first-person and a second player's view of ability impacts.
- Save/rejoin, restart the client with the same identity, and die/respawn.
  Class, XP, level and all allocated stats must survive; attributes must not
  double. Reconnect or respawn during a cast must cancel its delayed impact
  without providing a cooldown bypass.

Server tuning is created in `config/rpgplayable.properties` and the combat tuning
file `config/rpgplayable-abilities.properties`. Restart the server after editing. Invalid tuning fails with
an explicit startup error. Existing schema-1 Warrior saves migrate automatically;
old level-100 saves can continue leveling. All five stat coefficients are configurable
in `rpgplayable.properties` (see `StatTuning` for keys/defaults); legacy four-key
configs retain defaults for missing keys. Resources regenerate server-side and
restore to full on death or server restart. Reconnects retain resources and
cooldowns within the running session; the short cooldowns reset on server restart.
Warrior Heavy Strike evolves the earlier standalone Cleave, using the new shared
resource/cooldown path. No separate Cleave subsystem remains.

The project has one Gradle module and mod ID, `rpgplayable`. Common/server code
is in `src/main/java`, presentation in `src/client/java`, and native server tests
in `src/gametest`. Tests are excluded from the installable JAR. Editable art belongs
in `asset-sources/`; exported assets belong in `src/main/resources/assets/rpgplayable/`.
`Fupez/RPG-mod` is read-only reference material; see the
[focused reference map](docs/reference/reference-map.md). Earlier slice documents
are historical; the checklist above describes the alpha.

Verified alpha checkpoint: `check` and `build` pass; 39 unit tests and 17 native
checks pass (16 RPG tests plus the runner baseline), including all nine ability
effects and Fan of Knives hitting a hostile overlapping its launch point.
A real client loaded the previous Warrior save through schema migration,
selected Mage in a fresh world, and used R to spend mana and earn XP with Firebolt.
The hand renderer applied successfully. Final combat feel, audible output,
third-person/multiplayer presentation and broader GUI review remain local checks.
