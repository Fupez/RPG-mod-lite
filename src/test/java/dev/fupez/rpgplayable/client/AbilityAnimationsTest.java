package dev.fupez.rpgplayable.client;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbilityAnimationsTest {
    private final Object world = new Object();
    private final Object player = new Object();
    private final AbilityAnimations<Object, Object> animations = new AbilityAnimations<>();

    @Test void windupReachesImpactOnServerTickAndRecoveryReturnsToVanilla() {
        animations.receive(world, 7, player, 3, 10, 8);
        assertEquals(0, animations.sample(world, 7, player, 0).weight());
        advance(5);
        var windup = animations.sample(world, 7, player, 0);
        assertTrue(windup.weight() > .8f);
        assertEquals(0, windup.strike());
        advance(5);
        var impact = animations.sample(world, 7, player, 0);
        assertEquals(3, impact.style());
        assertEquals(1, impact.strike());
        assertEquals(1, impact.weight());
        advance(4);
        assertEquals(.5f, animations.sample(world, 7, player, 0).weight(), .001f);
        advance(4);
        assertEquals(AbilityAnimations.Pose.NONE, animations.sample(world, 7, player, 0));
        assertEquals(0, animations.trackedCount());
    }

    @Test void interpolationIsSmoothAcrossImpactAndClampsInvalidFrameDeltas() {
        animations.receive(world, 7, player, 0, 10, 8);
        advance(9);
        var before = animations.sample(world, 7, player, .999f);
        var impact = animations.sample(world, 7, player, 1);
        assertEquals(before.weight(), impact.weight(), .001f);
        assertEquals(before.strike(), impact.strike(), .001f);
        assertEquals(animations.sample(world, 7, player, 0), animations.sample(world, 7, player, Float.NaN));
        assertEquals(impact, animations.sample(world, 7, player, Float.POSITIVE_INFINITY));
        assertEquals(animations.sample(world, 7, player, 0), animations.sample(world, 7, player, -5));
    }

    @Test void zeroPacketCancelsOnlyTheMatchingActorImmediately() {
        var other = new Object();
        animations.receive(world, 7, player, 0, 6, 8);
        animations.receive(world, 8, other, 1, 6, 8);
        animations.receive(world, 7, player, 0, 0, 0);
        assertEquals(AbilityAnimations.Pose.NONE, animations.sample(world, 7, player, 1));
        assertTrue(animations.sample(world, 8, other, 1).weight() > 0);
        animations.clear();
        assertEquals(0, animations.trackedCount());
    }

    @Test void reusedEntityIdCannotInheritOrCancelAnotherPlayerTimeline() {
        var replacement = new Object();
        animations.receive(world, 7, player, 0, 6, 8);
        advance(3);
        assertEquals(AbilityAnimations.Pose.NONE, animations.sample(world, 7, replacement, 0));
        animations.receive(world, 7, replacement, 2, 6, 8);
        animations.receive(world, 7, player, 0, 0, 0);
        assertEquals(2, animations.sample(world, 7, replacement, 1).style());
        assertEquals(AbilityAnimations.Pose.NONE, animations.sample(world, 7, player, 0));
    }

    @Test void worldReplacementAndDisconnectDropAllActors() {
        animations.receive(world, 7, player, 1, 6, 8);
        var nextWorld = new Object();
        assertEquals(AbilityAnimations.Pose.NONE, animations.sample(nextWorld, 7, player, 1));
        animations.tick(nextWorld, id -> player, p -> true);
        assertEquals(0, animations.trackedCount());
        animations.receive(nextWorld, 7, player, 1, 6, 8);
        animations.tick(null, id -> player, p -> true);
        assertEquals(0, animations.trackedCount());
        animations.tick(world, id -> player, p -> true);
        assertEquals(AbilityAnimations.Pose.NONE, animations.sample(world, 7, player, 1));
    }

    @Test void deadRemovedAndReplacedEntitiesArePrunedWhileOtherActorsContinue() {
        var alive = new Object();
        var dead = new Object();
        var removed = new Object();
        var replaced = new Object();
        Map<Integer, Object> entities = new HashMap<>();
        entities.put(7, player); entities.put(8, alive); entities.put(9, dead);
        entities.put(10, removed); entities.put(11, replaced);
        entities.forEach((id, entity) -> animations.receive(world, id, entity, 1, 6, 8));
        entities.remove(10); entities.put(11, new Object());
        animations.tick(world, entities::get, entity -> entity != dead && entity != player);
        assertEquals(1, animations.trackedCount());
        assertTrue(animations.sample(world, 8, alive, 0).weight() > 0);
        assertEquals(AbilityAnimations.Pose.NONE, animations.sample(world, 7, player, 1));
    }

    @Test void repeatedStartsRestartTheTimelineAndTrackingHasAFixedBound() {
        for (int id = 0; id < AbilityAnimations.MAX_TRACKED + 10; id++)
            animations.receive(world, id, player, 0, 6, 8);
        assertEquals(AbilityAnimations.MAX_TRACKED, animations.trackedCount());
        assertEquals(AbilityAnimations.Pose.NONE, animations.sample(world, 0, player, 1));
        int id = AbilityAnimations.MAX_TRACKED + 9;
        animations.tick(world, key -> player, entity -> true);
        animations.receive(world, id, player, 2, 6, 8);
        assertEquals(0, animations.sample(world, id, player, 0).weight());
        assertEquals(2, animations.sample(world, id, player, 1).style());
    }

    @Test void invalidDurationsAndStylesCannotCreateUnboundedOrBrokenTimelines() {
        assertThrows(IllegalArgumentException.class, () -> animations.receive(world, 7, player, 4, 6, 8));
        assertThrows(IllegalArgumentException.class, () -> animations.receive(world, 7, player, 0, -1, 8));
        assertThrows(IllegalArgumentException.class, () -> animations.receive(world, 7, player, 0, 0, 8));
        assertThrows(IllegalArgumentException.class, () -> animations.receive(world, 7, player, 0, Integer.MAX_VALUE, 8));
        assertEquals(0, animations.trackedCount());
    }

    private void advance(int ticks) {
        for (int i = 0; i < ticks; i++) animations.tick(world, id -> player, entity -> true);
    }
}
