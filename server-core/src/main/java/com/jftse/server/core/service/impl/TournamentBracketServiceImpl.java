package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.entities.database.model.tournament.TournamentEntry;
import com.jftse.entities.database.model.tournament.TournamentMatch;
import com.jftse.entities.database.repository.player.PlayerRepository;
import com.jftse.entities.database.repository.tournament.TournamentEntryRepository;
import com.jftse.entities.database.repository.tournament.TournamentMatchRepository;
import com.jftse.server.core.service.TournamentBracketService;
import com.jftse.server.core.service.TournamentBrackets;
import com.jftse.server.core.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TournamentBracketServiceImpl implements TournamentBracketService {
    private final TournamentService tournamentService;
    private final TournamentEntryRepository entryRepository;
    private final TournamentMatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final com.jftse.server.core.service.InventoryService inventoryService;
    private final com.jftse.server.core.service.ProductService productService;
    private final com.jftse.entities.database.repository.guild.GuildRepository guildRepository;

    @Override
    @Transactional
    public Bracket bracket(Tournament t, boolean isFinal, int group, Date now) {
        if (t == null)
            return null;
        byte state = tournamentService.state(t, now);
        if (state < 2 || state == 7 || state == 8)
            return null;
        boolean qualifying = Boolean.TRUE.equals(t.getHasQualifying());
        int groups = TournamentBracketService.groups(t);
        boolean finalBracket = isFinal || !qualifying;
        int g = finalBracket ? 0 : group;
        if (g < 0 || g >= groups)
            return null;
        int teams = TournamentBracketService.teams(t, finalBracket);
        if (teams == 0)
            return null;
        List<TournamentMatch> rows = matchRepository.findAllByTournamentAndIsFinalAndGroupIndexOrderBySlotAsc(t, finalBracket, (byte) g);
        if (rows.isEmpty() && finalBracket && qualifying) {
            List<Long> leaves = finalLeaves(t, groups, now);
            if (leaves == null)
                return null;
            rows = create(t, true, 0, teams, leaves);
        } else if (rows.isEmpty()) {
            // Every group at once, so every placed entry is "entered" with its group from the start.
            for (int gg = 0; gg < groups; gg++) {
                boolean single = !qualifying;
                if (!matchRepository.findAllByTournamentAndIsFinalAndGroupIndexOrderBySlotAsc(t, single, (byte) gg).isEmpty())
                    continue;
                List<TournamentMatch> made = create(t, single, gg, teams, groupLeaves(t, groups, gg, teams));
                if (gg == g)
                    rows = made;
            }
        }
        return read(teams, rows);
    }

    /** Entries of group g in id order, padded with byes to the bracket size; marks them entered. */
    private List<Long> groupLeaves(Tournament t, int groups, int g, int teams) {
        List<TournamentEntry> entries = entryRepository.findAllByTournamentOrderByIdAsc(t);
        List<Long> leaves = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (TournamentBrackets.groupOf(i, groups) != g || leaves.size() >= teams)
                continue;
            TournamentEntry e = entries.get(i);
            e.setState((byte) 2);
            e.setGroupIndex((byte) g);
            entryRepository.save(e);
            leaves.add(e.getId());
        }
        while (leaves.size() < teams) leaves.add(null);
        return leaves;
    }

    /** The final's leaves from the decided group finals, or null while a group is still playing. */
    private List<Long> finalLeaves(Tournament t, int groups, Date now) {
        Long[] leaves = new Long[2 * groups];
        for (int g = 0; g < groups; g++) {
            Bracket b = bracket(t, false, g, now);
            if (b == null || b.winners()[0] < 0)
                return null;
            int champion = b.winners()[0];
            int a = TournamentBrackets.teamAt(occupied(b), b.winners(), 1), c = TournamentBrackets.teamAt(occupied(b), b.winners(), 2);
            int runnerUp = a == champion ? c : a;
            leaves[TournamentBrackets.finalLeaf(g, 0, groups)] = b.leaves().get(champion);
            leaves[TournamentBrackets.finalLeaf(g, 1, groups)] = runnerUp < 0 ? null : b.leaves().get(runnerUp);
        }
        return Arrays.asList(leaves);
    }

    private List<TournamentMatch> create(Tournament t, boolean isFinal, int g, int teams, List<Long> leaves) {
        List<TournamentMatch> rows = new ArrayList<>();
        for (int slot = 0; slot < 2 * teams - 1; slot++) {
            TournamentMatch m = new TournamentMatch();
            m.setTournament(t);
            m.setIsFinal(isFinal);
            m.setGroupIndex((byte) g);
            m.setSlot(slot);
            if (slot >= teams - 1)
                m.setEntryA(leaves.get(slot - (teams - 1)));
            rows.add(m);
        }
        return matchRepository.saveAll(rows);
    }

    static boolean[] occupied(Bracket b) {
        boolean[] o = new boolean[b.teams()];
        for (int i = 0; i < b.teams(); i++) o[i] = b.leaves().get(i) != null;
        return o;
    }

    /** Rows -> bracket: decided inner winners (by entry id) mapped to leaf indexes, byes resolved. */
    static Bracket read(int teams, List<TournamentMatch> rows) {
        List<Long> leaves = new ArrayList<>(Collections.nCopies(teams, (Long) null));
        int[] decided = new int[teams - 1];
        byte[] how = new byte[teams - 1];
        Arrays.fill(decided, -1);
        for (TournamentMatch m : rows) {
            if (m.getSlot() >= teams - 1 && m.getSlot() < 2 * teams - 1)
                leaves.set(m.getSlot() - (teams - 1), m.getEntryA());
        }
        for (TournamentMatch m : rows) {
            if (m.getSlot() < teams - 1 && m.getWinnerEntry() != null) {
                decided[m.getSlot()] = leaves.indexOf(m.getWinnerEntry());
                how[m.getSlot()] = m.getHowWin() == null ? 0 : m.getHowWin();
            }
        }
        boolean[] occupied = new boolean[teams];
        for (int i = 0; i < teams; i++) occupied[i] = leaves.get(i) != null;
        int[][] r = TournamentBrackets.resolve(occupied, decided, how);
        return new Bracket(teams, leaves, r[0], r[1]);
    }

    @Override
    @Transactional
    public MyMatch myMatch(Tournament t, long playerId, Date now) {
        if (t == null)
            return null;
        byte state = tournamentService.state(t, now);
        boolean qualifying = Boolean.TRUE.equals(t.getHasQualifying());
        boolean isFinal;
        if (state == 3 && qualifying) isFinal = false;
        else if (state == 5) isFinal = true;
        else return null;
        // Make the brackets first (they mark the placed entries entered).
        bracket(t, !qualifying, 0, now);
        TournamentEntry mine = tournamentService.entry(t, playerId);
        if (mine == null || mine.getState() == null || mine.getState() != 2)
            return null;
        int group = isFinal ? 0 : mine.getGroupIndex() == null ? 0 : mine.getGroupIndex();
        Bracket b = bracket(t, isFinal, group, now);
        if (b == null)
            return null;
        int leaf = b.leaves().indexOf(mine.getId());
        if (leaf < 0)
            return null;
        boolean[] occupied = occupied(b);
        for (int p : TournamentBrackets.openMatches(occupied, b.winners())) {
            int a = TournamentBrackets.teamAt(occupied, b.winners(), 2 * p + 1), c = TournamentBrackets.teamAt(occupied, b.winners(), 2 * p + 2);
            if (a != leaf && c != leaf)
                continue;
            TournamentMatch row = row(t, isFinal, group, p);
            if (row == null)
                return null;
            long opponent = b.leaves().get(a == leaf ? c : a);
            if (row.getEntryA() == null) {
                row.setEntryA(b.leaves().get(a));
                row.setEntryB(b.leaves().get(c));
                matchRepository.save(row);
            }
            return new MyMatch(row.getId(), isFinal, group, p, mine.getId(), opponent, row.getRoomId() == null ? 0 : row.getRoomId());
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Long matchId(Tournament t, boolean isFinal, int group, int node) {
        if (t == null || node < 0)
            return null;
        boolean finalBracket = isFinal || !Boolean.TRUE.equals(t.getHasQualifying());
        TournamentMatch m = row(t, finalBracket, finalBracket ? 0 : group, node);
        return m == null ? null : m.getId();
    }

    private TournamentMatch row(Tournament t, boolean isFinal, int group, int slot) {
        for (TournamentMatch m : matchRepository.findAllByTournamentAndIsFinalAndGroupIndexOrderBySlotAsc(t, isFinal, (byte) group))
            if (m.getSlot() == slot) return m;
        return null;
    }

    @Override
    @Transactional
    public void setRoom(long matchId, short roomId) {
        matchRepository.findById(matchId).ifPresent(m -> {
            m.setRoomId(roomId);
            matchRepository.save(m);
        });
    }

    @Override
    @Transactional
    public Recorded record(long matchId, long winnerPlayerId, byte winnerScore, byte loserScore) {
        return record(matchId, winnerPlayerId, winnerScore, loserScore, TournamentBrackets.HOW_NORMAL);
    }

    @Override
    @Transactional
    public Recorded record(long matchId, long winnerPlayerId, byte winnerScore, byte loserScore, byte howWin) {
        TournamentMatch m = matchRepository.findById(matchId).orElse(null);
        Recorded no = new Recorded(false, false, List.of());
        if (m == null || m.getWinnerEntry() != null || m.getEntryA() == null || m.getEntryB() == null)
            return no;
        TournamentEntry a = entryRepository.findById(m.getEntryA()).orElse(null), b = entryRepository.findById(m.getEntryB()).orElse(null);
        if (a == null || b == null)
            return no;
        // A club team wins through either member (notes-v401).
        TournamentEntry winner = plays(a, winnerPlayerId) ? a : plays(b, winnerPlayerId) ? b : null;
        if (winner == null)
            return no;
        TournamentEntry loser = winner == a ? b : a;
        m.setWinnerEntry(winner.getId());
        m.setHowWin(howWin);
        m.setScoreA(winner == a ? winnerScore : loserScore);
        m.setScoreB(winner == a ? loserScore : winnerScore);
        m.setRoomId((short) 0);
        matchRepository.save(m);
        // A qualifying group final's loser goes to the final as the group's runner-up (finalLeaves), so only other losers drop.
        if (Boolean.TRUE.equals(m.getIsFinal()) || m.getSlot() != 0) {
            loser.setState((byte) 3);
            entryRepository.save(loser);
        }
        if (Boolean.TRUE.equals(m.getIsFinal()) && m.getSlot() == 0) {
            winner.setState((byte) 4);
            entryRepository.save(winner);
            // A club team's partner gets the rewards too (placed in the pocket; only the reporting member's are sent live) [I].
            long partner = winner.getPartnerId() == null ? 0 : winner.getPlayerId() != null && winner.getPlayerId() == winnerPlayerId ? winner.getPartnerId() : winner.getPlayerId();
            if (partner > 0 && partner != winnerPlayerId)
                giveRewards(m.getTournament(), partner);
            return new Recorded(true, true, giveRewards(m.getTournament(), winnerPlayerId));
        }
        return new Recorded(true, false, List.of());
    }

    private static boolean plays(TournamentEntry e, long playerId) {
        return e.getPlayerId() != null && e.getPlayerId() == playerId || e.getPartnerId() != null && e.getPartnerId() == playerId;
    }

    private List<com.jftse.entities.database.model.pocket.PlayerPocket> giveRewards(Tournament t, long playerId) {
        List<com.jftse.entities.database.model.pocket.PlayerPocket> out = new ArrayList<>();
        for (int[] pair : TournamentService.rewards(t.getRewards())) {
            if (pair[0] <= 0)
                continue;
            com.jftse.entities.database.model.item.Product product = productService.findProductByProductItemIndex(pair[0]);
            if (product == null)
                continue;
            int quantity = switch (pair[1]) {
                case 1 -> product.getUse1();
                case 2 -> product.getUse2();
                default -> product.getUse0() == 0 ? 1 : product.getUse0();
            };
            out.addAll(inventoryService.addItem(playerId, product.getProductIndex(), Math.max(1, quantity), List.of()));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public String name(Long entryId) {
        TournamentEntry e = entryId == null ? null : entryRepository.findById(entryId).orElse(null);
        Player p = e == null || e.getPlayerId() == null ? null : playerRepository.findById(e.getPlayerId()).orElse(null);
        return p == null || p.getName() == null ? "" : p.getName();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> names(Bracket bracket) {
        List<String> out = new ArrayList<>();
        for (Long entryId : bracket.leaves()) {
            String name = "";
            if (entryId != null) {
                TournamentEntry e = entryRepository.findById(entryId).orElse(null);
                Player p = e == null || e.getPlayerId() == null ? null : playerRepository.findById(e.getPlayerId()).orElse(null);
                name = p == null || p.getName() == null ? "" : p.getName();
            }
            out.add(name);
        }
        return out;
    }

    private String playerName(Long playerId) {
        Player p = playerId == null ? null : playerRepository.findById(playerId).orElse(null);
        return p == null || p.getName() == null ? "" : p.getName();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String[]> teamNames(Bracket bracket) {
        List<String[]> out = new ArrayList<>();
        for (Long entryId : bracket.leaves()) {
            TournamentEntry e = entryId == null ? null : entryRepository.findById(entryId).orElse(null);
            if (e == null) {
                out.add(new String[] {"", "", ""});
                continue;
            }
            String club = e.getGuildId() == null ? "" : guildRepository.findById(e.getGuildId()).map(g -> g.getName() == null ? "" : g.getName()).orElse("");
            out.add(new String[] {club, playerName(e.getPlayerId()), playerName(e.getPartnerId())});
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public long[] players(long entryId) {
        TournamentEntry e = entryRepository.findById(entryId).orElse(null);
        if (e == null || e.getPlayerId() == null)
            return null;
        return new long[] {e.getPlayerId(), e.getPartnerId() == null ? 0 : e.getPartnerId()};
    }
}
