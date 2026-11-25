package com.jftse.emulator.server.core.packets.tournament;

import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.server.core.protocol.Packet;

// S2C 0x26AE (Gui_TournamentMenu 5c6b60): u8 result; 0 -> u32 tournamentId + the record header (5c9040);
// any other result -> MSG_TOURNEY_FAILED_TO_JOIN "Tournament does not exist." notes-v176.
public class S2CTournamentDetailAnswerPacket extends Packet {
    public S2CTournamentDetailAnswerPacket(Tournament tournament, byte state) {
        super((char) 0x26AE);

        if (tournament == null) {
            this.write((byte) 1);
            return;
        }
        this.write((byte) 0);
        this.write(tournament.getId().intValue());
        TournamentRecordWriter.header(this, tournament, state);
    }
}
