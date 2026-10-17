package dev.fupez.rpgplayable.client.fx;

import dev.fupez.rpgplayable.abilities.AbilityId;
import dev.fupez.rpgplayable.network.VisualEffectPayload;
import dev.fupez.rpgplayable.network.VisualEffectPayload.Kind;
import java.util.*;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisualEffectQueueTest {
    private final Object world = new Object(), actor = new Object();
    private final VisualEffectQueue<Object, Object> queue = new VisualEffectQueue<>();
    private static final Vec3d EYE = new Vec3d(0, 1.62, 0);
    private VisualEffectPayload effect(Kind kind, AbilityId id, Vec3d from, Vec3d to, int ticks) {
        return new VisualEffectPayload(kind, id.ordinal(), 7, from, to,
                id == AbilityId.HEAVY_STRIKE ? 3.5f : id == AbilityId.GROUND_SLAM || id == AbilityId.ARCANE_BURST ? 4 : 0,
                ticks, 15);
    }
    private void receive(Kind kind, AbilityId id) {
        queue.receive(world, actor, effect(kind, id, Vec3d.ZERO, new Vec3d(0, 0, 1), kind == Kind.START ? 10 : 0));
    }
    private List<VisualEffectQueue.Particle> tick(VisualEffectQueue.Quality quality) {
        return queue.tick(world, EYE, quality, id -> actor, a -> Vec3d.ZERO);
    }
    @Test void slamAndBurstPerimetersReachExactlyTheServerFourBlockRange() {
        for (var id : List.of(AbilityId.GROUND_SLAM, AbilityId.ARCANE_BURST)) {
            queue.clear(); receive(Kind.RELEASE, id);
            for (int i = 0; i < 4; i++) tick(VisualEffectQueue.Quality.ALL);
            var perimeter = tick(VisualEffectQueue.Quality.ALL).stream().filter(p -> p.importance() == 0).toList();
            assertTrue(perimeter.size() >= 6);
            for (var p : perimeter) assertEquals(4, Math.hypot(p.position().x, p.position().z), 1e-8);
        }
    }
    @Test void shockwavesKeepVisibleLateRimResidueWithinTheirPerEffectBudget() {
        for (var id : List.of(AbilityId.GROUND_SLAM, AbilityId.ARCANE_BURST)) {
            queue.clear(); receive(Kind.RELEASE, id);
            int admitted = 0;
            for (int age = 0; age < 15; age++) {
                var particles = tick(VisualEffectQueue.Quality.ALL); admitted += particles.size();
                if (age == 0) assertTrue(particles.stream().filter(p -> p.importance() == 0).allMatch(p -> p.scale() >= 1.3f));
                if (age == 8 || age == 12)
                    assertTrue(particles.stream().anyMatch(p -> p.ground()
                            && Math.abs(Math.hypot(p.position().x, p.position().z) - 4) < .001),
                            "Late ring layers must survive the per-effect particle cap");
            }
            assertTrue(admitted <= VisualEffectQueue.MAX_PER_EFFECT);
            assertEquals(0, queue.activeCount());
        }
    }
    @Test void heavyStrikeGroundArcMatchesTheThreeAndAHalfBlock110DegreeCone() {
        receive(Kind.RELEASE, AbilityId.HEAVY_STRIKE);
        var arc = tick(VisualEffectQueue.Quality.ALL).stream().filter(p -> p.importance() == 0).toList();
        assertFalse(arc.isEmpty());
        boolean left = false, right = false;
        for (var p : arc) {
            assertEquals(3.5, Math.hypot(p.position().x, p.position().z), 1e-8);
            double angle = Math.toDegrees(Math.atan2(p.position().x, p.position().z));
            assertTrue(Math.abs(angle) <= 55.0001);
            left |= Math.abs(angle + 55) < .001; right |= Math.abs(angle - 55) < .001;
        }
        assertTrue(left && right, "The rim includes both actual cone boundaries");
    }
    @Test void projectileSamplesStayOnTheResolvedSegmentAndExpireInOneTick() {
        for (var id : List.of(AbilityId.FIREBOLT, AbilityId.FAN_OF_KNIVES)) {
            var start = new Vec3d(4, 1, 0); var end = new Vec3d(4.3, 1, 0);
            queue.receive(world, actor, effect(Kind.TRAVEL, id, start, end, 0));
            var trail = tick(VisualEffectQueue.Quality.ALL);
            assertFalse(trail.isEmpty());
            for (var p : trail) {
                assertTrue(p.position().x >= start.x && p.position().x <= end.x);
                assertEquals(1, p.position().y); assertEquals(0, p.position().z);
            }
            assertEquals(0, queue.activeCount());
            assertTrue(tick(VisualEffectQueue.Quality.ALL).isEmpty());
        }
    }
    @Test void allAbilitiesAndPhasesKeepFiniteCompactCameraSafeGeometry() {
        for (var quality : VisualEffectQueue.Quality.values()) for (var id : AbilityId.values()) for (var kind : Kind.values()) {
            queue.clear(); receive(kind, id);
            int emitted = 0;
            for (int age = 0; age < 30; age++) {
                var particles = tick(quality); emitted += particles.size();
                assertTrue(particles.size() <= quality.budget);
                for (var p : particles) {
                    var point = p.position(); var velocity = p.velocity();
                    assertTrue(Double.isFinite(point.lengthSquared()) && Double.isFinite(velocity.lengthSquared()));
                    assertTrue(point.squaredDistanceTo(Vec3d.ZERO) < 25);
                    assertTrue(p.ground() || point.squaredDistanceTo(EYE) >= 1.3 * 1.3);
                    if (quality == VisualEffectQueue.Quality.MINIMAL) assertEquals(0, p.importance());
                }
            }
            assertTrue(emitted <= VisualEffectQueue.MAX_PER_EFFECT);
            assertEquals(0, queue.activeCount());
        }
    }
    @Test void minimalModeRetainsCoreRangeCueAndRemovesDecorativeLayers() {
        receive(Kind.START, AbilityId.GROUND_SLAM);
        var cue = tick(VisualEffectQueue.Quality.MINIMAL);
        assertTrue(cue.size() >= 4);
        assertTrue(cue.stream().allMatch(p -> p.importance() == 0 && p.ground()));
        assertTrue(cue.stream().allMatch(p -> Math.abs(Math.hypot(p.position().x, p.position().z) - 4) < .001));
    }
    @Test void floodsStayBoundedAndFreshCoreCuesDisplaceTravelDecoration() {
        var from = new Vec3d(8, 1, 0); var to = new Vec3d(9, 1, 0);
        for (int i = 0; i < 20_000; i++) queue.receive(world, actor, effect(Kind.TRAVEL, AbilityId.FIREBOLT, from, to, 0));
        assertEquals(VisualEffectQueue.MAX_ACTIVE, queue.activeCount());
        receive(Kind.START, AbilityId.ARCANE_BURST);
        var particles = tick(VisualEffectQueue.Quality.MINIMAL);
        assertTrue(particles.size() <= 16);
        assertTrue(particles.stream().anyMatch(p -> p.ground() && Math.abs(Math.hypot(p.position().x, p.position().z) - 4) < .001));
        for (int i = 0; i < 30; i++) tick(VisualEffectQueue.Quality.ALL);
        assertEquals(0, queue.activeCount());
    }
    @Test void cancellationIdentityWorldAndDisconnectRemoveStartupImmediately() {
        receive(Kind.START, AbilityId.FIREBOLT);
        assertEquals(1, queue.activeCount());
        var replacement = new Object();
        queue.receive(world, replacement, effect(Kind.CANCEL, AbilityId.FIREBOLT, Vec3d.ZERO, Vec3d.ZERO, 0));
        assertEquals(1, queue.activeCount(), "Reused ids cannot cancel another actor's gather");
        receive(Kind.CANCEL, AbilityId.FIREBOLT);
        assertEquals(0, queue.activeCount());
        receive(Kind.START, AbilityId.GROUND_SLAM);
        assertTrue(queue.tick(world, EYE, VisualEffectQueue.Quality.ALL, id -> replacement, a -> Vec3d.ZERO).isEmpty());
        assertEquals(0, queue.activeCount());
        receive(Kind.START, AbilityId.ARCANE_BURST);
        assertTrue(queue.tick(new Object(), EYE, VisualEffectQueue.Quality.ALL, id -> actor, a -> Vec3d.ZERO).isEmpty());
        assertEquals(0, queue.activeCount());
        receive(Kind.LEVEL_UP, AbilityId.HEAVY_STRIKE);
        queue.clear();
        assertEquals(0, queue.activeCount());
    }
    @Test void longConfiguredWindupsGatherOnlyAtStartAndEndAndExpireAtTheActualTick() {
        queue.receive(world, actor, effect(Kind.START, AbilityId.ARCANE_BURST, Vec3d.ZERO, new Vec3d(0, 0, 1), 72000));
        int emitted = 0;
        for (int age = 0; age < 72000; age++) {
            var particles = tick(VisualEffectQueue.Quality.ALL); emitted += particles.size();
            if (age >= 8 && age < 71992) assertTrue(particles.isEmpty());
            if (age < 71999) assertEquals(1, queue.activeCount());
        }
        assertTrue(emitted > 16 && emitted < VisualEffectQueue.MAX_PER_EFFECT);
        assertEquals(0, queue.activeCount());
    }
    @Test void fireboltHasAnAuthoritativeGlowingCoreAndShortLivedImpactEmbers() {
        var origin = new Vec3d(4, 1, 0); var end = new Vec3d(4.9, 1, 0);
        queue.receive(world, actor, effect(Kind.TRAVEL, AbilityId.FIREBOLT, origin, end, 0));
        assertTrue(tick(VisualEffectQueue.Quality.ALL).stream().anyMatch(p ->
                p.sprite() == VisualEffectQueue.Sprite.GLOW && p.importance() == 0 && p.position().equals(end)));
        queue.receive(world, actor, effect(Kind.IMPACT, AbilityId.FIREBOLT, end, end, 0));
        int emberTicks = 0;
        for (int age = 0; age < 12; age++) {
            var particles = tick(VisualEffectQueue.Quality.ALL);
            if (particles.stream().anyMatch(p -> p.sprite() == VisualEffectQueue.Sprite.EMBER)) emberTicks++;
        }
        assertEquals(2, emberTicks);
        assertEquals(0, queue.activeCount());
    }
    @Test void warriorDebrisIsSecondaryAndNeverCostsMinimalModeCoreMarks() {
        receive(Kind.RELEASE, AbilityId.GROUND_SLAM);
        tick(VisualEffectQueue.Quality.ALL);
        assertTrue(tick(VisualEffectQueue.Quality.ALL).stream().anyMatch(p -> p.sprite() == VisualEffectQueue.Sprite.DEBRIS));
        queue.clear(); receive(Kind.RELEASE, AbilityId.GROUND_SLAM);
        for (int age = 0; age < 15; age++)
            assertTrue(tick(VisualEffectQueue.Quality.MINIMAL).stream().noneMatch(p -> p.sprite() == VisualEffectQueue.Sprite.DEBRIS));
    }
    @Test void distanceThinsGeometryAndCullsDistantFactsWithoutExtendingLife() {
        receive(Kind.START, AbilityId.GROUND_SLAM);
        var near = tick(VisualEffectQueue.Quality.ALL);
        queue.clear(); receive(Kind.START, AbilityId.GROUND_SLAM);
        var distant = queue.tick(world, new Vec3d(30, 1.62, 0), VisualEffectQueue.Quality.ALL, id -> actor, a -> Vec3d.ZERO);
        assertTrue(distant.size() < near.size());
        queue.clear(); receive(Kind.TRAVEL, AbilityId.FIREBOLT);
        assertTrue(queue.tick(world, new Vec3d(100, 2, 0), VisualEffectQueue.Quality.ALL, id -> actor, a -> Vec3d.ZERO).isEmpty());
        assertEquals(0, queue.activeCount());
    }
}
