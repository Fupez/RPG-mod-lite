package dev.fupez.rpgplayable.config;

import dev.fupez.rpgplayable.ability.CleaveRules;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CleaveConfigTest {
    @TempDir Path dir;
    @Test void defaultsAreCreatedAndCustomTuningIsPreserved() throws Exception {
        var file = dir.resolve("cleave.properties");
        assertEquals(CleaveRules.DEFAULT, CleaveConfig.load(file));
        Files.writeString(file, "damage=9\ncooldownTicks=80\n");
        var before = Files.readString(file);
        assertEquals(9, CleaveConfig.load(file).damage());
        assertEquals(80, CleaveConfig.load(file).cooldownTicks());
        assertEquals(before, Files.readString(file));
    }
    @Test void invalidFileFailsExplicitly() throws Exception {
        var file = dir.resolve("cleave.properties");
        Files.writeString(file, "cooldownTicks=1\n");
        assertThrows(IllegalArgumentException.class, () -> CleaveConfig.load(file));
    }
}
