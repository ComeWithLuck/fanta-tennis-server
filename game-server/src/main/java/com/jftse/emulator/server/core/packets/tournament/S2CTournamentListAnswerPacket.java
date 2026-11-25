package com.jftse.emulator.server.core.packets.tournament;

import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

import java.util.List;

// S2C 0x26B0 (global handler 5cb9d0): u8 count, count x the list record (TournamentRecordWriter.record), then the client's
// Gui_Tournament refresh (scene event 0x33). notes-v176.
public class S2CTournamentListAnswerPacket extends Packet {
    public S2CTournamentListAnswerPacket(List<Tournament> tournaments, List<Byte> states) {
        super(PacketOperations.S2CTournamentListAnswer);

        this.write((byte) tournaments.size());
        for (int i = 0; i < tournaments.size(); i++)
            TournamentRecordWriter.record(this, tournaments.get(i), states.get(i));
    }
}
