package dev.fupez.rpgplayable.abilities;

import dev.fupez.rpgplayable.player.PlayerClass;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AbilityCatalogTest {
    @TempDir Path dir;
    @Test void eachClassHasThreeDifferentUsableDefinitions() {
        for (var type : new PlayerClass[]{PlayerClass.WARRIOR, PlayerClass.MAGE, PlayerClass.ROGUE}) {
            var abilities = AbilityCatalog.forClass(type);
            assertEquals(3, abilities.size()); assertEquals(3, abilities.stream().map(AbilityDefinition::id).distinct().count());
            for (int slot = 0; slot < 3; slot++) {
                var a = AbilityCatalog.get(type, slot);
                assertEquals(slot, a.slot()); assertTrue(a.cost() > 0 && a.cooldownTicks() >= a.windupTicks() + a.recoveryTicks());
            }
        }
        assertNull(AbilityCatalog.get(PlayerClass.UNSELECTED, 0));
    }
    @Test void serverTuningIsPreservedAndInvalidPublicationIsAtomic() throws Exception {
        var file = dir.resolve("abilities.properties");
        try {
            AbilityCatalog.load(file); assertTrue(Files.readString(file).contains("firebolt.damage=8.0"));
            Files.writeString(file, "firebolt.cost=10\nfirebolt.damage=12\n");
            var saved = Files.readString(file); AbilityCatalog.load(file);
            assertEquals(10, AbilityCatalog.get(PlayerClass.MAGE, 0).cost()); assertEquals(12, AbilityCatalog.damage(AbilityId.FIREBOLT));
            assertEquals(saved, Files.readString(file));
            Files.writeString(file, "firebolt.cost=-1\n");
            assertThrows(IllegalArgumentException.class, () -> AbilityCatalog.load(file));
            assertEquals(10, AbilityCatalog.get(PlayerClass.MAGE, 0).cost());
        } finally { AbilityCatalog.reset(); }
    }
}
