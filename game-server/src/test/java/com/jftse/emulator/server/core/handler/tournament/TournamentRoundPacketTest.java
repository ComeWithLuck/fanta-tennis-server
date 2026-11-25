package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.packets.tournament.S2CTournamentRoundPacket;
import com.jftse.server.core.protocol.Packet;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** notes-v191: the round packets as the client readers 5cb350 (0x2413), 5cb430 (0x2415) and 5cb490 (0x2414) take them. */
class TournamentRoundPacketTest {
    private static ByteBuffer body(Packet packet) {
        byte[] raw = packet.getRawPacket();
        return ByteBuffer.wrap(Arrays.copyOfRange(raw, 8, raw.length)).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Test void layouts() {
        ByteBuffer i = body(S2CTournamentRoundPacket.info(7, true, 10));
        assertEquals(7, i.getInt()); i.get(); i.get();
        for (int k = 0; k < 4; k++) assertEquals(0, i.getInt());
        assertEquals(1, i.get(), "+0x3a finished");
        assertEquals(10, i.getInt(), "+0x34 max play minutes");
        assertEquals(0, i.remaining());

        ByteBuffer s = body(S2CTournamentRoundPacket.score(7, 1, 0));
        assertEquals(7, s.getInt()); assertEquals(1, s.get()); assertEquals(0, s.get()); assertEquals(0, s.remaining());

        ByteBuffer r = body(S2CTournamentRoundPacket.result(7, false, 1, 1, 0, 1));
        assertEquals(7, r.getInt());
        assertArrayEquals(new byte[] {0, 1, 0, 1, 0, 1}, new byte[] {r.get(), r.get(), r.get(), r.get(), r.get(), r.get()});
        assertEquals(0, r.remaining());
    }

    // notes-v193: 0x26BF ends with the round start / end FILETIMEs (5c8120 -> tournament object +0x230 / +0x238).
    @Test void myInfoRoundTimes() {
        java.util.Date start = new java.util.Date(1_700_000_000_000L), end = new java.util.Date(1_700_086_400_000L);
        ByteBuffer m = body(new com.jftse.emulator.server.core.packets.tournament.S2CTournamentMyInfoAnswerPacket(9, (byte) 2, (byte) 0, "B", true, start, end));
        assertEquals(0, m.get()); assertEquals(9, m.getInt()); assertEquals(2, m.get()); m.get();
        for (int k = 0; k < 3; k++) while (m.getChar() != 0) { }
        m.get(); assertEquals(1, m.get(), "canPlay");
        assertEquals((1_700_000_000_000L + 11_644_473_600_000L) * 10_000L, m.getLong());
        assertEquals((1_700_086_400_000L + 11_644_473_600_000L) * 10_000L, m.getLong());
        assertEquals(0, m.remaining());
    }

    // notes-v198: 0x2410 (4c8800: i16 code; code 0 u8 auto + start / end / now FILETIMEs), 0x203D (49ec70: u32 seconds).
    @Test void waitAndPlayTime() {
        long ft = 11_644_473_600_000L;
        java.util.Date a = new java.util.Date(1_700_000_000_000L), b = new java.util.Date(1_700_000_600_000L), n = new java.util.Date(1_700_000_060_000L);
        ByteBuffer w = body(S2CTournamentRoundPacket.waitTime(true, a, b, n));
        assertEquals(0, w.getShort()); assertEquals(1, w.get(), "+0xca6 auto");
        assertEquals((a.getTime() + ft) * 10_000L, w.getLong()); assertEquals((b.getTime() + ft) * 10_000L, w.getLong()); assertEquals((n.getTime() + ft) * 10_000L, w.getLong());
        assertEquals(0, w.remaining());
        ByteBuffer c = body(S2CTournamentRoundPacket.waitCode(S2CTournamentRoundPacket.WAIT_FINISHED));
        assertEquals(2, c.getShort()); assertEquals(0, c.remaining());
        ByteBuffer g = body(S2CTournamentRoundPacket.gameTime(600));
        assertEquals(600, g.getInt()); assertEquals(0, g.remaining());
        assertEquals(0x2410, (int) S2CTournamentRoundPacket.waitCode((short) 1).getPacketId());
        assertEquals(0x203D, (int) S2CTournamentRoundPacket.gameTime(1).getPacketId());
    }

    // notes-v198 [I] server rules: walkover after the wait with one seated player; time-up winner by sets then points.
    @Test void rules() {
        long start = 1_000_000L, end = start + TournamentRooms.WAIT_MINUTES * 60_000L;
        assertTrue(TournamentRooms.walkoverDue(true, start, end, 1, (short) 0));
        assertTrue(TournamentRooms.walkoverDue(true, start, end - 4_000, 1, (short) 1), "within the slack");
        assertFalse(TournamentRooms.walkoverDue(true, start, end - 60_000, 1, (short) 0), "too early");
        assertFalse(TournamentRooms.walkoverDue(true, start, end, 2, (short) 0), "both seated");
        assertFalse(TournamentRooms.walkoverDue(false, start, end, 1, (short) 0), "playing");
        assertFalse(TournamentRooms.walkoverDue(true, null, end, 1, (short) 0));
        assertFalse(TournamentRooms.walkoverDue(true, start, end, 1, (short) 5), "spectator");
        assertEquals(0, TournamentRooms.timeUpWinner(1, 0, 0, 3));
        assertEquals(1, TournamentRooms.timeUpWinner(0, 1, 3, 0));
        assertEquals(0, TournamentRooms.timeUpWinner(1, 1, 2, 1));
        assertEquals(1, TournamentRooms.timeUpWinner(0, 0, 0, 1));
        assertEquals(-1, TournamentRooms.timeUpWinner(1, 1, 2, 2));
    }
}
