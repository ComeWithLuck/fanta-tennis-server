package com.jftse.entities.database.model.emblem;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;

// A character's emblem / quest log entry (S2C 0x226B, client parse 0x4608c0; notes-v176, user-approved 2026-11-11).
// inProgress: accepted and not finished. completeCount > 0: completed at least once (client: slot state 4, owned emblem for
// index 1000..1999, non-repeatable gate, prerequisites). base1..4: the counter value at accept for ConditionType 1..4 - the client
// shows progress = counter - base for non-Total types (0x45e1f2..) [I: the native server's rule].
@Getter
@Setter
@Entity
public class EmblemQuestLog extends AbstractBaseModel {
    private Long playerId;
    private Integer questIndex;
    private Boolean inProgress = false;
    private Integer completeCount = 0;
    private Integer base1 = 0;
    private Integer base2 = 0;
    private Integer base3 = 0;
    private Integer base4 = 0;
}
