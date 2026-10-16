package dev.fupez.rpgplayable.network;

import dev.fupez.rpgplayable.player.PlayerClass;
import dev.fupez.rpgplayable.player.PlayerProgress;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.Objects;

public record ProgressSnapshotPayload(PlayerProgress progress, long nextLevelXp, double strengthBonus,
                                      long requestId, boolean accepted) implements CustomPayload {
    public static final Id<ProgressSnapshotPayload> ID = new Id<>(Identifier.of("rpgplayable", "progress_snapshot"));
    public static final PacketCodec<RegistryByteBuf, ProgressSnapshotPayload> CODEC = PacketCodec.ofStatic(
            ProgressSnapshotPayload::write, ProgressSnapshotPayload::read);

    public ProgressSnapshotPayload {
        Objects.requireNonNull(progress, "progress");
        if (requestId < 0 || !Double.isFinite(strengthBonus) || strengthBonus < 0
                || (progress.level() == PlayerProgress.MAX_LEVEL ? nextLevelXp != 0
                    : nextLevelXp <= progress.xp()))
            throw new IllegalArgumentException("Invalid RPG snapshot");
    }
    private static void write(RegistryByteBuf b, ProgressSnapshotPayload p) {
        b.writeString(p.progress().playerClass().id(), 16);
        b.writeVarInt(p.progress().level()); b.writeVarLong(p.progress().xp());
        b.writeVarInt(p.progress().unspentPoints()); b.writeVarInt(p.progress().strength());
        b.writeVarLong(p.nextLevelXp()); b.writeDouble(p.strengthBonus());
        b.writeVarLong(p.requestId()); b.writeBoolean(p.accepted());
    }
    private static ProgressSnapshotPayload read(RegistryByteBuf b) {
        var state = new PlayerProgress(PlayerClass.fromId(b.readString(16)), b.readVarInt(), b.readVarLong(), b.readVarInt(), b.readVarInt());
        return new ProgressSnapshotPayload(state, b.readVarLong(), b.readDouble(), b.readVarLong(), b.readBoolean());
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
