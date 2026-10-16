# Warrior: Cleave

Press R (rebindable under Controls → RPG Playable) as a living Warrior in
Survival/Adventure. The sword icon and key beside the hotbar show availability,
wind-up, recovery and remaining cooldown. Normal gameplay needs no commands.
The icon reuses Minecraft's iron sword item rendering; no custom artwork or new
asset dependency is needed for this prototype.

Default timing: 6 server ticks of wind-up, one hit at tick 6, 8 ticks of recovery,
ready again 100 ticks after activation. Basic entity attacks are rejected during
wind-up/recovery. Movement remains available. Facing is captured at activation;
range is measured from the player's position at impact. Turning cannot redirect
an already started attack. There is no stamina/mana cost in this slice.

The hit selects up to eight nearest living hostile mobs in a 100-degree frontal
arc, within 3 horizontal blocks and 2 vertical blocks, with native line of sight.
It excludes players and passive mobs. Damage is 6 + the player's configured
Strength bonus before native armor/invulnerability rules. Native player-attributed
damage feeds the existing hostile-kill RPG XP path. Held-weapon damage, enchantment
procs and ordinary melee cooldown scaling do not add to Cleave damage.

The server accepts only an empty activation intent. It owns eligibility, clock,
targets and damage. It limits network activation handling to once per player per
server tick. The client waits for authority, times out pending input, and receives
remaining-tick corrections every five ticks while on cooldown. Disconnect and
client player replacement clear presentation state; join, respawn and dimension
transfer explicitly resynchronize it. No client counter authorizes a hit.

Death, disconnection, entity replacement, dimension change or becoming ineligible
interrupts a pending hit. Interruption retains cooldown and releases the normal
attack restriction. UUID cooldowns last through reconnect/respawn in the same
server session; expired entries are removed. A server restart resets these short
cooldowns. No new permanent save schema is introduced.

First-person animation uses a mirrored procedural hand pullback/return around
vanilla rendering, including empty hands. The mixin lives only in the client
source set. Nearby players receive native preparatory/impact hand swings. Nine
small startup particles form the telegraph; nine sweep particles and at most five
crit particles per damaged target mark impact. Equip/sweep sounds accompany the
phases. Gameplay timing remains independent of animation playback and latency.

Server tuning is created once in `config/rpgplayable-cleave.properties`:

```properties
windupTicks=6
recoveryTicks=8
cooldownTicks=100
range=3
damage=6
```

Restart to apply edits. Invalid timing, non-finite values and excessive range or
damage fail startup; existing files are preserved.

Automated coverage includes exact timing boundaries, cone/range/vertical misses,
invalid tuning, file preservation, pending/timeout/cancellation presentation,
pose progression and packet validation. Native tests cover real delayed damage,
rear/passive exclusions, no repeated hit, cooldown expiry, death/reconnect
retention, stale-actor interruption, a solid wall, and payload wire round trips.
The existing progression/save/respawn checks remain in the suite.

Manual acceptance: start a Survival world, choose Warrior through K, press R
facing a hostile mob, observe hand startup/hit/recovery and the cooldown slot,
then repeat after readiness. Check small GUI scales, dimension transfer at idle,
and that a Cleave kill awards RPG XP. Commands may prepare disposable fixtures;
they are not the ability interface. Audio output, authenticated remote multiplayer
and subjective balance require separate acceptance on a normal game machine.

Verified checkpoint (2026-10-16): 32 JUnit tests pass without failures, errors or
skips; all seven required native checks pass (six RPG tests plus runner baseline).
`check`, `build` and Java 21/Gradle wrapper checks pass. The remapped JAR packages
the client-only mixin and excludes tests; common classes have no client references.
Real client acceptance under Xvfb/software OpenGL observed hand wind-up/return,
server-timed sweep/hit particles, a hostile kill via R awarding 25 RPG XP, cooldown
readiness, idle dimension-transfer resynchronization and a 320×240 scaled GUI.
The exact final client applies the mixin successfully. Startup/impact sounds are
implemented, but this machine has no audio device, so audible output remains
unverified. Third-person observer synchronization and authenticated remote
multiplayer were not manually accepted. Subjective balance remains prototype work.
