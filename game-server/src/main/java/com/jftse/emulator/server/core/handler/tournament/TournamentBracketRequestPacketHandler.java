package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentBracketPacket;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentResultPacket;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.TournamentBracketService;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentBracket;

import java.util.Date;

// C2S 0x26C0 {u32 tournamentId, u8 isFinal, u8 group} (Gui_TournamentMenu brackets 5c7041 / Gui_TournamentBracket paging and its
// 10 s refresh 5c5640) -> the bracket teams (S2CTournamentBracketPacket); no bracket yet -> MSG_TOURNEY_MATCH_MADE_NOT_YET.
// Bracket rules: TournamentBracketService [I] (notes-v184).
@PacketId(CMSGTournamentBracket.PACKET_ID)
public class TournamentBracketRequestPacketHandler implements PacketHandler<FTConnection, CMSGTournamentBracket> {
    @Override
    public void handle(FTConnection connection, CMSGTournamentBracket packet) {
        if (!connection.getClient().hasPlayer())
            return;
        ServiceManager sm = ServiceManager.getInstance();
        Tournament t = sm.getTournamentService().find(Integer.toUnsignedLong(packet.getTournamentId()));
        boolean isFinal = packet.getIsFinal() != 0;
        int group = Byte.toUnsignedInt(packet.getGroup());
        TournamentBracketService.Bracket b = t == null ? null : sm.getTournamentBracketService().bracket(t, isFinal, group, new Date());
        if (b == null) {
            connection.sendTCP(new S2CTournamentResultPacket(S2CTournamentResultPacket.BRACKET_TEAMS, (byte) 1));
            return;
        }
        int id = t.getId().intValue();
        // A club tournament (entry type 0) sends its teams as club / member / partner (notes-v401).
        if (t.getEntryType() != null && t.getEntryType() == 0)
            connection.sendTCP(S2CTournamentBracketPacket.clubTeams(id, isFinal, group, sm.getTournamentBracketService().teamNames(b)));
        else
            connection.sendTCP(S2CTournamentBracketPacket.teams(id, isFinal, group, sm.getTournamentBracketService().names(b)));
    }
}
