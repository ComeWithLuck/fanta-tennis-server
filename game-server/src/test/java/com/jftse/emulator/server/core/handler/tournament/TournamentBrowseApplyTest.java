package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.packets.tournament.S2CTournamentApplyAnswerPacket;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentDetailAnswerPacket;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentListAnswerPacket;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentMyInfoAnswerPacket;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.entities.database.model.tournament.TournamentEntry;
import com.jftse.entities.database.repository.tournament.TournamentEntryRepository;
import com.jftse.entities.database.repository.tournament.TournamentRepository;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.service.TournamentService;
import com.jftse.server.core.service.impl.TournamentServiceImpl;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** notes-v176: tournament browse / individual apply. Mocked repositories only; the byte layouts are the client readers
 * 5ca470 (0x26B0 record), 5c9040 (0x26AE header), 5c8120 (0x26BF) and 5c7330 (0x26B2). */
class TournamentBrowseApplyTest {
    private static final long DAY = 86_400_000L;

    private static Tournament tournament(long id, Date now) {
        Tournament t = new Tournament();
        t.setId(id);
        t.setTitle("Web Cup");
        t.setEntryType((byte) 1);
        t.setGameMode((byte) 0);
        t.setApplyStart(new Date(now.getTime() - DAY));
        t.setApplyEnd(new Date(now.getTime() + DAY));
        t.setTotalStart(new Date(now.getTime() + 2 * DAY));
        t.setTotalEnd(new Date(now.getTime() + 3 * DAY));
        t.setMaxTeams(2);
        t.setRewards("1001:0, 2002:2");
        return t;
    }

    private static ByteBuffer body(Packet packet) {
        byte[] raw = packet.getRawPacket();
        return ByteBuffer.wrap(raw, 8, raw.length - 8).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static String wstr(ByteBuffer b) {
        StringBuilder s = new StringBuilder();
        for (char c; (c = b.getChar()) != 0; ) s.append(c);
        return s.toString();
    }

    @Test void stateFollowsThePeriods() {
        TournamentServiceImpl service = new TournamentServiceImpl(mock(TournamentRepository.class), mock(TournamentEntryRepository.class));
        Date now = new Date();
        Tournament t = tournament(1, now);
        assertEquals(1, service.state(t, now), "inside the application period: APPLY");
        assertEquals(0, service.state(t, new Date(now.getTime() - 2 * DAY)), "before: PREPARE");
        assertEquals(4, service.state(t, new Date(now.getTime() + DAY + DAY / 2)), "after applying, no qualifying: PREPARE_FINAL");
        t.setHasQualifying(true);
        assertEquals(2, service.state(t, new Date(now.getTime() + DAY + DAY / 2)), "PREPARE_QUALIFYING");
        assertEquals(3, service.state(t, new Date(now.getTime() + 2 * DAY + DAY / 2)), "QUALIFYING");
        assertEquals(6, service.state(t, new Date(now.getTime() + 4 * DAY)), "FINISHED");
        t.setState((byte) 8);
        assertEquals(8, service.state(t, now), "a stored state wins (CANCELED)");
    }

    @Test void applyAndCancelCodes() {
        TournamentRepository tournaments = mock(TournamentRepository.class);
        TournamentEntryRepository entries = mock(TournamentEntryRepository.class);
        TournamentServiceImpl service = new TournamentServiceImpl(tournaments, entries);
        Date now = new Date();
        Tournament t = tournament(7, now);
        t.setEnabled(true);

        assertEquals(-2, service.apply(null, 1, 10, now), "no such tournament");
        t.setEntryType((byte) 0);
        assertEquals(-3, service.apply(t, 1, 10, now), "guild entry only");
        t.setEntryType((byte) 1);
        assertEquals(-6, service.apply(t, 1, 10, new Date(now.getTime() + 2 * DAY)), "outside the application period");
        when(entries.existsByTournamentAndAccountIdAndPlayerIdNot(t, 1L, 10L)).thenReturn(true);
        assertEquals(-4, service.apply(t, 1, 10, now), "another character of this account");
        when(entries.existsByTournamentAndAccountIdAndPlayerIdNot(t, 1L, 10L)).thenReturn(false);
        when(entries.countByTournament(t)).thenReturn(2L);
        assertEquals(-7, service.apply(t, 1, 10, now), "full");
        when(entries.countByTournament(t)).thenReturn(0L);
        Tournament other = tournament(8, now);
        other.setEnabled(true);
        TournamentEntry otherEntry = new TournamentEntry();
        otherEntry.setTournament(other);
        when(entries.findAllByPlayerId(10L)).thenReturn(List.of(otherEntry));
        assertEquals(-5, service.apply(t, 1, 10, now), "already in a running tournament");
        assertSame(other, service.activeOther(t, 10, now));
        when(entries.findAllByPlayerId(10L)).thenReturn(List.of());
        assertEquals(0, service.apply(t, 1, 10, now));
        verify(entries).save(argThat(e -> e.getTournament() == t && e.getPlayerId() == 10L && e.getAccountId() == 1L && e.getState() == 1));

        assertEquals(-1, service.cancel(t, 10, now), "nothing to cancel");
        TournamentEntry mine = new TournamentEntry();
        when(entries.findByTournamentAndPlayerId(t, 10L)).thenReturn(Optional.of(mine));
        assertEquals(-5, service.apply(t, 1, 10, now), "the same tournament twice");
        assertEquals(-2, service.cancel(t, 10, new Date(now.getTime() + 2 * DAY)), "cancel outside the application period");
        assertEquals(0, service.cancel(t, 10, now));
        verify(entries).delete(mine);
    }

    @Test void rewardsColumn() {
        int[][] r = TournamentService.rewards("1001:0, 2002:2,bad,3003");
        assertArrayEquals(new int[]{1001, 0}, r[0]);
        assertArrayEquals(new int[]{2002, 2}, r[1]);
        assertArrayEquals(new int[]{0, 0}, r[2]);
        assertArrayEquals(new int[]{3003, 0}, r[3]);
        assertArrayEquals(new int[]{0, 0}, r[4]);
        assertArrayEquals(new int[]{0, 0}, TournamentService.rewards(null)[0]);
    }

    @Test void listRecordLayout() {
        Date now = new Date();
        Tournament t = tournament(5, now);
        ByteBuffer b = body(new S2CTournamentListAnswerPacket(List.of(t), List.of((byte) 1)));
        assertEquals(1, b.get(), "count");
        assertEquals(5, b.getInt(), "id");
        assertEquals(1, b.get(), "entry: individual");
        assertEquals(0, b.get(), "game mode: basic");
        assertEquals("Web Cup", wstr(b));
        long fileTime = (t.getApplyStart().getTime() + 11644473600000L) * 10000L;
        assertEquals(fileTime, b.getLong(), "apply start (+0x30)");
        b.getLong(); b.getLong(); b.getLong();
        assertEquals(0, b.getInt()); assertEquals(0, b.getInt()); assertEquals(0, b.get()); assertEquals(0, b.getInt());
        assertEquals(0L, b.getLong(), "no round time");
        assertEquals(1, b.get(), "state");
        assertEquals(0, b.get(), "hasQualifying");
        assertEquals(2, b.getInt(), "teams");
        assertEquals(0, b.getInt(), "groups");
        assertEquals(1001, b.getInt()); assertEquals(0, b.getInt());
        assertEquals(2002, b.getInt()); assertEquals(2, b.getInt());
        for (int i = 0; i < 6; i++) assertEquals(0, b.getInt(), "rewards 3..5");
        for (int i = 0; i < 64; i++) assertEquals(0, b.getInt(), "two 16 x {u32, u32} tables");
        assertFalse(b.hasRemaining());
    }

    @Test void detailMyInfoApplyLayouts() {
        Date now = new Date();
        Tournament t = tournament(9, now);
        ByteBuffer d = body(new S2CTournamentDetailAnswerPacket(t, (byte) 1));
        assertEquals(0, d.get());
        assertEquals(9, d.getInt());
        assertEquals(1, d.get());
        assertEquals(0, d.get());
        assertEquals("Web Cup", wstr(d));
        for (int i = 0; i < 4; i++) d.getLong();
        d.getInt(); d.getInt(); d.get(); d.getInt(); d.getLong();
        assertEquals(1, d.get(), "state");
        d.get(); d.getInt(); d.getInt();
        assertFalse(d.hasRemaining(), "the header has no item tables");
        assertEquals(1, body(new S2CTournamentDetailAnswerPacket(null, (byte) 0)).get(), "unknown -> MSG_TOURNEY_FAILED_TO_JOIN");

        ByteBuffer m = body(new S2CTournamentMyInfoAnswerPacket(9, (byte) 1, (byte) 0));
        assertEquals(0, m.get()); assertEquals(9, m.getInt()); assertEquals(1, m.get()); assertEquals(0, m.get());
        assertEquals("", wstr(m)); assertEquals("", wstr(m)); assertEquals("", wstr(m));
        assertEquals(0, m.get()); assertEquals(0, m.get(), "canPlay"); assertEquals(0L, m.getLong()); assertEquals(0L, m.getLong());
        assertFalse(m.hasRemaining());

        ByteBuffer a = body(new S2CTournamentApplyAnswerPacket((byte) -5, "Other Cup"));
        assertEquals(-5, a.get());
        assertEquals("Other Cup", wstr(a));
        assertEquals(1, body(new S2CTournamentApplyAnswerPacket((byte) 0, "x")).remaining(), "only -5 carries a title");
        assertEquals("Other Cup".getBytes(StandardCharsets.UTF_16LE).length + 3, body(new S2CTournamentApplyAnswerPacket((byte) -5, "Other Cup")).remaining());
    }
}
