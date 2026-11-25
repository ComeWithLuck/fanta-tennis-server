package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentListAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.TournamentService;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentList;

import java.util.Date;
import java.util.List;

// C2S 0x26AF {u32 x} (Gui_Tournament activate / next page, 5cb7b0): x = 0 for the first page (the client clears its
// list), else the id of the last loaded tournament; answered with the next five enabled tournaments by id (S2C 0x26B0)
// [I: the native batch size]. notes-v176.
@PacketId(CMSGTournamentList.PACKET_ID)
public class TournamentListRequestPacketHandler implements PacketHandler<FTConnection, CMSGTournamentList> {
    private final TournamentService tournamentService;

    public TournamentListRequestPacketHandler() {
        tournamentService = ServiceManager.getInstance().getTournamentService();
    }

    @Override
    public void handle(FTConnection connection, CMSGTournamentList packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        Date now = new Date();
        List<Tournament> rows = tournamentService.listAfter(Integer.toUnsignedLong(packet.getLastTournamentId()));
        connection.sendTCP(new S2CTournamentListAnswerPacket(rows, rows.stream().map(t -> tournamentService.state(t, now)).toList()));
    }
}
