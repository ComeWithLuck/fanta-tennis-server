package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentResultPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.TournamentService;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentCancel;

import java.util.Date;

// C2S 0x26B3 {u32 tournamentId} (btApply -> MSG_TOURNEY_ASK_CANCEL_SINGLE yes, 5c69bd; waited 20 s) -> S2C 0x26B4 {u8}.
// notes-v176.
@PacketId(CMSGTournamentCancel.PACKET_ID)
public class TournamentCancelRequestPacketHandler implements PacketHandler<FTConnection, CMSGTournamentCancel> {
    private final TournamentService tournamentService;

    public TournamentCancelRequestPacketHandler() {
        tournamentService = ServiceManager.getInstance().getTournamentService();
    }

    @Override
    public void handle(FTConnection connection, CMSGTournamentCancel packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        Tournament tournament = tournamentService.find(Integer.toUnsignedLong(packet.getTournamentId()));
        byte result = tournamentService.cancel(tournament, client.getPlayer().getId(), new Date());
        connection.sendTCP(new S2CTournamentResultPacket(S2CTournamentResultPacket.CANCEL, result));
    }
}
