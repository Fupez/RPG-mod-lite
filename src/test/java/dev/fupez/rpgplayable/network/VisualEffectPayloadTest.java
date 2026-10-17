package dev.fupez.rpgplayable.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisualEffectPayloadTest {
    private VisualEffectPayload effect(VisualEffectPayload.Kind kind, Vec3d from, Vec3d to, float radius, int ticks) {
        return new VisualEffectPayload(kind, 8, 231, from, to, radius, ticks, Long.MIN_VALUE);
    }
    @Test void everyFactRoundTripsWithoutRegistryOrClientState() {
        for (var kind : VisualEffectPayload.Kind.values()) {
            var value = effect(kind, new Vec3d(-22.125, 77.5, 29_999_900),
                    new Vec3d(-20.5, 78, 29_999_901), 4, kind == VisualEffectPayload.Kind.START ? 72000 : 0);
            var buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
            try {
                VisualEffectPayload.CODEC.encode(buffer, value);
                assertTrue(buffer.readableBytes() < 96, "One small fact replaces a cloud of particle packets");
                assertEquals(value, VisualEffectPayload.CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
    @Test void malformedWireGeometryAndEnumsFailBeforeScheduling() {
        var valid = effect(VisualEffectPayload.Kind.RELEASE, Vec3d.ZERO, new Vec3d(0, 0, 1), 4, 0);
        var buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            VisualEffectPayload.CODEC.encode(buffer, valid);
            buffer.setByte(0, 127);
            assertThrows(IllegalArgumentException.class, () -> VisualEffectPayload.CODEC.decode(buffer));
            buffer.clear();
            VisualEffectPayload.CODEC.encode(buffer, valid);
            buffer.writerIndex(buffer.writerIndex() - 1);
            assertThrows(IndexOutOfBoundsException.class, () -> VisualEffectPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void invalidCoordinatesRangesAndDurationsCannotReachTheClient() {
        var kind = VisualEffectPayload.Kind.START;
        assertThrows(IllegalArgumentException.class, () -> effect(kind, new Vec3d(Double.NaN, 0, 0), Vec3d.ZERO, 1, 4));
        assertThrows(IllegalArgumentException.class, () -> effect(kind, Vec3d.ZERO, new Vec3d(0, Double.POSITIVE_INFINITY, 0), 1, 4));
        assertThrows(IllegalArgumentException.class, () -> effect(kind, new Vec3d(30_000_001, 0, 0), new Vec3d(30_000_001, 0, 0), 1, 4));
        assertThrows(IllegalArgumentException.class, () -> effect(kind, Vec3d.ZERO, new Vec3d(65, 0, 0), 1, 4));
        assertThrows(IllegalArgumentException.class, () -> effect(kind, Vec3d.ZERO, Vec3d.ZERO, Float.NaN, 4));
        assertThrows(IllegalArgumentException.class, () -> effect(kind, Vec3d.ZERO, Vec3d.ZERO, 4.01f, 4));
        assertThrows(IllegalArgumentException.class, () -> effect(kind, Vec3d.ZERO, Vec3d.ZERO, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> effect(kind, Vec3d.ZERO, Vec3d.ZERO, 1, 72001));
        assertThrows(IllegalArgumentException.class, () -> new VisualEffectPayload(kind, 9, 0, Vec3d.ZERO, Vec3d.ZERO, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new VisualEffectPayload(kind, 0, -1, Vec3d.ZERO, Vec3d.ZERO, 0, 1, 0));
    }
}
