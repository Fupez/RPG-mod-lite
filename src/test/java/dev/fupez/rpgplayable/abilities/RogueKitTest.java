package dev.fupez.rpgplayable.abilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RogueKitTest {
    @Test
    void rearBonusUsesTargetFacingAcrossAllCardinalDirections() {
        assertTrue(RogueKit.isBehind(0, -2, 0, 0, 0));
        assertTrue(RogueKit.isBehind(2, 0, 0, 0, 90));
        assertTrue(RogueKit.isBehind(0, 2, 0, 0, 180));
        assertTrue(RogueKit.isBehind(-2, 0, 0, 0, 270));
        assertFalse(RogueKit.isBehind(0, 2, 0, 0, 0));
        assertFalse(RogueKit.isBehind(-2, 0, 0, 0, 90));
        assertFalse(RogueKit.isBehind(0, -2, 0, 0, 180));
        assertFalse(RogueKit.isBehind(2, 0, 0, 0, 270));
    }

    @Test
    void sideAttacksAndOverlappingPositionsDoNotGetRearBonus() {
        assertFalse(RogueKit.isBehind(2, 0, 0, 0, 0));
        assertFalse(RogueKit.isBehind(-2, 0, 0, 0, 0));
        assertFalse(RogueKit.isBehind(10, 20, 10, 20, 0));
        assertTrue(RogueKit.isBehind(11, 18, 10, 20, 0));
        assertFalse(RogueKit.isBehind(12, 19, 10, 20, 0));
    }
}
