package com.jftse.entities.database.model.tournament;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

// One bracket slot of a tournament (notes-v184; the bracket the client reads with 0x26C1 / 0x26C3). isFinal + groupIndex name
// the bracket (qualifying group n, or the final); slot is the client's slot index (0 .. 2 * teams - 2). A leaf slot holds one
// entry (entryA); an inner slot is a match between the winners of its two child slots (entryA / entryB once known) [I: the
// native server's bracket rules are not in the client]. winnerEntryId / howWin (GUI_TOURNEY_HOW_WIN_0..4) / scores are the
// result; roomId the room the server made for it (0 none).
@Getter
@Setter
@Entity
public class TournamentMatch extends AbstractBaseModel {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;
    private Boolean isFinal = false;
    private Byte groupIndex = 0;
    private Integer slot = 0;
    private Long entryA;
    private Long entryB;
    private Long winnerEntry;
    private Byte howWin = 0;
    private Byte scoreA = 0;
    private Byte scoreB = 0;
    private Short roomId = 0;
}
