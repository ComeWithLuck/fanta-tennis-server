package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.packets.tournament.S2CTournamentBracketPacket;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.entities.database.model.tournament.TournamentEntry;
import com.jftse.entities.database.model.tournament.TournamentMatch;
import com.jftse.entities.database.repository.guild.GuildRepository;
import com.jftse.entities.database.repository.player.PlayerRepository;
import com.jftse.entities.database.repository.tournament.TournamentEntryRepository;
import com.jftse.entities.database.repository.tournament.TournamentMatchRepository;
import com.jftse.server.core.service.TournamentBracketService;
import com.jftse.server.core.service.TournamentBrackets;
import com.jftse.server.core.service.TournamentService;
import com.jftse.server.core.service.InventoryService;
import com.jftse.server.core.service.ProductService;
import com.jftse.server.core.service.impl.TournamentBracketServiceImpl;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** notes-v184: bracket arithmetic (client heap layout, 5c9d20 / 5cafd0) and the [I] seeding / bye rules. */
class TournamentBracketTest {
    @Test void byesWinByDefaultAndOpenMatches() {
        // 8 teams, leaves 0..4 occupied: leaves 5, 6, 7 are byes.
        boolean[] occ = {true, true, true, true, true, false, false, false};
        int[][] r = TournamentBrackets.resolve(occ, null, null);
        int[] w = r[0], how = r[1];
        assertEquals(7, w.length);
        // heap: leaf i = node 7 + i. node 5 = leaves 4 / 5 (5 a bye) -> team 4 by default; node 6 = two byes -> nobody;
        // node 2 = nodes 5 / 6 -> team 4 by default again; nodes 3 / 4 (leaves 0..3) are the open matches.
        assertEquals(4, w[5]); assertEquals(TournamentBrackets.HOW_DEFAULT, how[5]);
        assertEquals(-1, w[6]); assertEquals(0, how[6]);
        assertEquals(4, w[2]); assertEquals(TournamentBrackets.HOW_DEFAULT, how[2]);
        assertEquals(List.of(3, 4), TournamentBrackets.openMatches(occ, w));
    }

    @Test void heapLeavesAndDefaults() {
        boolean[] occ = {true, true, true, false}; // leaves 0..2, leaf 3 a bye
        int[][] r = TournamentBrackets.resolve(occ, null, null);
        // nodes: 0 final, 1 = leaves 0 / 1, 2 = leaves 2 / 3 -> team 2 by default.
        assertArrayEquals(new int[]{-1, -1, 2}, r[0]);
        assertArrayEquals(new int[]{0, 0, TournamentBrackets.HOW_DEFAULT}, r[1]);
        assertEquals(List.of(1), TournamentBrackets.openMatches(occ, r[0]));
        // a decided node 1 (team 1 normal) opens the final.
        int[][] r2 = TournamentBrackets.resolve(occ, new int[]{-1, 1, -1}, new byte[]{0, 1, 0});
        assertArrayEquals(new int[]{-1, 1, 2}, r2[0]);
        assertEquals(List.of(0), TournamentBrackets.openMatches(occ, r2[0]));
        // an inconsistent decision (team 3 is not under node 1) is ignored.
        assertEquals(-1, TournamentBrackets.resolve(occ, new int[]{-1, 3, -1}, null)[0][1]);
        assertTrue(TournamentBrackets.validSize(64));
        assertFalse(TournamentBrackets.validSize(6));
        assertFalse(TournamentBrackets.validSize(128));
        assertEquals(1, TournamentBrackets.groupOf(5, 2));
        assertEquals(3, TournamentBrackets.finalLeaf(1, 1, 2));
    }

    @Test void serviceMakesEveryGroupOnceAndMarksEntries() {
        TournamentService ts = mock(TournamentService.class);
        TournamentEntryRepository entries = mock(TournamentEntryRepository.class);
        TournamentMatchRepository matches = mock(TournamentMatchRepository.class);
        InventoryService inventory = mock(InventoryService.class);
        ProductService products = mock(ProductService.class);
        TournamentBracketServiceImpl service = new TournamentBracketServiceImpl(ts, entries, matches, mock(PlayerRepository.class), inventory, products, mock(GuildRepository.class));
        Tournament t = new Tournament();
        t.setId(3L);
        t.setMaxTeams(4);
        t.setHasQualifying(true);
        t.setQualifyingGroups(2);
        when(ts.state(eq(t), any())).thenReturn((byte) 1);
        assertNull(service.bracket(t, false, 0, new Date()), "still applying: not made yet");
        when(ts.state(eq(t), any())).thenReturn((byte) 3);
        List<TournamentEntry> list = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            TournamentEntry e = new TournamentEntry();
            e.setId(i);
            e.setPlayerId(100 + i);
            list.add(e);
        }
        when(entries.findAllByTournamentOrderByIdAsc(t)).thenReturn(list);
        Map<String, List<TournamentMatch>> saved = new HashMap<>();
        when(matches.findAllByTournamentAndIsFinalAndGroupIndexOrderBySlotAsc(eq(t), anyBoolean(), anyByte()))
                .thenAnswer(a -> saved.getOrDefault(a.getArgument(1) + ":" + a.getArgument(2), List.of()));
        when(matches.saveAll(anyList())).thenAnswer(a -> {
            List<TournamentMatch> rows = a.getArgument(0);
            saved.put(rows.get(0).getIsFinal() + ":" + rows.get(0).getGroupIndex(), rows);
            return rows;
        });
        TournamentBracketService.Bracket b0 = service.bracket(t, false, 0, new Date());
        assertEquals(4, b0.teams());
        assertEquals(Arrays.asList(1L, 3L, 5L, null), b0.leaves(), "round robin: entries 1, 3, 5 in group 0");
        assertEquals(2, saved.size(), "both groups made at once");
        assertEquals(Arrays.asList(2L, 4L, null, null), service.bracket(t, false, 1, new Date()).leaves());
        assertTrue(list.stream().allMatch(e -> e.getState() == 2), "every placed entry entered");
        assertEquals((byte) 1, list.get(1).getGroupIndex());
        assertNull(service.bracket(t, true, 0, new Date()), "the final waits for the group finals");
        assertEquals(7, saved.get("false:0").size());
    }

    @Test void packets() {
        TournamentBracketService.Bracket b = new TournamentBracketService.Bracket(4, Arrays.asList(1L, 2L, 3L, null), new int[]{-1, -1, 2}, new int[]{0, 0, 2});
        byte[] raw = S2CTournamentBracketPacket.results(9, false, 1, b).getRawPacket();
        ByteBuffer r = ByteBuffer.wrap(raw, 8, raw.length - 8).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, r.get());
        assertEquals(9, r.getInt());
        assertEquals(0, r.get());
        assertEquals(1, r.get());
        assertEquals(3, r.getShort(), "teams - 1 results");
        assertEquals(-1, r.get()); assertEquals(0, r.get());
        assertEquals(-1, r.get()); assertEquals(0, r.get());
        assertEquals(2, r.get()); assertEquals(2, r.get());
        assertFalse(r.hasRemaining());
        raw = S2CTournamentBracketPacket.teams(9, true, 0, List.of("Ann", "", "A_very_long_name")).getRawPacket();
        r = ByteBuffer.wrap(raw, 8, raw.length - 8).order(ByteOrder.LITTLE_ENDIAN);
        r.get(); r.getInt(); assertEquals(1, r.get()); r.get();
        assertEquals(1, r.get(), "individual entry type");
        assertEquals(3, r.getShort());
        StringBuilder s = new StringBuilder();
        for (char c; (c = r.getChar()) != 0; ) s.append(c);
        assertEquals("Ann", s.toString());
        assertEquals(0, r.getChar(), "a bye is an empty name");
        s.setLength(0);
        for (char c; (c = r.getChar()) != 0; ) s.append(c);
        assertEquals("A_very_long_", s.toString(), "12 characters at most");
    }

    @Test void playOpenMatchesRecordAndAdvance() {
        TournamentService ts = mock(TournamentService.class);
        TournamentEntryRepository entries = mock(TournamentEntryRepository.class);
        TournamentMatchRepository matches = mock(TournamentMatchRepository.class);
        InventoryService inventory = mock(InventoryService.class);
        ProductService products = mock(ProductService.class);
        TournamentBracketServiceImpl service = new TournamentBracketServiceImpl(ts, entries, matches, mock(PlayerRepository.class), inventory, products, mock(GuildRepository.class));
        Tournament t = new Tournament();
        t.setId(4L);
        t.setMaxTeams(4);
        when(ts.state(eq(t), any())).thenReturn((byte) 5);
        Map<Long, TournamentEntry> byId = new HashMap<>();
        List<TournamentEntry> list = new ArrayList<>();
        for (long i = 1; i <= 3; i++) {
            TournamentEntry e = new TournamentEntry();
            e.setId(i);
            e.setPlayerId(100 + i);
            list.add(e);
            byId.put(i, e);
        }
        when(entries.findAllByTournamentOrderByIdAsc(t)).thenReturn(list);
        when(entries.findById(anyLong())).thenAnswer(a -> Optional.ofNullable(byId.get((Long) a.getArgument(0))));
        when(ts.entry(eq(t), anyLong())).thenAnswer(a -> list.stream().filter(e -> e.getPlayerId() == (long) a.getArgument(1)).findFirst().orElse(null));
        List<TournamentMatch> rows = new ArrayList<>();
        when(matches.findAllByTournamentAndIsFinalAndGroupIndexOrderBySlotAsc(eq(t), anyBoolean(), anyByte())).thenAnswer(a -> rows);
        when(matches.saveAll(anyList())).thenAnswer(a -> {
            List<TournamentMatch> r = a.getArgument(0);
            for (int i = 0; i < r.size(); i++) r.get(i).setId(50L + i);
            rows.addAll(r);
            return r;
        });
        when(matches.findById(anyLong())).thenAnswer(a -> rows.stream().filter(m -> m.getId().equals(a.getArgument(0))).findFirst());
        // leaves: entries 1, 2, 3, bye -> node 1 = 1 vs 2 (open), node 2 = 3 by default.
        TournamentBracketService.MyMatch m = service.myMatch(t, 101, new Date());
        assertNotNull(m);
        assertEquals(1, m.node());
        assertEquals(2L, m.opponentEntry());
        assertNull(service.myMatch(t, 103, new Date()), "entry 3 waits for the final");
        assertFalse(service.record(m.matchId(), 999, (byte) 2, (byte) 0).recorded(), "not a player of the match");
        assertTrue(service.record(m.matchId(), 102, (byte) 2, (byte) 1).recorded());
        assertEquals((byte) 3, byId.get(1L).getState(), "the loser drops");
        assertFalse(service.record(m.matchId(), 102, (byte) 2, (byte) 1).recorded(), "already decided");
        TournamentBracketService.MyMatch fin = service.myMatch(t, 103, new Date());
        assertEquals(0, fin.node());
        assertEquals(2L, fin.opponentEntry());
        t.setRewards("3000:1, 0:0, 77:0");
        com.jftse.entities.database.model.item.Product holy = new com.jftse.entities.database.model.item.Product();
        holy.setProductIndex(3000); holy.setUse0(50); holy.setUse1(100); holy.setUse2(500);
        when(products.findProductByProductItemIndex(3000)).thenReturn(holy);
        com.jftse.entities.database.model.pocket.PlayerPocket placed = new com.jftse.entities.database.model.pocket.PlayerPocket();
        when(inventory.addItem(103L, 3000, 100, List.of())).thenReturn(List.of(placed));
        TournamentBracketService.Recorded won = service.record(fin.matchId(), 103, (byte) 2, (byte) 0);
        assertTrue(won.recorded());
        assertTrue(won.champion());
        assertEquals(List.of(placed), won.rewards(), "option 1 -> Use1 = 100; unknown product 77 skipped");
        assertEquals((byte) 4, byId.get(3L).getState(), "the final's winner is the champion");
        TournamentBracketService.Bracket b = service.bracket(t, true, 0, new Date());
        assertArrayEquals(new int[]{2, 1, 2}, b.winners());
        assertArrayEquals(new int[]{1, 1, 2}, b.hows());
    }
}
