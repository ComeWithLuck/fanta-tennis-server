package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentApplyAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.TournamentService;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentApply;

import java.util.Date;

// C2S 0x26B1 {u32 tournamentId} (btApply -> MSG_TOURNEY_ASK_APPLY_SINGLE yes, 5c692a; the client waits 20 s) -> S2C 0x26B2.
// Codes: TournamentService. -5 carries the title of the tournament the character is already in. notes-v176.
@PacketId(CMSGTournamentApply.PACKET_ID)
public class TournamentApplyRequestPacketHandler implements PacketHandler<FTConnection, CMSGTournamentApply> {
    private final TournamentService tournamentService;

    public TournamentApplyRequestPacketHandler() {
        tournamentService = ServiceManager.getInstance().getTournamentService();
    }

    @Override
    public void handle(FTConnection connection, CMSGTournamentApply packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        FTPlayer player = client.getPlayer();
        Date now = new Date();
        Tournament tournament = tournamentService.find(Integer.toUnsignedLong(packet.getTournamentId()));
        byte result = tournamentService.apply(tournament, player.getAccountId(), player.getId(), now);
        String title = null;
        if (result == TournamentService.APPLY_ALREADY) {
            Tournament other = tournamentService.activeOther(tournament, player.getId(), now);
            title = other != null ? other.getTitle() : tournament.getTitle();
        }
        connection.sendTCP(new S2CTournamentApplyAnswerPacket(result, title));
    }
}
