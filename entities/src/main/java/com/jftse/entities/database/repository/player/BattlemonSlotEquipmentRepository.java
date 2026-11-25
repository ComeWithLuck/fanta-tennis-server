package com.jftse.entities.database.repository.player;

import com.jftse.entities.database.model.player.BattlemonSlotEquipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BattlemonSlotEquipmentRepository extends JpaRepository<BattlemonSlotEquipment, Long> {
    Optional<BattlemonSlotEquipment> findByPlayerId(Long playerId);
}
