package dev.fupez.rpgplayable.network;

import dev.fupez.rpgplayable.player.*;
import dev.fupez.rpgplayable.stats.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressPayloadTest {
    @Test void statScreenReceivesServerTuningRatherThanClientDefaults() {
        var progress = new PlayerProgress(PlayerClass.MAGE, 6, 0, 0, 1, 1, 1, 1, 1);
        var custom = new ProgressionRules(100, 50, 25, 1.25,
                new StatTuning(.02, 1.5, .004, .025, .03, 4, .5, .3, .4, .02));
        var effects = DerivedStats.calculate(progress, custom);
        assertNotEquals(DerivedStats.calculate(progress, ProgressionRules.DEFAULT), effects);
        var snapshot = new ProgressSnapshotPayload(progress, BigInteger.valueOf(350), custom.strengthBonus(progress), 11, true, effects);
        var wire = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            ProgressSnapshotPayload.CODEC.encode(wire, snapshot);
            var received = ProgressSnapshotPayload.CODEC.decode(wire);
            assertEquals(effects, received.derived());
            assertEquals(snapshot, received);
            assertFalse(wire.isReadable());
        } finally { wire.release(); }
    }
    @Test void requestsCannotSupplyXpPointsDamageOrInvalidActions() {
        assertThrows(IllegalArgumentException.class, () -> new ProgressActionPayload(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProgressActionPayload(8, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProgressActionPayload(0, 0));
        assertEquals(17, new ProgressActionPayload(ProgressActionPayload.ALLOCATE_STRENGTH, 17).requestId());
    }

    @Test void snapshotsRejectImpossibleDisplayValues() {
        assertThrows(IllegalArgumentException.class, () -> new ProgressSnapshotPayload(PlayerProgress.DEFAULT, 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new ProgressSnapshotPayload(PlayerProgress.DEFAULT, 100, Double.NaN, 0, true));
        var p = new PlayerProgress(PlayerClass.WARRIOR, 2, 20, 1, 0);
        assertThrows(IllegalArgumentException.class, () -> new ProgressSnapshotPayload(p, 20, 0, 0, true));
    }
}
