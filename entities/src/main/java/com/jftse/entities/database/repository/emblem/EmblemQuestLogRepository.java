package com.jftse.entities.database.repository.emblem;

import com.jftse.entities.database.model.emblem.EmblemQuestLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmblemQuestLogRepository extends JpaRepository<EmblemQuestLog, Long> {
    List<EmblemQuestLog> findAllByPlayerIdOrderByIdAsc(Long playerId);

    Optional<EmblemQuestLog> findByPlayerIdAndQuestIndex(Long playerId, Integer questIndex);
}
