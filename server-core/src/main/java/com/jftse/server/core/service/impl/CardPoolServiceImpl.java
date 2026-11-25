package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.card.CardPool;
import com.jftse.entities.database.repository.card.CardPoolRepository;
import com.jftse.server.core.service.CardPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class CardPoolServiceImpl implements CardPoolService {
    private final CardPoolRepository cardPoolRepository;

    @Override
    public int roll(int packItemIndex) {
        return pick(cardPoolRepository.findAllByPackItemIndex(packItemIndex), ThreadLocalRandom.current().nextDouble());
    }

    // r in [0, 1): the row whose cumulative weight passes r * total; rows with weight <= 0 never win.
    public static int pick(List<CardPool> rows, double r) {
        long total = 0;
        for (CardPool row : rows)
            if (row.getWeight() != null && row.getWeight() > 0) total += row.getWeight();
        if (total <= 0)
            return -1;
        double at = r * total;
        long acc = 0;
        for (CardPool row : rows) {
            if (row.getWeight() == null || row.getWeight() <= 0) continue;
            acc += row.getWeight();
            if (at < acc) return row.getItemIndex();
        }
        return -1;
    }
}
