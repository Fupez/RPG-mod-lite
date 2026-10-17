# Ability presentation

The server announces accepted startup, cancellation, release, movement, resolved projectile segments and real impacts. `visual_effect` is registered only in the S2C registry. No particle fact can request damage, movement, cooldowns or resources. All existing gameplay queries, movement checks, target limits and timing remain in the ability kits/runtime.

`ClientVisualEffects.register()` receives these facts and feeds `VisualEffectQueue`. Native particles provide the sprites; the client schedules short rings, arcs, spirals and traces. There are no new textures, particles, world entities, lights, camera shakes or screen overlays.

| Ability | Startup and release | Travel, impact and residue |
| --- | --- | --- |
| Heavy Strike | Copper ground cue and gold gather; three sweep samples across the 110° cone | Ground arc reaches the actual 3.5-block boundary; gold hit sparks and fading copper dust |
| Charge | Copper brace cue | Two narrow ground traces between actual endpoints, sparse local material debris and two gold landing rings; hit sparks follow actual damage |
| Ground Slam | Copper four-block perimeter and inward gather during the configured windup | Five timed expanding rings reach exactly four blocks, another rim pulse, ground material fragments and a sparse late residue ring |
| Firebolt | Violet rune, cyan gather and a small forward release spark | Each rendered segment ends at the real server collision endpoint; fullbright native star core, violet/cyan trail, compact impact sparks and two short ember pulses |
| Arcane Burst | Violet four-block rune and cyan gather | Expanding violet/cyan rings reach exactly four blocks; late perimeter residue; successful heal adds a compact central response |
| Blink | Violet startup rune | Ground link between actual departure/arrival, small departure/arrival rings and a short violet arrival spiral |
| Dash | Teal startup mark | Two narrow teal/steel traces between actual endpoints, followed by two small landing rings |
| Backstab | Teal startup cue | Three compact steel slash samples and target sparks on actual damage; native critical/strong sound still distinguishes the rear hit |
| Fan of Knives | Teal gather and restrained steel release | All five knives use their real server segments, compact teal/steel streaks and real entity/block impact sparks |

Startup length uses the accepted definition's actual `windupTicks`. A server cancellation removes the matching actor's startup immediately. Long configured windups emit only during the first and final eight ticks. Release sequences last at most 15 ticks, impact sequences nine ticks and level-up sequences 25 ticks. Existing vanilla particle lifetimes provide the final fade after their last scheduled emission.

A level increase announces one gold ground ring/ascending double spiral/crown sequence to nearby observers, even if one XP award gains multiple levels. The native level-up sound plays once at the player. This replaces the old local progress-snapshot particle burst.

## Explicit limits

- At most 64 pending timelines, 192 admitted particles per fact and 96 particles per client tick globally. Idle queues return immediately.
- Vanilla All / Decreased / Minimal select global budgets of 96 / 48 / 16. Minimal emits only core marks. Core admission runs before accents and debris; only admitted core particles bypass vanilla random thinning, keeping the sparse range cue readable under vanilla's minimal particle setting.
- Ring density starts at 24 / 14 / 6 points. Core dust uses scale 1.45 for a readable rim; secondary dust uses 0.62. Beyond 16 blocks it halves, beyond 28 blocks it drops to a third, and four points remain the minimum. Facts and individual particles beyond 40 blocks are culled.
- Airborne particles within 1.3 blocks of the active camera are discarded. Ground marks remain visible underfoot. The level-up crown and impact residue use the airborne safety check.
- New core events can displace old travel decoration when the timeline cap is reached. Changing worlds, replacing the local player or disconnecting clears the queue. Startup actors are checked by entity object identity to handle removal and reused IDs.
- Payload constructors reject non-finite/out-of-world coordinates, segments longer than 64 blocks, radii outside 0–4 and durations outside 0–72,000 ticks. The server drops invalid cosmetic geometry without aborting the gameplay action. A serialized fact remains below 96 bytes in the codec tests.
- A projectile advance sends one small segment fact rather than several server particle packets; client visuals never extrapolate through a wall. Fan of Knives has at most five segments per server tick, each bounded by its existing eight-tick projectile lifetime.

Layered sounds use only native Minecraft events. Quiet copper, amethyst or iron startup cues sit below the ability's existing release sound; material hits, amethyst resonance or light iron accents distinguish the class without thunder, explosions or repeating ambient loops.

## Verification

`VisualEffectPayloadTest`, `VisualEffectQueueTest` and `AbilityVisualsTest` cover codec round trips and malformed data, actual four-block rings and the 3.5-block/110° melee cone, authoritative projectile endpoints, finite camera-safe geometry across every ability and quality, a 20,000-event flood, priority under minimal settings, material/ember layers, distance culling, world/actor replacement, cancellation, long-windup lifecycle and harmless server boundary drops.

Run with Java 21 and the repository's `.gradle/cloud-env.sh`:

```sh
./gradlew --offline compileJava compileClientJava test \
  --tests '*VisualEffectPayloadTest' --tests '*VisualEffectQueueTest' \
  --tests '*AbilityVisualsTest' --max-workers=2
```

All 16 focused FX tests passed as part of the 75-test `check`/`build` run. The native server suite also passed all 17 checks. Real Minecraft clients were launched against a dedicated Fabric server: all nine abilities, observer-visible travel/impact layers, class confirmation, stat allocation, low health and level-up presentation were inspected. The character and guide screens were checked at minimum GUI size, 16:9 and ultrawide, including the compact guide's scrolling cast details.

Native sound balance, visibility across varied terrain/night scenes and crowded-combat FPS still need local validation. The cloud display uses software OpenGL and provides no audible output. Quality-mode geometry and budgets are covered automatically; compare All/Decreased/Minimal in a normal local client as well.
