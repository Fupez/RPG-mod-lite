package dev.fupez.rpgplayable.network;

import dev.fupez.rpgplayable.player.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressPayloadTest {
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
