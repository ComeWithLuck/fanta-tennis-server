package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentDetailAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.TournamentService;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentDetail;

import java.util.Date;

// C2S 0x26AD {u32 tournamentId} (Gui_TournamentMenu activate / btRefresh, 5c666f; the client waits 20 s) -> S2C 0x26AE.
// notes-v176.
@PacketId(CMSGTournamentDetail.PACKET_ID)
public class TournamentDetailRequestPacketHandler implements PacketHandler<FTConnection, CMSGTournamentDetail> {
    private final TournamentService tournamentService;

    public TournamentDetailRequestPacketHandler() {
        tournamentService = ServiceManager.getInstance().getTournamentService();
    }

    @Override
    public void handle(FTConnection connection, CMSGTournamentDetail packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        Tournament tournament = tournamentService.find(Integer.toUnsignedLong(packet.getTournamentId()));
        byte state = tournament == null ? 0 : tournamentService.state(tournament, new Date());
        connection.sendTCP(new S2CTournamentDetailAnswerPacket(tournament, state));
    }
}
