package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.guild.GuildMember;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.GuildMemberService;
import com.jftse.server.core.service.TournamentService;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentTeamListAnswerPacket;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentTeamList;

import java.util.List;

// C2S 0x2038 {i32 tournamentId} (Gui_TournamentMenu btApply, the club master of a club tournament, 5c6ed1; waited 20 s)
// -> S2C 0x2039 (the club's teams). Not the master / no club / not a club tournament -> result -1 (the popup still opens,
// empty). notes-v373.
@PacketId(CMSGTournamentTeamList.PACKET_ID)
public class TournamentTeamListRequestPacketHandler implements PacketHandler<FTConnection, CMSGTournamentTeamList> {
    private final TournamentService tournamentService;
    private final GuildMemberService guildMemberService;

    public TournamentTeamListRequestPacketHandler() {
        tournamentService = ServiceManager.getInstance().getTournamentService();
        guildMemberService = ServiceManager.getInstance().getGuildMemberService();
    }

    @Override
    public void handle(FTConnection connection, CMSGTournamentTeamList packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        FTPlayer player = client.getPlayer();
        Tournament tournament = tournamentService.find(Integer.toUnsignedLong(packet.getTournamentId()));
        GuildMember master = guildMemberService.getByPlayer(player.getId());
        if (tournament == null || tournament.getEntryType() == null || tournament.getEntryType() != 0 || master == null
                || Boolean.TRUE.equals(master.getWaitingForApproval()) || master.getMemberRank() == null || master.getMemberRank() != 3) {
            connection.sendTCP(new S2CTournamentTeamListAnswerPacket((byte) -1, List.of()));
            return;
        }
        connection.sendTCP(new S2CTournamentTeamListAnswerPacket((byte) 0, tournamentService.teams(tournament, master.getGuild().getId())));
    }
}
