package dev.fupez.rpgplayable.abilities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourcePoolTest {
    @Test void paymentIsAtomicAndFailedPaymentCannotCreateNegativeResources() {
        var pool = new ResourcePool(20, 30);
        assertEquals(new ResourcePool(5, 30), pool.spend(ResourceType.MANA, 15));
        assertEquals(new ResourcePool(20, 0), pool.spend(ResourceType.STAMINA, 30));
        assertThrows(IllegalArgumentException.class, () -> pool.spend(ResourceType.MANA, 21));
        assertEquals(20, pool.mana()); assertEquals(30, pool.stamina());
        assertFalse(pool.canSpend(ResourceType.STAMINA, -1));
        assertFalse(pool.canSpend(ResourceType.MANA, Double.NaN));
        assertFalse(new ResourcePool(0, 0).canSpend(ResourceType.MANA, 1e-9));
    }
    @Test void regenerationStopsAtCurrentCapsAndClampsWhenCapsDecrease() {
        assertEquals(new ResourcePool(100, 140), new ResourcePool(99, 139).recover(100, 140, 4, 8));
        assertEquals(new ResourcePool(60, 80), new ResourcePool(100, 140).recover(60, 80, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResourcePool(Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> new ResourcePool(1, 1).recover(2, 2, -.1, 1));
    }
}
