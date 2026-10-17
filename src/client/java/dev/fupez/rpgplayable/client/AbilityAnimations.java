package dev.fupez.rpgplayable.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** Presentation timelines keyed by both world and entity identity, never just an entity ID. */
public final class AbilityAnimations<W, E> {
    public static final int MAX_TRACKED = 256;
    private final Map<Integer, Timeline<E>> timelines = new LinkedHashMap<>();
    private W world;

    public record Pose(int style, float weight, float strike) {
        public static final Pose NONE = new Pose(0, 0, 0);

        /** An additive keyframe channel, smoothly entering and leaving the vanilla pose. */
        public float offset(float anticipation, float impact) {
            return (anticipation + (impact - anticipation) * strike) * weight;
        }
    }

    public void receive(W world, int id, E entity, int style, int windup, int recovery) {
        if (id < 0 || style < 0 || style > 3 || windup < 0 || recovery < 0
                || (long) windup + recovery > 72000 || (windup == 0) != (recovery == 0))
            throw new IllegalArgumentException("Invalid ability animation");
        bind(world);
        if (world == null || entity == null) return;
        if (windup == 0) {
            var previous = timelines.get(id);
            if (previous != null && previous.entity == entity) timelines.remove(id);
            return;
        }
        // A fresh cast supersedes the old one and becomes the newest eviction candidate.
        timelines.remove(id);
        if (timelines.size() == MAX_TRACKED) timelines.remove(timelines.keySet().iterator().next());
        timelines.put(id, new Timeline<>(entity, style, windup, recovery));
    }

    public void tick(W world, IntFunction<E> resolve, Predicate<E> alive) {
        bind(world);
        timelines.entrySet().removeIf(entry -> {
            var timeline = entry.getValue();
            return resolve.apply(entry.getKey()) != timeline.entity || !alive.test(timeline.entity)
                    || ++timeline.elapsed >= timeline.windup + timeline.recovery;
        });
    }

    public Pose sample(W world, int id, E entity, float delta) {
        var timeline = timelines.get(id);
        if (world == null || world != this.world || timeline == null || timeline.entity != entity) return Pose.NONE;
        float elapsed = timeline.elapsed + (Float.isNaN(delta) ? 0 : Math.max(0, Math.min(1, delta)));
        if (elapsed >= timeline.windup + timeline.recovery) return Pose.NONE;
        // Reach anticipation early, drive through the active frame at exactly windup,
        // then settle from the impact pose instead of replaying the windup backwards.
        float weight = elapsed < timeline.windup ? smooth(elapsed / (timeline.windup * .65f))
                : 1 - smooth((elapsed - timeline.windup) / timeline.recovery);
        float strike = smooth((elapsed - timeline.windup * .72f) / (timeline.windup * .28f));
        return new Pose(timeline.style, weight, strike);
    }

    public void clear() {
        timelines.clear();
        world = null;
    }

    int trackedCount() { return timelines.size(); }

    private void bind(W world) {
        if (world != this.world) {
            timelines.clear();
            this.world = world;
        }
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    private static final class Timeline<E> {
        final E entity;
        final int style, windup, recovery;
        int elapsed;

        Timeline(E entity, int style, int windup, int recovery) {
            this.entity = entity; this.style = style; this.windup = windup; this.recovery = recovery;
        }
    }
}
