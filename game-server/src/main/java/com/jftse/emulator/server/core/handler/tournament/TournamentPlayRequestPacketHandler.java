package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentResultPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.entities.database.model.tournament.TournamentEntry;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.TournamentBracketService;
import com.jftse.server.core.service.TournamentService;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentPlay;

import java.util.Date;

// C2S 0x26C6 {u32 tournamentId} (btPlay, only with my state 2, 5c6fc8; not waited) -> S2C 0x26C7: -1 no such tournament
// (MSG_TOURNEY_FAILED_TO_JOIN), -2 not entered (_JOIN_02), -3 no open match / already in a room (_JOIN_03 "(code)") [I];
// 0 -> the match room (TournamentRooms, notes-v186).
@PacketId(CMSGTournamentPlay.PACKET_ID)
public class TournamentPlayRequestPacketHandler implements PacketHandler<FTConnection, CMSGTournamentPlay> {
    @Override
    public void handle(FTConnection connection, CMSGTournamentPlay packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        TournamentService ts = ServiceManager.getInstance().getTournamentService();
        Tournament tournament = ts.find(Integer.toUnsignedLong(packet.getTournamentId()));
        TournamentEntry entry = tournament == null ? null : ts.entry(tournament, client.getPlayer().getId());
        byte result;
        if (tournament == null) result = -1;
        else if (entry == null || entry.getState() == null || entry.getState() != 2) result = -2;
        else {
            TournamentBracketService.MyMatch match = ServiceManager.getInstance().getTournamentBracketService().myMatch(tournament, client.getPlayer().getId(), new Date());
            if (match != null) {
                TournamentRooms.play(connection, client, tournament, match);
                return;
            }
            result = -3;
        }
        connection.sendTCP(new S2CTournamentResultPacket(S2CTournamentResultPacket.PLAY, result));
    }
}
