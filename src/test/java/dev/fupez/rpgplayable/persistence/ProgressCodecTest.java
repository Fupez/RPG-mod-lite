package dev.fupez.rpgplayable.persistence;

import com.mojang.serialization.JsonOps;
import dev.fupez.rpgplayable.player.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressCodecTest {
    @Test void savesRoundTripBothUnselectedAndInvestedStates() {
        for (var p : new PlayerProgress[]{PlayerProgress.DEFAULT,
                new PlayerProgress(PlayerClass.WARRIOR, 4, 23, 1, 2)}) {
            var json = ProgressCodec.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow();
            assertEquals(p, ProgressCodec.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
    }

    @Test void futureSchemaUnknownClassAndForgedPointsAreRejected() {
        var p = new PlayerProgress(PlayerClass.WARRIOR, 2, 0, 1, 0);
        var json = ProgressCodec.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow().getAsJsonObject();
        var future = json.deepCopy(); future.addProperty("schema_version", 2);
        assertTrue(ProgressCodec.CODEC.parse(JsonOps.INSTANCE, future).error().isPresent());
        var unknown = json.deepCopy(); unknown.addProperty("class", "unknown");
        assertTrue(ProgressCodec.CODEC.parse(JsonOps.INSTANCE, unknown).error().isPresent());
        var forged = json.deepCopy(); forged.addProperty("unspent_points", 99);
        assertTrue(ProgressCodec.CODEC.parse(JsonOps.INSTANCE, forged).error().isPresent());
    }
}
