package com.jftse.entities.database.model.card;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;

// The cards an unopened card pack can give (notes-v175; user-approved 2026-11-11). One row per (pack item, CARD itemIndex) with a
// relative weight. The native server's pool and odds are unknown [I]; the seed (DDL) gives the Normal pack (Item_Special 47) the
// 13 "1st Class" cards with equal weights [I: Normal = 1st Class].
@Getter
@Setter
@Entity
public class CardPool extends AbstractBaseModel {
    private Integer packItemIndex;
    private Integer itemIndex;
    private Integer weight = 1;
}
