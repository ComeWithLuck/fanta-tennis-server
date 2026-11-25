package com.jftse.entities.database.model.tournament;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import java.util.Date;

// Tournament list / detail rows (S2C 0x26B0 record, client parser 5ca470; 0x26AE header 5c9040; notes-v176, user-approved
// 2026-11-11 "Tournament browse/apply"). entryType is the wire byte: 0 = guild (team) entry, 1 = individual entry (the
// client keeps entryType == 0 as its guild flag, 5ca470 / 5c88f0); gameMode 0 basic / 1 battle (5c896f). The four periods are
// the client's +0x30 apply start, +0x38 apply end, +0x40 total start, +0x48 total end (GUI_TOURNEY_TIME_APPLY / _TOTAL in
// 5c88f0). state: null = derived from the periods (TournamentService), else forced (7 suspended, 8 canceled ...).
// rewards: up to five "productIndex:useOption" pairs, comma separated (cTournamentRewardItem shows the first four, 5c0930).
// maxTeams (+0x74) / qualifyingGroups (+0x78) are sent as stored; brackets are not served yet [I].
@Getter
@Setter
@Entity
public class Tournament extends AbstractBaseModel {
    @Column(length = 64, nullable = false)
    private String title;
    private Byte entryType = 1;
    private Byte gameMode = 0;
    private Date applyStart;
    private Date applyEnd;
    private Date totalStart;
    private Date totalEnd;
    private Date roundTime;
    private Byte state;
    @Column(columnDefinition = "bit(1) DEFAULT 0")
    private Boolean hasQualifying = false;
    private Integer maxTeams = 0;
    private Integer qualifyingGroups = 0;
    @Column(length = 128)
    private String rewards;
    @Column(columnDefinition = "bit(1) DEFAULT 1")
    private Boolean enabled = true;
}
