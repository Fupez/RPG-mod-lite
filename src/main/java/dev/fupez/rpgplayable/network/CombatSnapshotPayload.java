package dev.fupez.rpgplayable.network;

import java.util.Arrays;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Authoritative resources/cooldowns and a bounded three-slot read model. */
public record CombatSnapshotPayload(double mana, double maxMana, double stamina, double maxStamina,
        int[] cooldowns, int[] cooldownTotals, double[] costs, int activeSlot, int castElapsed,
        int windupTicks, int recoveryTicks, int animation, long requestId, int result) implements CustomPayload {
    public static final int UPDATE = 0, ACCEPTED = 1, NOT_READY = 2, NO_RESOURCE = 3,
            UNAVAILABLE = 4, INTERRUPTED = 5, BLOCKED = 6;
    public static final Id<CombatSnapshotPayload> ID = new Id<>(Identifier.of("rpgplayable", "combat_snapshot"));
    public static final PacketCodec<RegistryByteBuf, CombatSnapshotPayload> CODEC = PacketCodec.ofStatic(
            CombatSnapshotPayload::write, CombatSnapshotPayload::read);
    public CombatSnapshotPayload {
        if (!finite(mana) || !finite(stamina) || !finite(maxMana) || !finite(maxStamina)
                || maxMana > 10000 || maxStamina > 10000 || mana > maxMana || stamina > maxStamina
                || cooldowns == null || cooldownTotals == null || costs == null
                || cooldowns.length != 3 || cooldownTotals.length != 3 || costs.length != 3
                || activeSlot < -1 || activeSlot > 2 || castElapsed < 0 || windupTicks < 0 || recoveryTicks < 0
                || windupTicks + (long) recoveryTicks > 72000 || animation < 0 || animation > 3
                || requestId < 0 || result < UPDATE || result > BLOCKED)
            throw new IllegalArgumentException("Invalid combat snapshot");
        cooldowns = cooldowns.clone(); cooldownTotals = cooldownTotals.clone(); costs = costs.clone();
        for (int i = 0; i < 3; i++) if (cooldownTotals[i] < 0 || cooldownTotals[i] > 72000
                || cooldowns[i] < 0 || cooldowns[i] > cooldownTotals[i] || !finite(costs[i]) || costs[i] > 1000)
            throw new IllegalArgumentException("Invalid ability slot snapshot");
    }
    private static boolean finite(double value) { return Double.isFinite(value) && value >= 0; }
    @Override public int[] cooldowns() { return cooldowns.clone(); }
    @Override public int[] cooldownTotals() { return cooldownTotals.clone(); }
    @Override public double[] costs() { return costs.clone(); }
    private static void write(RegistryByteBuf b, CombatSnapshotPayload v) {
        b.writeDouble(v.mana); b.writeDouble(v.maxMana); b.writeDouble(v.stamina); b.writeDouble(v.maxStamina);
        for (int i = 0; i < 3; i++) { b.writeVarInt(v.cooldowns[i]); b.writeVarInt(v.cooldownTotals[i]); b.writeDouble(v.costs[i]); }
        b.writeVarInt(v.activeSlot); b.writeVarInt(v.castElapsed); b.writeVarInt(v.windupTicks); b.writeVarInt(v.recoveryTicks);
        b.writeVarInt(v.animation); b.writeVarLong(v.requestId); b.writeVarInt(v.result);
    }
    private static CombatSnapshotPayload read(RegistryByteBuf b) {
        double mana = b.readDouble(), maxMana = b.readDouble(), stamina = b.readDouble(), maxStamina = b.readDouble();
        int[] cooldowns = new int[3], totals = new int[3]; double[] costs = new double[3];
        for (int i = 0; i < 3; i++) { cooldowns[i] = b.readVarInt(); totals[i] = b.readVarInt(); costs[i] = b.readDouble(); }
        return new CombatSnapshotPayload(mana, maxMana, stamina, maxStamina, cooldowns, totals, costs,
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readVarInt());
    }
    @Override public boolean equals(Object other) {
        return other instanceof CombatSnapshotPayload v && mana == v.mana && maxMana == v.maxMana && stamina == v.stamina
                && maxStamina == v.maxStamina && Arrays.equals(cooldowns, v.cooldowns) && Arrays.equals(cooldownTotals, v.cooldownTotals)
                && Arrays.equals(costs, v.costs) && activeSlot == v.activeSlot && castElapsed == v.castElapsed
                && windupTicks == v.windupTicks && recoveryTicks == v.recoveryTicks && animation == v.animation
                && requestId == v.requestId && result == v.result;
    }
    @Override public int hashCode() {
        return java.util.Objects.hash(mana, maxMana, stamina, maxStamina, Arrays.hashCode(cooldowns), Arrays.hashCode(cooldownTotals),
                Arrays.hashCode(costs), activeSlot, castElapsed, windupTicks, recoveryTicks, animation, requestId, result);
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
