package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.entities.database.model.tournament.TournamentEntry;
import com.jftse.entities.database.repository.tournament.TournamentEntryRepository;
import com.jftse.entities.database.repository.tournament.TournamentRepository;
import com.jftse.server.core.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {
    private final TournamentRepository tournamentRepository;
    private final TournamentEntryRepository tournamentEntryRepository;

    @Override
    public List<Tournament> listAfter(long lastTournamentId) {
        return tournamentRepository.findTop5ByEnabledTrueAndIdGreaterThanOrderByIdAsc(Math.max(0L, lastTournamentId));
    }

    @Override
    public Tournament find(long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId).orElse(null);
        return tournament != null && Boolean.TRUE.equals(tournament.getEnabled()) ? tournament : null;
    }

    @Override
    public byte state(Tournament t, Date now) {
        if (t.getState() != null)
            return t.getState();
        boolean qualifying = Boolean.TRUE.equals(t.getHasQualifying());
        if (t.getApplyStart() != null && now.before(t.getApplyStart()))
            return 0;
        if (t.getApplyEnd() == null || !now.after(t.getApplyEnd()))
            return 1;
        if (t.getTotalStart() != null && now.before(t.getTotalStart()))
            return (byte) (qualifying ? 2 : 4);
        if (t.getTotalEnd() == null || !now.after(t.getTotalEnd()))
            return (byte) (qualifying ? 3 : 5);
        return 6;
    }

    @Override
    public TournamentEntry entry(Tournament tournament, long playerId) {
        // A club team's partner finds the team's entry too (notes-v401).
        return tournamentEntryRepository.findByTournamentAndPlayerId(tournament, playerId)
                .or(() -> tournamentEntryRepository.findByTournamentAndPartnerId(tournament, playerId)).orElse(null);
    }

    // Read-only transaction: the entries' tournaments are lazy proxies; the handler also calls this outside apply() for the
    // -5 title, so the proxy is resolved here (a LazyInitializationException otherwise, live 2026-11-11).
    @Override
    @Transactional(readOnly = true)
    public Tournament activeOther(Tournament tournament, long playerId, Date now) {
        java.util.List<TournamentEntry> mine = new java.util.ArrayList<>(tournamentEntryRepository.findAllByPlayerId(playerId));
        mine.addAll(tournamentEntryRepository.findAllByPartnerId(playerId));
        for (TournamentEntry e : mine) {
            Tournament other = e.getTournament();
            if (other == null || (tournament != null && other.getId().equals(tournament.getId())))
                continue;
            if (Boolean.TRUE.equals(other.getEnabled()) && state(other, now) < 6)
                return (Tournament) Hibernate.unproxy(other);
        }
        return null;
    }

    @Override
    @Transactional
    public byte apply(Tournament t, long accountId, long playerId, Date now) {
        if (t == null)
            return APPLY_NO_TOURNAMENT;
        if (t.getEntryType() == null || t.getEntryType() == 0)
            return APPLY_TEAM_ONLY;
        if (state(t, now) != 1)
            return APPLY_NOT_PERIOD;
        if (entry(t, playerId) != null)
            return APPLY_ALREADY;
        if (tournamentEntryRepository.existsByTournamentAndAccountIdAndPlayerIdNot(t, accountId, playerId))
            return APPLY_ACCOUNT;
        if (activeOther(t, playerId, now) != null)
            return APPLY_ALREADY;
        // maxTeams is the bracket size of one group (client record +0x74); with qualifying groups the capacity is maxTeams * groups.
        int capacity = t.getMaxTeams() == null ? 0 : t.getMaxTeams() * (Boolean.TRUE.equals(t.getHasQualifying()) ? Math.max(1, t.getQualifyingGroups() == null ? 0 : t.getQualifyingGroups()) : 1);
        if (capacity > 0 && tournamentEntryRepository.countByTournament(t) >= capacity)
            return APPLY_FULL;
        TournamentEntry e = new TournamentEntry();
        e.setTournament(t);
        e.setAccountId(accountId);
        e.setPlayerId(playerId);
        e.setState((byte) 1);
        e.setGroupIndex((byte) 0);
        tournamentEntryRepository.save(e);
        return APPLY_OK;
    }

    @Override
    @Transactional
    public byte cancel(Tournament t, long playerId, Date now) {
        TournamentEntry e = t == null ? null : entry(t, playerId);
        if (e == null)
            return CANCEL_NOT_APPLIED;
        if (state(t, now) != 1)
            return CANCEL_NOT_PERIOD;
        tournamentEntryRepository.delete(e);
        return CANCEL_OK;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentEntry> teams(Tournament t, long guildId) {
        return t == null ? List.of() : tournamentEntryRepository.findAllByTournamentAndGuildIdOrderByIdAsc(t, guildId);
    }

    @Override
    @Transactional
    public byte applyTeam(Tournament t, long guildId, long player1, long player2, Date now, long[] teamId) {
        if (t == null)
            return TEAM_NO_TOURNAMENT;
        if (t.getEntryType() == null || t.getEntryType() != 0)
            return TEAM_NOT_CLUB;
        if (state(t, now) != 1)
            return TEAM_NOT_PERIOD;
        if (player1 == player2)
            return TEAM_NO_CHARACTER;
        List<TournamentEntry> all = tournamentEntryRepository.findAllByTournamentOrderByIdAsc(t);
        for (TournamentEntry e : all) {
            for (Long id : new Long[] {e.getPlayerId(), e.getPartnerId()})
                if (id != null && (id == player1 || id == player2))
                    return TEAM_ALREADY;
        }
        if (activeOther(t, player1, now) != null || activeOther(t, player2, now) != null)
            return TEAM_ALREADY;
        if (all.stream().filter(e -> e.getGuildId() != null && e.getGuildId() == guildId).count() >= TEAMS_PER_CLUB)
            return TEAM_CLUB_LIMIT;
        int capacity = t.getMaxTeams() == null ? 0 : t.getMaxTeams() * (Boolean.TRUE.equals(t.getHasQualifying()) ? Math.max(1, t.getQualifyingGroups() == null ? 0 : t.getQualifyingGroups()) : 1);
        if (capacity > 0 && all.size() >= capacity)
            return TEAM_FULL;
        TournamentEntry e = new TournamentEntry();
        e.setTournament(t);
        e.setGuildId(guildId);
        e.setPlayerId(player1);
        e.setPartnerId(player2);
        e.setState((byte) 1);
        e.setGroupIndex((byte) 0);
        e = tournamentEntryRepository.save(e);
        teamId[0] = e.getId();
        return TEAM_OK;
    }

    @Override
    @Transactional
    public byte cancelTeam(Tournament t, long guildId, long teamId, Date now) {
        TournamentEntry e = t == null ? null : tournamentEntryRepository.findById(teamId).orElse(null);
        if (e == null || e.getGuildId() == null || e.getGuildId() != guildId || !e.getTournament().getId().equals(t.getId()))
            return CANCEL_NOT_APPLIED;
        if (state(t, now) != 1)
            return CANCEL_NOT_PERIOD;
        tournamentEntryRepository.delete(e);
        return CANCEL_OK;
    }
}
