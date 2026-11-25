package com.jftse.entities.database.repository.tournament;

import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.entities.database.model.tournament.TournamentMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentMatchRepository extends JpaRepository<TournamentMatch, Long> {
    List<TournamentMatch> findAllByTournamentAndIsFinalAndGroupIndexOrderBySlotAsc(Tournament tournament, Boolean isFinal, Byte groupIndex);
}
