package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.handler.event.HourlyPlayEvent;
import com.jftse.entities.database.model.event.SGameEvent;
import com.jftse.server.core.protocol.Packet;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** notes-v179: spectator gold betting rules and the hourly play events. No Spring context, persistence or sockets. */
class RoomBettingTest {
    private static Room room(int amount) {
        Room room = new Room();
        room.setMode((byte) 0);
        room.setBettingType((char) RoomBetting.MODE_GOLD);
        room.setBettingAmount(amount);
        room.setStatus(RoomStatus.NotRunning);
        return room;
    }

    private static RoomPlayer seat(Room room, int position, byte side) {
        RoomPlayer rp = new RoomPlayer(mock(FTPlayer.class));
        rp.setPosition((short) position);
        rp.setBettingSide(side);
        room.getRoomPlayerList().add(rp);
        return rp;
    }

    @Test void createKeepsGoldBettingOnlyForBasicWithAListedAmount() {
        assertEquals(2, RoomBetting.sanitize((char) 2, 1000, (byte) 0));
        assertEquals(2, RoomBetting.sanitize((char) (2 | 5 << 8), 100, (byte) 0), "the coin byte is dropped");
        assertEquals(0, RoomBetting.sanitize((char) 2, 1234, (byte) 0), "an amount not in Item_Betting");
        assertEquals(0, RoomBetting.sanitize((char) 2, 1000, (byte) 1), "battle");
        assertEquals(0, RoomBetting.sanitize((char) 1, 1000, (byte) 0), "item betting is not served");
        assertEquals(0, RoomBetting.sanitize('0', 0, (byte) 0), "the quick-room '0' is no bet");
        Room plain = room(1000);
        plain.setBettingType((char) 0);
        assertEquals(0, RoomBetting.mode(plain));
        assertEquals(2, RoomBetting.mode(room(1000)));
    }

    @Test void betAndCancelCodes() {
        Room room = room(1000);
        RoomPlayer player = seat(room, 0, RoomBetting.NONE), spectator = seat(room, 5, RoomBetting.NONE);
        assertEquals(RoomBetting.CANNOT, RoomBetting.check(room, player, (byte) 2, RoomBetting.RED, 5000), "players cannot bet");
        assertEquals(RoomBetting.CANNOT, RoomBetting.check(room, spectator, (byte) 2, (byte) 3, 5000), "no such side");
        assertEquals(RoomBetting.CANNOT, RoomBetting.check(room, spectator, (byte) 1, RoomBetting.RED, 5000), "item bet mode");
        assertEquals(RoomBetting.NEED_GOLD, RoomBetting.check(room, spectator, (byte) 2, RoomBetting.RED, 999));
        assertEquals(RoomBetting.OK, RoomBetting.check(room, spectator, (byte) 2, RoomBetting.BLUE, 1000));
        room.setStatus(RoomStatus.Running);
        assertEquals(RoomBetting.CANNOT, RoomBetting.check(room, spectator, (byte) 2, RoomBetting.BLUE, 1000), "match running");
        spectator.setBettingSide(RoomBetting.BLUE);
        assertEquals(RoomBetting.ALREADY, RoomBetting.check(room, spectator, (byte) 2, RoomBetting.RED, 1000));
        assertEquals(RoomBetting.CANCEL_FAILED_02, RoomBetting.checkCancel(room, spectator), "cannot cancel while running");
        room.setStatus(RoomStatus.NotRunning);
        assertEquals(RoomBetting.CANCEL_OK, RoomBetting.checkCancel(room, spectator));
        assertEquals(RoomBetting.CANCEL_FAILED_01, RoomBetting.checkCancel(room, player), "no bet");
    }

    @Test void startNeedsABettorOnEachSide() {
        Room room = room(500);
        seat(room, 0, RoomBetting.NONE);
        seat(room, 5, RoomBetting.RED);
        assertEquals(RoomBetting.START_NO_BETTOR, RoomBetting.startCheck(room));
        seat(room, 6, RoomBetting.BLUE);
        assertEquals(0, RoomBetting.startCheck(room));
        Room plain = room(500);
        plain.setBettingType((char) 0);
        assertEquals(0, RoomBetting.startCheck(plain), "not a betting room");
    }

    @Test void winnersShareThePot() {
        Room room = room(1000);
        RoomPlayer a = seat(room, 5, RoomBetting.RED), b = seat(room, 6, RoomBetting.RED), c = seat(room, 7, RoomBetting.BLUE);
        seat(room, 0, RoomBetting.NONE);
        Map<RoomPlayer, Integer> red = RoomBetting.settle(room.getRoomPlayerList(), 1000, true).stream().collect(Collectors.toMap(RoomBetting.Payout::bettor, RoomBetting.Payout::gold));
        assertEquals(Map.of(a, 1500, b, 1500, c, 0), red, "3000 shared by the two red bettors");
        Map<RoomPlayer, Integer> blue = RoomBetting.settle(room.getRoomPlayerList(), 1000, false).stream().collect(Collectors.toMap(RoomBetting.Payout::bettor, RoomBetting.Payout::gold));
        assertEquals(Map.of(a, 0, b, 0, c, 3000), blue);
        c.setBettingSide(RoomBetting.NONE);
        List<RoomBetting.Payout> none = RoomBetting.settle(room.getRoomPlayerList(), 1000, false);
        assertEquals(List.of(1000, 1000), none.stream().map(RoomBetting.Payout::gold).toList(), "no winner: stakes back");
    }

    @Test void hourlyEventWindowThrottleAndNotice() {
        Date now = new Date();
        SGameEvent e = new SGameEvent();
        e.setType(HourlyPlayEvent.NEW_YEAR);
        e.setEnabled(true);
        assertTrue(HourlyPlayEvent.active(List.of(e), HourlyPlayEvent.NEW_YEAR, now));
        assertFalse(HourlyPlayEvent.active(List.of(e), HourlyPlayEvent.RICE_CAKE, now), "another type");
        e.setEndDate(new Date(now.getTime() - 1000));
        assertFalse(HourlyPlayEvent.active(List.of(e), HourlyPlayEvent.NEW_YEAR, now), "over");
        e.setEndDate(null);
        e.setEnabled(false);
        assertFalse(HourlyPlayEvent.active(List.of(e), HourlyPlayEvent.NEW_YEAR, now), "disabled");

        long t = 1_000_000_000L;
        assertTrue(HourlyPlayEvent.claim("T", 42, t));
        assertFalse(HourlyPlayEvent.claim("T", 42, t + HourlyPlayEvent.MIN_INTERVAL_MS - 1));
        assertTrue(HourlyPlayEvent.claim("T", 43, t), "per character");
        assertTrue(HourlyPlayEvent.claim("T", 42, t + HourlyPlayEvent.MIN_INTERVAL_MS));

        Packet p = HourlyPlayEvent.goldNotice(1000);
        byte[] raw = p.getRawPacket();
        ByteBuffer b = ByteBuffer.wrap(raw, 8, raw.length - 8).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, b.get(), "type 0: MSG_GOLD_EVENT");
        assertEquals(1000, b.getInt());
        assertEquals(0, b.getInt());
        assertFalse(b.hasRemaining(), "the client skips exactly 8 bytes");
    }
}
