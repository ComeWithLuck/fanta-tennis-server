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
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentTeamApplyAnswerPacket;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentTeamApply;

import java.util.Date;

// C2S 0x26B5 {i32 tournamentId, i32 player1, i16, i32 player2, i16} (GuiPop_TourneyApplyTeam btAddTeam ->
// MSG_TOURNEY_ASK_APPLY_DOUBLE yes, 5c3f70; waited 20 s) -> S2C 0x26B6. The shorts are the club member rows' +0x2c (the
// trailing short 0 of S2C 0x201B) and are ignored. notes-v373.
@PacketId(CMSGTournamentTeamApply.PACKET_ID)
public class TournamentTeamApplyRequestPacketHandler implements PacketHandler<FTConnection, CMSGTournamentTeamApply> {
    private final TournamentService tournamentService;
    private final GuildMemberService guildMemberService;

    public TournamentTeamApplyRequestPacketHandler() {
        tournamentService = ServiceManager.getInstance().getTournamentService();
        guildMemberService = ServiceManager.getInstance().getGuildMemberService();
    }

    @Override
    public void handle(FTConnection connection, CMSGTournamentTeamApply packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        FTPlayer player = client.getPlayer();
        Tournament tournament = tournamentService.find(Integer.toUnsignedLong(packet.getTournamentId()));
        GuildMember master = guildMemberService.getByPlayer(player.getId());
        if (master == null || Boolean.TRUE.equals(master.getWaitingForApproval()) || master.getMemberRank() == null || master.getMemberRank() != 3) {
            connection.sendTCP(new S2CTournamentTeamApplyAnswerPacket(TournamentService.TEAM_NOT_MASTER, 0));
            return;
        }
        long guildId = master.getGuild().getId();
        long p1 = Integer.toUnsignedLong(packet.getPlayer1()), p2 = Integer.toUnsignedLong(packet.getPlayer2());
        if (!member(p1, guildId) || !member(p2, guildId)) {
            connection.sendTCP(new S2CTournamentTeamApplyAnswerPacket(TournamentService.TEAM_NO_CHARACTER, 0));
            return;
        }
        long[] teamId = new long[1];
        byte result = tournamentService.applyTeam(tournament, guildId, p1, p2, new Date(), teamId);
        connection.sendTCP(new S2CTournamentTeamApplyAnswerPacket(result, teamId[0]));
    }

    private boolean member(long playerId, long guildId) {
        GuildMember m = guildMemberService.getByPlayer(playerId);
        return m != null && !Boolean.TRUE.equals(m.getWaitingForApproval()) && m.getGuild() != null && m.getGuild().getId() == guildId;
    }
}
