package com.jftse.emulator.server.core.handler.guild;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.guild.GuildGoldUsage;
import com.jftse.entities.database.model.guild.GuildMember;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.GuildMemberService;
import com.jftse.server.core.service.GuildService;
import com.jftse.server.core.shared.packets.guild.CMSGGuildGoldData;
import com.jftse.server.core.shared.packets.guild.GuildGoldUseInfo;
import com.jftse.server.core.shared.packets.guild.SMSGGuildGoldData;

import java.util.ArrayList;
import java.util.List;

// Club Gold tab (GuiPop_GuildSystem btTabMyGuild03): C 0x202C {page} -> S 0x202D, ten GuildGoldUseInfo rows of the
// player's club, newest first (notes-v401). Not a member: result 0 with no rows (the tab is member-only on the client).
@PacketId(CMSGGuildGoldData.PACKET_ID)
public class GuildGoldDataRequestPacketHandler implements PacketHandler<FTConnection, CMSGGuildGoldData> {
    private final GuildMemberService guildMemberService;
    private final GuildService guildService;

    public GuildGoldDataRequestPacketHandler() {
        guildMemberService = ServiceManager.getInstance().getGuildMemberService();
        guildService = ServiceManager.getInstance().getGuildService();
    }

    @Override
    public void handle(FTConnection connection, CMSGGuildGoldData packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;

        FTPlayer activePlayer = client.getPlayer();
        List<GuildGoldUseInfo> rows = new ArrayList<>();
        try {
            GuildMember guildMember = guildMemberService.getByPlayer(activePlayer.getId());
            if (guildMember != null && !Boolean.TRUE.equals(guildMember.getWaitingForApproval())) {
                for (GuildGoldUsage usage : guildService.findGoldUsage(guildMember.getGuild().getId(), packet.getPage())) {
                    rows.add(GuildGoldUseInfo.builder()
                            .id(Math.toIntExact(usage.getId()))
                            .date(usage.getDate())
                            .goldUsed(usage.getGoldUsed() == null ? 0 : usage.getGoldUsed())
                            .name(usage.getPlayer().getName())
                            .goldRemaining(usage.getGoldRemaining() == null ? 0 : usage.getGoldRemaining())
                            .build());
                }
            }
        } catch (Exception e) {
            connection.sendTCP(SMSGGuildGoldData.builder().result((short) -10).rows(new ArrayList<>()).build());
            return;
        }
        connection.sendTCP(SMSGGuildGoldData.builder().result((short) 0).rows(rows).build());
    }
}
