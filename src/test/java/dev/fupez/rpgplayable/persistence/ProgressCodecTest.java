package dev.fupez.rpgplayable.persistence;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.fupez.rpgplayable.player.*;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressCodecTest {
    @Test void schemaTwoRoundTripsAllClassesFiveStatsAndHugeCounters() {
        for (var type : PlayerClass.values()) {
            var p = type == PlayerClass.UNSELECTED ? PlayerProgress.DEFAULT : new PlayerProgress(type, 6, 23, 0, 1, 1, 1, 1, 1);
            var json = ProgressCodec.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow();
            assertEquals(2, json.getAsJsonObject().get("schema_version").getAsInt());
            assertEquals(p, ProgressCodec.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
        var huge = BigInteger.TEN.pow(1000);
        var p = new PlayerProgress(PlayerClass.MAGE, huge.add(BigInteger.ONE), huge, huge, BigInteger.ZERO,
                BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);
        assertEquals(p, ProgressCodec.CODEC.parse(JsonOps.INSTANCE, ProgressCodec.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow()).getOrThrow());
    }
    @Test void legacyLevelOneHundredMigratesAndCanContinueLeveling() {
        var legacy = JsonParser.parseString("{\"schema_version\":1,\"class\":\"warrior\",\"level\":100,\"xp\":0,\"unspent_points\":98,\"strength\":1}");
        var p = ProgressCodec.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(BigInteger.valueOf(100), p.level()); assertEquals(BigInteger.ONE, p.strength());
        assertEquals(BigInteger.ZERO, p.spirit());
        assertEquals(BigInteger.valueOf(101), Progression.addXp(p, 5050, ProgressionRules.DEFAULT).level());
    }
    @Test void futureSchemaUnknownClassAndForgedPointsAreRejected() {
        var p = new PlayerProgress(PlayerClass.WARRIOR, 2, 0, 1, 0);
        var json = ProgressCodec.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow().getAsJsonObject();
        var future = json.deepCopy(); future.addProperty("schema_version", 3);
        assertTrue(ProgressCodec.CODEC.parse(JsonOps.INSTANCE, future).error().isPresent());
        var unknown = json.deepCopy(); unknown.addProperty("class", "unknown");
        assertTrue(ProgressCodec.CODEC.parse(JsonOps.INSTANCE, unknown).error().isPresent());
        var forged = json.deepCopy(); forged.addProperty("unspent_points", "99");
        assertTrue(ProgressCodec.CODEC.parse(JsonOps.INSTANCE, forged).error().isPresent());
    }
}
