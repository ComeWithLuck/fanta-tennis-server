package com.jftse.emulator.server.core.life.room;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** notes-v404: the room ball falls back to the Tennis Ball without an owned BALL row; a missing session / room is a no-op. */
class RoomBallUseTest {
    @Test
    void tennisBallOrNoOwnerGivesTheDefault() {
        assertEquals(1, RoomBallUse.validBall(null, 0));
        assertEquals(1, RoomBallUse.validBall(null, 1));
        assertEquals(1, RoomBallUse.validBall(null, 5));
    }

    @Test
    void afterMatchWithoutSessionOrRoomDoesNothing() {
        assertDoesNotThrow(() -> RoomBallUse.afterMatch(null, null));
    }
}
