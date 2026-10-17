package dev.fupez.rpgplayable.abilities;

import dev.fupez.rpgplayable.network.VisualEffectPayload.Kind;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbilityVisualsTest {
    @Test void invalidCosmeticBoundaryOrDirectionDoesNotThrowThroughGameplay() {
        assertNull(AbilityVisuals.safePayload(Kind.RELEASE, AbilityId.CHARGE, 1,
                new Vec3d(30_000_000, 70, 0), new Vec3d(30_000_001, 70, 0), 0, 0, 0));
        assertNull(AbilityVisuals.safePayload(Kind.TRAVEL, AbilityId.FIREBOLT, 1,
                Vec3d.ZERO, new Vec3d(Double.NaN, 0, 0), 0, 0, 0));
        assertNotNull(AbilityVisuals.safePayload(Kind.RELEASE, AbilityId.GROUND_SLAM, 1,
                new Vec3d(29_999_984, 70, 0), new Vec3d(29_999_985, 70, 0), 4, 0, 0));
    }
}
