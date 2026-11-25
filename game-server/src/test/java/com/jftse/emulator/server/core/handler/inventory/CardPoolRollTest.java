package com.jftse.emulator.server.core.handler.inventory;

import com.jftse.entities.database.model.card.CardPool;
import com.jftse.server.core.service.impl.CardPoolServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** notes-v175: the weighted card pool roll (uniform seed for the Normal pack, item 47). */
class CardPoolRollTest {
    private static CardPool row(int index, Integer weight) { CardPool r = new CardPool(); r.setPackItemIndex(47); r.setItemIndex(index); r.setWeight(weight); return r; }

    @Test void pickIsWeightedAndSkipsNonPositive() {
        List<CardPool> uniform = List.of(row(1, 1), row(4, 1), row(7, 1));
        assertEquals(1, CardPoolServiceImpl.pick(uniform, 0.0));
        assertEquals(4, CardPoolServiceImpl.pick(uniform, 0.34));
        assertEquals(7, CardPoolServiceImpl.pick(uniform, 0.99));
        List<CardPool> weighted = List.of(row(1, 0), row(4, 3), row(7, null), row(10, 1));
        assertEquals(4, CardPoolServiceImpl.pick(weighted, 0.0));
        assertEquals(4, CardPoolServiceImpl.pick(weighted, 0.74));
        assertEquals(10, CardPoolServiceImpl.pick(weighted, 0.76));
        assertEquals(-1, CardPoolServiceImpl.pick(List.of(row(1, 0)), 0.5));
        assertEquals(-1, CardPoolServiceImpl.pick(List.of(), 0.5));
    }
}
