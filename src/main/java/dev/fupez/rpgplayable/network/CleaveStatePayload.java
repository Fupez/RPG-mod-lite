package dev.fupez.rpgplayable.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Remaining ticks include latency; the client displays them but cannot authorize casts. */
public record CleaveStatePayload(int remaining, int total, int windup, int recovery, int result, boolean interrupted) implements CustomPayload {
    public static final int UPDATE = 0, ACCEPTED = 1, UNAVAILABLE = 2, INTERRUPTED = 3;
    public static final Id<CleaveStatePayload> ID = new Id<>(Identifier.of("rpgplayable", "cleave_state"));
    public static final PacketCodec<RegistryByteBuf, CleaveStatePayload> CODEC = PacketCodec.ofStatic(
            (b, v) -> { b.writeVarInt(v.remaining); b.writeVarInt(v.total); b.writeVarInt(v.windup); b.writeVarInt(v.recovery); b.writeVarInt(v.result); b.writeBoolean(v.interrupted); },
            b -> new CleaveStatePayload(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean()));
    public CleaveStatePayload {
        if (total < 2 || total > 72000 || remaining < 0 || remaining > total || windup < 1 || recovery < 1
                || windup + (long) recovery > total || result < UPDATE || result > INTERRUPTED)
            throw new IllegalArgumentException("Invalid Cleave state");
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
