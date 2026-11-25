package com.jftse.entities.database.repository.card;

import com.jftse.entities.database.model.card.CardPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardPoolRepository extends JpaRepository<CardPool, Long> {
    List<CardPool> findAllByPackItemIndex(Integer packItemIndex);
}
