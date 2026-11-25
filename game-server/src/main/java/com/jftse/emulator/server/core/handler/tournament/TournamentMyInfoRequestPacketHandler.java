package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentMyInfoAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.entities.database.model.tournament.TournamentEntry;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.TournamentService;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentMyInfo;

// C2S 0x26BE {u32 tournamentId} (sent after 0x26AE unless the state is 2 / 4, 5c673b; waited 20 s) -> S2C 0x26BF: the
// character's entry state and group; no entry -> state 0 (not applied); an unknown tournament -> -1. notes-v176.
@PacketId(CMSGTournamentMyInfo.PACKET_ID)
public class TournamentMyInfoRequestPacketHandler implements PacketHandler<FTConnection, CMSGTournamentMyInfo> {
    private final TournamentService tournamentService;

    public TournamentMyInfoRequestPacketHandler() {
        tournamentService = ServiceManager.getInstance().getTournamentService();
    }

    @Override
    public void handle(FTConnection connection, CMSGTournamentMyInfo packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        Tournament tournament = tournamentService.find(Integer.toUnsignedLong(packet.getTournamentId()));
        if (tournament == null) {
            connection.sendTCP(new S2CTournamentMyInfoAnswerPacket((byte) -1));
            return;
        }
        // Past the application period the brackets are made on first use (TournamentBracketService [I]): make them here too, so
        // the entry already carries "entered" and its group in this answer.
        if (tournamentService.state(tournament, new java.util.Date()) >= 2)
            ServiceManager.getInstance().getTournamentBracketService().bracket(tournament, !Boolean.TRUE.equals(tournament.getHasQualifying()), 0, new java.util.Date());
        TournamentEntry entry = tournamentService.entry(tournament, client.getPlayer().getId());
        byte state = entry == null || entry.getState() == null ? 0 : entry.getState();
        byte group = entry == null || entry.getGroupIndex() == null ? 0 : entry.getGroupIndex();
        var bracketService = ServiceManager.getInstance().getTournamentBracketService();
        var match = state == 2 ? bracketService.myMatch(tournament, client.getPlayer().getId(), new java.util.Date()) : null;
        String opponent = match == null ? "" : bracketService.name(match.opponentEntry());
        // The round of an entered player [I: the server has no scheduled rounds]: from the tournament's round time (else the
        // total start) to the total end - the tournament room shows this end as stRoundEndTime.
        java.util.Date roundStart = null, roundEnd = null;
        if (state == 2 && tournament.getTotalEnd() != null) {
            roundStart = tournament.getRoundTime() != null ? tournament.getRoundTime() : tournament.getTotalStart();
            roundEnd = tournament.getTotalEnd();
        }
        connection.sendTCP(new S2CTournamentMyInfoAnswerPacket(tournament.getId().intValue(), state, group, opponent, match != null, roundStart, roundEnd));
    }
}
