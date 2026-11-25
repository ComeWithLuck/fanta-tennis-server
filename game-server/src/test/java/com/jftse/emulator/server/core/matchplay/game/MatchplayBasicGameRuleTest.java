package com.jftse.emulator.server.core.matchplay.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** notes-v402: the room rule's match end (native 4a3da0 on [7b1fbc]). */
class MatchplayBasicGameRuleTest {
    @Test
    void shortMatchEndsAtTwoSets() {
        assertTrue(MatchplayBasicGame.setsWin((byte) 0, 2, 0));
        assertTrue(MatchplayBasicGame.setsWin((byte) 0, 2, 1));
        assertFalse(MatchplayBasicGame.setsWin((byte) 0, 1, 1));
    }

    @Test
    void tieMatchEndsAtTwoNilOrThreeSets() {
        assertTrue(MatchplayBasicGame.setsWin((byte) 1, 2, 0));
        assertFalse(MatchplayBasicGame.setsWin((byte) 1, 2, 1));
        assertFalse(MatchplayBasicGame.setsWin((byte) 1, 2, 2));
        assertTrue(MatchplayBasicGame.setsWin((byte) 1, 3, 1));
        assertTrue(MatchplayBasicGame.setsWin((byte) 1, 3, 2));
        assertFalse(MatchplayBasicGame.setsWin((byte) 1, 1, 2));
    }
}
