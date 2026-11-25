package com.jftse.entities.database.repository.tournament;

import com.jftse.entities.database.model.tournament.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    List<Tournament> findTop5ByEnabledTrueAndIdGreaterThanOrderByIdAsc(Long id);
}
