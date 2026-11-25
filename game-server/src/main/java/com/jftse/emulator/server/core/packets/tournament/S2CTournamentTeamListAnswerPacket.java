package com.jftse.emulator.server.core.packets.tournament;

import com.jftse.entities.database.model.tournament.TournamentEntry;
import com.jftse.server.core.protocol.Packet;

import java.util.List;

// S2C 0x2039 (the record's 5ca2c0, notes-v373): i8 result (non-zero: no list); i8 count; count x {i32 teamId, i32 player1,
// i32 player2}. The client looks both players up in its club member list (5ca1e0 -> 46faf0) and drops the list if one is missing.
public class S2CTournamentTeamListAnswerPacket extends Packet {
    public S2CTournamentTeamListAnswerPacket(byte result, List<TournamentEntry> teams) {
        super((char) 0x2039);

        this.write(result);
        if (result != 0)
            return;
        int count = Math.min(255, teams.size());
        this.write((byte) count);
        for (int i = 0; i < count; i++) {
            TournamentEntry e = teams.get(i);
            this.write(e.getId().intValue());
            this.write(e.getPlayerId() == null ? 0 : e.getPlayerId().intValue());
            this.write(e.getPartnerId() == null ? 0 : e.getPartnerId().intValue());
        }
    }
}
