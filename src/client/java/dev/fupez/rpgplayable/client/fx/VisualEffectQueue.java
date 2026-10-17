package dev.fupez.rpgplayable.client.fx;

import dev.fupez.rpgplayable.abilities.AbilityId;
import dev.fupez.rpgplayable.network.VisualEffectPayload;
import dev.fupez.rpgplayable.network.VisualEffectPayload.Kind;
import java.util.*;
import java.util.function.*;
import net.minecraft.util.math.Vec3d;

/** World-scoped cosmetic timelines. Pure geometry and admission rules are shared with the tests. */
public final class VisualEffectQueue<W, A> {
    public static final int MAX_ACTIVE = 64;
    public static final int MAX_PER_EFFECT = 192;
    public enum Quality {
        ALL(96), DECREASED(48), MINIMAL(16);
        public final int budget;
        Quality(int budget) { this.budget = budget; }
    }
    public enum Sprite { DUST, SPARK, GLOW, EMBER, CRIT, DEBRIS }
    public record Particle(Vec3d position, Vec3d velocity, int color, float scale, Sprite sprite,
                           int importance, boolean ground) { }
    private final List<Entry<A>> active = new ArrayList<>();
    private W world;
    private static final int COPPER = 0xd69055, GOLD = 0xffd36a, VIOLET = 0xa779ff, CYAN = 0x7aeeff,
            TEAL = 0x46cdbb, STEEL = 0xc4dce5;
    private static final class Entry<A> {
        final VisualEffectPayload payload;
        final A actor;
        int age, emitted;
        Entry(VisualEffectPayload payload, A actor) { this.payload = payload; this.actor = actor; }
    }
    public void clear() { active.clear(); world = null; }
    public int activeCount() { return active.size(); }
    public void bind(W nextWorld) {
        if (world != nextWorld) { active.clear(); world = nextWorld; }
    }
    public void receive(W nextWorld, A actor, VisualEffectPayload payload) {
        bind(nextWorld);
        if (world == null) return;
        if (payload.kind() == Kind.START || payload.kind() == Kind.CANCEL || payload.kind() == Kind.RELEASE)
            active.removeIf(e -> e.payload.kind() == Kind.START && e.payload.entityId() == payload.entityId() && e.actor == actor);
        if (payload.kind() == Kind.CANCEL || (payload.kind() == Kind.START && actor == null)) return;
        if (active.size() == MAX_ACTIVE) {
            // Prefer a fresh core cue to old decorative travel; memory never grows with packet rate.
            int victim = -1;
            for (int i = 0; i < active.size(); i++) if (active.get(i).payload.kind() == Kind.TRAVEL) { victim = i; break; }
            if (victim < 0 && payload.kind() == Kind.TRAVEL) return;
            active.remove(victim < 0 ? 0 : victim);
        }
        active.add(new Entry<>(payload, actor));
    }
    public List<Particle> tick(W nextWorld, Vec3d camera, Quality quality,
                               IntFunction<A> actors, Function<A, Vec3d> feet) {
        bind(nextWorld);
        if (world == null || active.isEmpty() || camera == null || !finite(camera)) return List.of();
        var candidates = new ArrayList<Candidate<A>>(MAX_ACTIVE * 32);
        var iterator = active.iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var p = entry.payload;
            Vec3d origin = p.origin();
            if (p.kind() == Kind.START) {
                if (entry.actor == null || actors.apply(p.entityId()) != entry.actor) { iterator.remove(); continue; }
                origin = feet.apply(entry.actor);
                if (origin == null || !finite(origin)) { iterator.remove(); continue; }
            }
            double distance = Math.min(origin.squaredDistanceTo(camera), p.end().squaredDistanceTo(camera));
            if (distance <= 40 * 40 && entry.emitted < MAX_PER_EFFECT) {
                int density = quality == Quality.ALL ? 24 : quality == Quality.DECREASED ? 14 : 6;
                if (distance > 28 * 28) density = Math.max(4, density / 3);
                else if (distance > 16 * 16) density = Math.max(4, density / 2);
                var particles = new ArrayList<Particle>(48);
                plan(entry, origin, density, particles);
                for (var particle : particles) {
                    // Ground marks remain readable underfoot; airborne sprites never cover the eye.
                    if (finite(particle.position()) && finite(particle.velocity())
                            && (particle.ground() || particle.position().squaredDistanceTo(camera) >= 1.3 * 1.3)
                            && particle.position().squaredDistanceTo(camera) <= 40 * 40
                            && (quality != Quality.MINIMAL || particle.importance() == 0))
                        candidates.add(new Candidate<>(entry, particle, distance));
                }
            }
            if (++entry.age >= life(p)) iterator.remove();
        }
        candidates.sort(Comparator.<Candidate<A>>comparingInt(c -> c.particle.importance())
                .thenComparingDouble(c -> c.distance));
        var result = new ArrayList<Particle>(quality.budget);
        for (var candidate : candidates) {
            if (result.size() >= quality.budget) break;
            if (candidate.entry.emitted >= MAX_PER_EFFECT) continue;
            candidate.entry.emitted++;
            result.add(candidate.particle);
        }
        return List.copyOf(result);
    }
    private record Candidate<A>(Entry<A> entry, Particle particle, double distance) { }
    private static int life(VisualEffectPayload p) {
        return switch (p.kind()) { case START -> p.duration(); case CANCEL -> 0; case TRAVEL -> 1;
            case HIT, IMPACT -> 9; case LEVEL_UP -> 25; case RELEASE -> 15; };
    }
    private static boolean finite(Vec3d p) { return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static <A> void plan(Entry<A> e, Vec3d origin, int n, List<Particle> out) {
        var p = e.payload;
        var id = AbilityId.values()[p.ability()];
        int primary = p.ability() < 3 ? COPPER : p.ability() < 6 ? VIOLET : TEAL;
        int secondary = p.ability() < 3 ? GOLD : p.ability() < 6 ? CYAN : STEEL;
        int age = e.age;
        double rotation = (p.seed() & 255) * Math.PI / 128;
        Vec3d forward = p.end().subtract(p.origin()).multiply(1, 0, 1).normalize();
        double facing = forward.lengthSquared() < 1e-8 ? 0 : Math.atan2(forward.z, forward.x);
        switch (p.kind()) {
            case START -> {
                // Even a server-configured long windup has only two short gathers, at its start and end.
                if (age == 0) ring(out, origin, p.radius() > 0 ? p.radius() : .75, n, primary, 0, 0, .06);
                if ((age < 8 || age >= p.duration() - 8) && age % 2 == 0) {
                    double t = (age + 1.0) / p.duration();
                    double r = 1.8 - .4 * t;
                    for (int i = 0; i < 3; i++) {
                        double a = rotation + age * .55 + i * Math.PI * 2 / 3;
                        dot(out, origin.add(Math.cos(a) * r, .35 + .5 * t, Math.sin(a) * r),
                                new Vec3d(-Math.cos(a) * .025, .018, -Math.sin(a) * .025), secondary, 1, false, Sprite.DUST);
                    }
                }
            }
            case TRAVEL -> {
                // Every segment ends at the authoritative collision query. No predicted particles pass a wall.
                for (int i = 1; i <= (id == AbilityId.FIREBOLT ? 4 : 3); i++) {
                    double t = i / (id == AbilityId.FIREBOLT ? 4.0 : 3.0);
                    var point = p.origin().lerp(p.end(), t);
                    dot(out, point, Vec3d.ZERO, i % 2 == 0 ? secondary : primary,
                            i >= 3 ? 0 : 1, false, Sprite.DUST);
                    if (id == AbilityId.FIREBOLT && i == 4)
                        dot(out, point, new Vec3d(0, .006, 0), secondary, 0, false, Sprite.GLOW);
                }
            }
            case HIT, IMPACT -> {
                if (age == 0 || age == 2) {
                    int rays = age == 0 ? Math.max(4, n / 2) : Math.max(3, n / 3);
                    for (int i = 0; i < rays; i++) {
                        double a = rotation + i * Math.PI * 2 / rays;
                        double r = age == 0 ? .08 : .28;
                        dot(out, origin.add(Math.cos(a) * r, (i % 3 - 1) * .1, Math.sin(a) * r),
                                new Vec3d(Math.cos(a) * .055, .025 + i % 3 * .012, Math.sin(a) * .055),
                                secondary, age == 0 ? 0 : 1, false, age == 0 ? Sprite.SPARK : Sprite.DUST);
                    }
                }
                if (id == AbilityId.FIREBOLT && (age == 3 || age == 6)) {
                    for (int i = 0; i < 3; i++) {
                        double a = rotation + i * Math.PI * 2 / 3;
                        dot(out, origin.add(Math.cos(a) * .14, age * .025, Math.sin(a) * .14),
                                new Vec3d(0, .012, 0), CYAN, 2, false, Sprite.EMBER);
                    }
                }
                if (age == 5) ringAir(out, origin.add(0, -.15, 0), .35, Math.max(4, n / 2), primary, 2, rotation, .02);
            }
            case LEVEL_UP -> {
                if (age == 0 || age == 6) ring(out, origin, 1.1, n, GOLD, 0, rotation, .08);
                if (age % 3 == 0 && age < 22) {
                    double a = rotation + age * .5;
                    for (int i = 0; i < 2; i++) {
                        double angle = a + Math.PI * i;
                        dot(out, origin.add(Math.cos(angle) * .95, .25 + age * .085, Math.sin(angle) * .95),
                                new Vec3d(0, .025, 0), i == 0 ? GOLD : CYAN, 1, false, Sprite.DUST);
                    }
                }
                if (age == 15) ringAir(out, origin.add(0, 2.25, 0), .62, Math.max(6, n / 2), GOLD, 0, rotation, 0);
            }
            case RELEASE -> {
                switch (id) {
                    case HEAVY_STRIKE, BACKSTAB -> {
                        if (age <= 2) {
                            double radius = id == AbilityId.HEAVY_STRIKE ? p.radius() : 2.5;
                            double half = Math.toRadians(id == AbilityId.HEAVY_STRIKE ? 55 : 35);
                            for (int i = 0; i <= n; i++) {
                                double a = facing - half + 2 * half * i / n;
                                dot(out, origin.add(Math.cos(a) * radius, .08, Math.sin(a) * radius),
                                        Vec3d.ZERO, secondary, 0, true, Sprite.DUST);
                                if (i % 3 == 0) dot(out, origin.add(Math.cos(a) * radius * .7, .7 + age * .12, Math.sin(a) * radius * .7),
                                        new Vec3d(Math.cos(a) * .015, .01, Math.sin(a) * .015), primary, 1, false, Sprite.CRIT);
                            }
                        }
                    }
                    case GROUND_SLAM, ARCANE_BURST -> {
                        if (age <= 4 || age == 8) {
                            double radius = age == 8 ? p.radius() : p.radius() * (age + 1) / 5.0;
                            ring(out, origin, radius, n, age % 2 == 0 ? secondary : primary, 0, rotation, .08);
                            if (age == 4) ring(out, origin, radius, Math.max(4, n / 2), primary, 1, rotation + Math.PI / n, .3);
                        }
                        if (age == 1 || age == 6) {
                            int rays = Math.max(4, n / 3);
                            for (int i = 0; i < rays; i++) {
                                double a = rotation + i * Math.PI * 2 / rays;
                                dot(out, origin.add(Math.cos(a) * 1.5, .2, Math.sin(a) * 1.5),
                                        new Vec3d(Math.cos(a) * .035, .025, Math.sin(a) * .035), primary, 1, true,
                                        id == AbilityId.GROUND_SLAM ? Sprite.DEBRIS : Sprite.DUST);
                            }
                        }
                        if (age == 12) ring(out, origin, p.radius(), Math.max(4, n / 2), primary, 2, rotation, .06);
                    }
                    case CHARGE, DASH, BLINK -> {
                        if (age == 0) {
                            int steps = Math.min(20, Math.max(4, (int) Math.ceil(p.origin().distanceTo(p.end()) * 2)));
                            for (int i = 0; i <= steps; i++) {
                                Vec3d point = p.origin().lerp(p.end(), i / (double) steps);
                                Vec3d side = new Vec3d(-(p.end().z - p.origin().z), 0, p.end().x - p.origin().x).normalize().multiply(.24);
                                dot(out, point.add(side).add(0, .08, 0), Vec3d.ZERO, primary, 0, true, Sprite.DUST);
                                dot(out, point.subtract(side).add(0, .11, 0), Vec3d.ZERO, secondary, 1, true, Sprite.DUST);
                                if (id == AbilityId.CHARGE && i % 3 == 0)
                                    dot(out, point.add(0, .12, 0), new Vec3d(0, .025, 0), primary, 2, true, Sprite.DEBRIS);
                            }
                            ring(out, p.origin(), .6, Math.max(4, n / 2), primary, 1, rotation, .08);
                        }
                        if (age == 1 || age == 5) ring(out, p.end(), age == 1 ? .75 : .95, n, secondary, 0, rotation, .08);
                        if (id == AbilityId.BLINK && (age == 0 || age == 2)) {
                            for (int i = 0; i < Math.max(4, n / 2); i++) {
                                double a = rotation + i * Math.PI * 4 / n;
                                dot(out, p.end().add(Math.cos(a) * .75, .2 + i * .12, Math.sin(a) * .75),
                                        new Vec3d(0, .025, 0), primary, 1, false, Sprite.DUST);
                            }
                        }
                    }
                    case FIREBOLT, FAN_OF_KNIVES -> {
                        if (age == 0) {
                            ring(out, origin, .8, Math.max(4, n / 2), primary, 0, rotation, .08);
                            if (id == AbilityId.FIREBOLT) {
                                var point = origin.add(forward.multiply(1.5)).add(0, 1.1, 0);
                                dot(out, point, forward.multiply(.035), CYAN, 0, false, Sprite.SPARK);
                            }
                        }
                        if (age == 4) ring(out, origin, .65, Math.max(4, n / 2), secondary, 2, rotation, .06);
                    }
                }
            }
            case CANCEL -> { }
        }
    }
    private static void ring(List<Particle> out, Vec3d center, double radius, int n, int color,
                             int importance, double rotation, double y) {
        ring(out, center, radius, n, color, importance, rotation, y, true);
    }
    private static void ringAir(List<Particle> out, Vec3d center, double radius, int n, int color,
                                int importance, double rotation, double y) {
        ring(out, center, radius, n, color, importance, rotation, y, false);
    }
    private static void ring(List<Particle> out, Vec3d center, double radius, int n, int color,
                             int importance, double rotation, double y, boolean ground) {
        for (int i = 0; i < n; i++) {
            double a = rotation + i * Math.PI * 2 / n;
            dot(out, center.add(Math.cos(a) * radius, y, Math.sin(a) * radius), Vec3d.ZERO,
                    color, importance, ground, Sprite.DUST);
        }
    }
    private static void dot(List<Particle> out, Vec3d position, Vec3d velocity, int color, int importance, boolean ground, Sprite sprite) {
        out.add(new Particle(position, velocity, color, importance == 0 ? 1.45f : .62f, sprite, importance, ground));
    }
}
