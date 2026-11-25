package com.jftse.entities.database.model.emblem;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;

// A character's four equipped emblems (quest indexes, 0 = empty): client char +0x30a..+0x310, set by S2C 0x2297 and written in the
// room player record (0x494130) and the 0x139D user info. notes-v185 E3, user-approved 2026-11-11.
@Getter
@Setter
@Entity
public class EmblemSlot extends AbstractBaseModel {
    private Long playerId;
    private Integer slot1 = 0;
    private Integer slot2 = 0;
    private Integer slot3 = 0;
    private Integer slot4 = 0;
}
