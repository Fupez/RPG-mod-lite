package dev.fupez.rpgplayable.network;

import dev.fupez.rpgplayable.player.PlayerClass;
import dev.fupez.rpgplayable.player.PlayerProgress;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.math.BigInteger;
import java.util.Objects;

public record ProgressSnapshotPayload(PlayerProgress progress, BigInteger nextLevelXp, double strengthBonus,
                                      long requestId, boolean accepted) implements CustomPayload {
    public static final Id<ProgressSnapshotPayload> ID = new Id<>(Identifier.of("rpgplayable", "progress_snapshot"));
    public static final PacketCodec<RegistryByteBuf, ProgressSnapshotPayload> CODEC = PacketCodec.ofStatic(
            ProgressSnapshotPayload::write, ProgressSnapshotPayload::read);
    // Transport allocation guard, not a gameplay counter cap. Each value can represent over 157,000 decimal digits.
    private static final int MAX_COUNTER_BYTES = 65536;
    public ProgressSnapshotPayload {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(nextLevelXp, "nextLevelXp");
        if (requestId < 0 || !Double.isFinite(strengthBonus) || strengthBonus < 0
                || nextLevelXp.compareTo(progress.xp()) <= 0)
            throw new IllegalArgumentException("Invalid RPG snapshot");
    }
    public ProgressSnapshotPayload(PlayerProgress progress, long nextLevelXp, double strengthBonus, long requestId, boolean accepted) {
        this(progress, BigInteger.valueOf(nextLevelXp), strengthBonus, requestId, accepted);
    }
    private static void write(RegistryByteBuf b, ProgressSnapshotPayload p) {
        b.writeString(p.progress().playerClass().id(), 16);
        writeCounter(b, p.progress().level()); writeCounter(b, p.progress().xp());
        writeCounter(b, p.progress().unspentPoints()); writeCounter(b, p.progress().strength());
        writeCounter(b, p.progress().vitality()); writeCounter(b, p.progress().agility());
        writeCounter(b, p.progress().intelligence()); writeCounter(b, p.progress().spirit());
        writeCounter(b, p.nextLevelXp()); b.writeDouble(p.strengthBonus());
        b.writeVarLong(p.requestId()); b.writeBoolean(p.accepted());
    }
    private static void writeCounter(RegistryByteBuf b, BigInteger counter) {
        var bytes = counter.toByteArray();
        if (bytes.length > MAX_COUNTER_BYTES) throw new IllegalArgumentException("RPG counter exceeds safe packet allocation");
        b.writeByteArray(bytes);
    }
    private static BigInteger readCounter(RegistryByteBuf b) {
        var bytes = b.readByteArray(MAX_COUNTER_BYTES);
        if (bytes.length == 0) throw new IllegalArgumentException("Missing RPG counter");
        return new BigInteger(bytes);
    }
    private static ProgressSnapshotPayload read(RegistryByteBuf b) {
        var state = new PlayerProgress(PlayerClass.fromId(b.readString(16)), readCounter(b), readCounter(b),
                readCounter(b), readCounter(b), readCounter(b), readCounter(b), readCounter(b), readCounter(b));
        return new ProgressSnapshotPayload(state, readCounter(b), b.readDouble(), b.readVarLong(), b.readBoolean());
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
