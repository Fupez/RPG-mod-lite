package dev.fupez.rpgplayable.network;

import dev.fupez.rpgplayable.abilities.AbilityId;
import java.util.Objects;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** S2C cosmetic facts only. Geometry describes an already accepted cast or resolved server segment. */
public record VisualEffectPayload(Kind kind, int ability, int entityId, Vec3d origin, Vec3d end,
                                 float radius, int duration, long seed) implements CustomPayload {
    public enum Kind { START, CANCEL, RELEASE, TRAVEL, IMPACT, HIT, LEVEL_UP }
    public static final int MAX_DURATION = 72000;
    public static final Id<VisualEffectPayload> ID = new Id<>(Identifier.of("rpgplayable", "visual_effect"));
    public static final PacketCodec<RegistryByteBuf, VisualEffectPayload> CODEC = PacketCodec.ofStatic(
            (b, v) -> {
                b.writeVarInt(v.kind.ordinal()); b.writeVarInt(v.ability); b.writeVarInt(v.entityId);
                writePoint(b, v.origin); writePoint(b, v.end);
                b.writeFloat(v.radius); b.writeVarInt(v.duration); b.writeLong(v.seed);
            }, b -> new VisualEffectPayload(readKind(b.readVarInt()), b.readVarInt(), b.readVarInt(),
                    readPoint(b), readPoint(b), b.readFloat(), b.readVarInt(), b.readLong()));
    public VisualEffectPayload {
        Objects.requireNonNull(kind); Objects.requireNonNull(origin); Objects.requireNonNull(end);
        if (ability < 0 || ability >= AbilityId.values().length || entityId < 0
                || !validPoint(origin) || !validPoint(end) || origin.squaredDistanceTo(end) > 64 * 64
                || !Float.isFinite(radius) || radius < 0 || radius > 4
                || duration < 0 || duration > MAX_DURATION || (kind == Kind.START && duration == 0))
            throw new IllegalArgumentException("Invalid cosmetic effect geometry");
    }
    private static boolean validPoint(Vec3d p) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
                && Math.abs(p.x) <= 30_000_000 && Math.abs(p.y) <= 30_000_000 && Math.abs(p.z) <= 30_000_000;
    }
    private static Kind readKind(int ordinal) {
        if (ordinal < 0 || ordinal >= Kind.values().length) throw new IllegalArgumentException("Invalid effect kind");
        return Kind.values()[ordinal];
    }
    private static Vec3d readPoint(RegistryByteBuf b) { return new Vec3d(b.readDouble(), b.readDouble(), b.readDouble()); }
    private static void writePoint(RegistryByteBuf b, Vec3d p) { b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
