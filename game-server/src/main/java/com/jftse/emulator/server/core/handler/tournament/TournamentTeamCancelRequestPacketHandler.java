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
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentTeamCancelAnswerPacket;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentTeamCancel;

import java.util.Date;

// C2S 0x26B7 {i32 tournamentId, i32 teamId} (GuiPop_TourneyApplyTeam btRemoveTeam -> MSG_TOURNEY_ASK_CANCEL_DOUBLE yes,
// 5c4045; waited 20 s) -> S2C 0x26B8 {i8}. Only the club master of that team's club. notes-v373.
@PacketId(CMSGTournamentTeamCancel.PACKET_ID)
public class TournamentTeamCancelRequestPacketHandler implements PacketHandler<FTConnection, CMSGTournamentTeamCancel> {
    private final TournamentService tournamentService;
    private final GuildMemberService guildMemberService;

    public TournamentTeamCancelRequestPacketHandler() {
        tournamentService = ServiceManager.getInstance().getTournamentService();
        guildMemberService = ServiceManager.getInstance().getGuildMemberService();
    }

    @Override
    public void handle(FTConnection connection, CMSGTournamentTeamCancel packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        FTPlayer player = client.getPlayer();
        Tournament tournament = tournamentService.find(Integer.toUnsignedLong(packet.getTournamentId()));
        GuildMember master = guildMemberService.getByPlayer(player.getId());
        if (master == null || Boolean.TRUE.equals(master.getWaitingForApproval()) || master.getMemberRank() == null || master.getMemberRank() != 3) {
            connection.sendTCP(new S2CTournamentTeamCancelAnswerPacket(TournamentService.CANCEL_NOT_APPLIED));
            return;
        }
        byte result = tournamentService.cancelTeam(tournament, master.getGuild().getId(), Integer.toUnsignedLong(packet.getTeamId()), new Date());
        connection.sendTCP(new S2CTournamentTeamCancelAnswerPacket(result));
    }
}
