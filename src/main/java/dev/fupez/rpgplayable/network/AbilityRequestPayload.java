package dev.fupez.rpgplayable.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AbilityRequestPayload(int slot, long requestId) implements CustomPayload {
    public static final Id<AbilityRequestPayload> ID = new Id<>(Identifier.of("rpgplayable", "ability_request"));
    public static final PacketCodec<RegistryByteBuf, AbilityRequestPayload> CODEC = PacketCodec.ofStatic(
            (b, v) -> { b.writeVarInt(v.slot); b.writeVarLong(v.requestId); },
            b -> new AbilityRequestPayload(b.readVarInt(), b.readVarLong()));
    public AbilityRequestPayload {
        if (slot < 0 || slot > 2 || requestId <= 0) throw new IllegalArgumentException("Invalid ability request");
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
