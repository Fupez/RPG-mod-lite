package dev.fupez.rpgplayable.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Intent only: a client cannot supply levels, XP, points or damage values. */
public record ProgressActionPayload(int action, long requestId) implements CustomPayload {
    public static final int SELECT_WARRIOR = 0;
    public static final int ALLOCATE_STRENGTH = 1;
    public static final int SELECT_MAGE = 2;
    public static final int SELECT_ROGUE = 3;
    public static final int ALLOCATE_VITALITY = 4;
    public static final int ALLOCATE_AGILITY = 5;
    public static final int ALLOCATE_INTELLIGENCE = 6;
    public static final int ALLOCATE_SPIRIT = 7;
    public static final Id<ProgressActionPayload> ID = new Id<>(Identifier.of("rpgplayable", "progress_action"));
    public static final PacketCodec<RegistryByteBuf, ProgressActionPayload> CODEC = PacketCodec.ofStatic(
            (buffer, value) -> { buffer.writeVarInt(value.action()); buffer.writeVarLong(value.requestId()); },
            buffer -> new ProgressActionPayload(buffer.readVarInt(), buffer.readVarLong()));

    public ProgressActionPayload {
        if (action < SELECT_WARRIOR || action > ALLOCATE_SPIRIT || requestId <= 0)
            throw new IllegalArgumentException("Invalid RPG action");
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
