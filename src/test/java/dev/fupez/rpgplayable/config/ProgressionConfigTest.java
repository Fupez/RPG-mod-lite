package dev.fupez.rpgplayable.config;

import dev.fupez.rpgplayable.player.ProgressionRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionConfigTest {
    @TempDir Path temporary;

    @Test void firstLoadWritesDefaultsAndIsRepeatable() throws Exception {
        var p = temporary.resolve("config/rpgplayable.properties");
        assertEquals(ProgressionRules.DEFAULT, ProgressionConfig.load(p));
        var written = Files.readString(p);
        assertEquals(ProgressionRules.DEFAULT, ProgressionConfig.load(p));
        assertEquals(written, Files.readString(p));
    }

    @Test void existingTuningIsRespectedAndNeverOverwritten() throws Exception {
        var p = temporary.resolve("rpg.properties");
        var input = "baseXp=20\nxpStep=10\nkillXp=5\nstrengthDamage=0.75\n";
        Files.writeString(p, input);
        assertEquals(new ProgressionRules(20, 10, 5, 0.75), ProgressionConfig.load(p));
        assertEquals(input, Files.readString(p));
    }

    @Test void everyStatCoefficientCanBeTunedWithoutOverwritingLegacyFiles() throws Exception {
        var p = temporary.resolve("rpg.properties");
        String text = "healthPerVitality=2.25\nmanaPerIntelligence=4\nmanaRegenPerSpirit=0.2\n";
        Files.writeString(p, text);
        var rules = ProgressionConfig.load(p);
        var player = new dev.fupez.rpgplayable.player.PlayerProgress(dev.fupez.rpgplayable.player.PlayerClass.MAGE,
                4, 0, 0, 0, 1, 0, 1, 1);
        var stats = dev.fupez.rpgplayable.stats.DerivedStats.calculate(player, rules);
        assertEquals(2.25, stats.maxHealthBonus()); assertEquals(144, stats.maxMana()); assertEquals(4.2, stats.manaRegen());
        assertEquals(text, Files.readString(p));
        for (String bad : new String[]{"healthPerVitality=3", "manaPerIntelligence=NaN", "supportPerSpirit=-1"}) {
            Files.writeString(p, bad);
            assertThrows(IllegalArgumentException.class, () -> ProgressionConfig.load(p));
        }
    }
    @Test void malformedAndInvalidTuningFailsWithoutReplacingFile() throws Exception {
        var p = temporary.resolve("rpg.properties");
        for (var input : new String[]{"killXp=nope", "baseXp=0", "strengthDamage=NaN"}) {
            Files.writeString(p, input);
            assertThrows(IllegalArgumentException.class, () -> ProgressionConfig.load(p));
            assertEquals(input, Files.readString(p));
        }
    }
}
