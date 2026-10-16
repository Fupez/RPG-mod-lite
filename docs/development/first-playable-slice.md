# First playable slice

Scope: choose Warrior using K → kill hostile mobs → gain RPG XP → level up →
spend a point on Strength → deal more vanilla melee damage. No abilities or
additional class kits are included. The existing single-module architecture stays.

Defaults are prototype tuning in `config/rpgplayable.properties`: 25 XP per
hostile kill, 100 XP for level 1, +50 required XP per subsequent level, one point
per level and +0.5 attack damage per Strength. Level caps at 100; excess XP at
cap is discarded. Creative/spectator kills, passive mobs and unselected players
award no RPG XP. Vanilla XP is untouched.

Player state is immutable and validated, persisted with a versioned Fabric
attachment, and copied on death. A pure transition function owns selection,
leveling and allocation. Clients send only selection/allocation intent; the server
checks eligibility and publishes snapshots including its tuning. Strength is a
namespaced temporary native attribute modifier, restored from saved RPG state on
join/respawn. No client value is used as authority.
Respawn restoration runs in an explicit phase after Fabric's default attachment
transfer. A native regression fixture resets derived damage late in the default
phase and requires the final restoration to recover it; callback registration
order must not leave correct saved stats with an incorrect native modifier.

Implementation sequence:
1. Test the pure transitions, config validation and codec; implement them.
2. Wire server death/lifecycle events, attachment persistence and typed packets.
3. Add a responsive character screen, XP HUD and level-up sound/particles.
4. Run unit tests, native GameTests (real combat/save/load/respawn), check/build,
   review the bounded change and commit. Graphical acceptance requires a display.

Manual acceptance (not implied by compilation): join a Survival world, open K,
select Warrior, defeat four hostile mobs, see level 2 and one point, allocate it,
check +0.5 damage, save/rejoin and die/respawn without losing progression. Confirm
small GUI scales, translated buttons, XP bar, level-up sound/particles and a
remote server connection. Rejoining must not replay level-up feedback. A client
without the mod is outside the supported gameplay workflow.

Verification commands:

```sh
./gradlew check build
./gradlew runGameTest -PrpgGameTests=true
./gradlew runClient
```

The native suite has three RPG tests plus the runner's baseline check. It uses real
server players over embedded transports, native player files, real hostile-mob
deaths and native respawn. It does not establish remote multiplayer acceptance.
Client smoke testing uses a disposable Survival world and vanilla commands only
to spawn/damage hostile fixtures; class selection and allocation use the real
screen and server request/acknowledgement path.

## Verified checkpoint — 2026-10-16

- 22 JUnit tests pass with zero failures, errors or skips; `check` and `build` pass.
- All four required native checks pass, including the deterministic respawn-order
  regression (damage 1.5 before/after, rather than losing the 0.5 modifier).
- Real Minecraft client launched under a local Xvfb/software-OpenGL display.
  Warrior selection, four attributed zombie-fixture kills, level-up banner and
  particles, and Strength allocation were observed through the normal client UI.
- Character screen and HUD were inspected at scaled 427×240 and 320×240 sizes;
  the RPG HUD sits above vanilla health/armor/hunger instead of overlapping them.
- A complete client/JVM stop and restart with the same offline test identity
  restored Warrior level 2, Strength 1 and zero unspent points. Actual client
  death/respawn retained the same state and displayed +0.50 attack damage.
- Remapped JAR contains both entrypoints, excludes test classes and has no
  client-class references in common classes. The reference checkout is unchanged.

Limits: no audio device was available, so audible feedback was not accepted.
Mojang public-key lookup is unavailable in this proxy-only test environment;
offline integrated-world testing worked, but authenticated remote multiplayer
and subjective combat balance remain unverified. These are not implied by the
unit/native checks or the client screenshots.
