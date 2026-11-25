package com.jftse.entities.database.repository.tournament;

import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.entities.database.model.tournament.TournamentEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TournamentEntryRepository extends JpaRepository<TournamentEntry, Long> {
    Optional<TournamentEntry> findByTournamentAndPlayerId(Tournament tournament, Long playerId);
    boolean existsByTournamentAndAccountIdAndPlayerIdNot(Tournament tournament, Long accountId, Long playerId);
    long countByTournament(Tournament tournament);
    List<TournamentEntry> findAllByPlayerId(Long playerId);
    // A club team is one entry: the applying member (playerId) and the partner (partnerId) (notes-v401).
    Optional<TournamentEntry> findByTournamentAndPartnerId(Tournament tournament, Long partnerId);
    List<TournamentEntry> findAllByPartnerId(Long partnerId);
    List<TournamentEntry> findAllByTournamentOrderByIdAsc(Tournament tournament);
    List<TournamentEntry> findAllByTournamentAndGuildIdOrderByIdAsc(Tournament tournament, Long guildId);
}
