package com.jftse.entities.database.repository.emblem;

import com.jftse.entities.database.model.emblem.EmblemSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmblemSlotRepository extends JpaRepository<EmblemSlot, Long> {
    Optional<EmblemSlot> findByPlayerId(Long playerId);
}
