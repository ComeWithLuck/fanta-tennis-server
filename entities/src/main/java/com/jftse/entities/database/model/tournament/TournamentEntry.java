package com.jftse.entities.database.model.tournament;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

// One individual application (C2S 0x26B1 / 0x26B3, notes-v176). state is the client's "my tournament" state
// (MY_TOURNEY_STATE_*, 5c7f95): 1 applied, 2 entered, 3 dropped, 4 winner; groupIndex is the qualifying group sent in 0x26BF.
@Getter
@Setter
@Entity
public class TournamentEntry extends AbstractBaseModel {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;
    private Long accountId;
    private Long playerId;
    private Byte state = 1;
    private Byte groupIndex = 0;
    // A club (guild) tournament team (C2S 0x26B5, notes-v373 / v375 (ai-phases-rest)): the master's club, playerId is the first member and
    // partnerId the second; accountId stays null. Both are null for an individual entry.
    private Long guildId;
    private Long partnerId;
}
