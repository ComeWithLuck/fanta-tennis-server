package com.jftse.emulator.server.core.handler.guild;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.guild.S2CGuildCastleInfoAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.guild.CMSGGuildCastleInfo;

// Castle tab (notes-v405): C 0x2044 {} -> S 0x2045 (559640): byte result; on 0 two ints (not shown by the tab; 0 here [I]),
// the access byte (radGuildCastleQualification_00..03) and the admission fee (sldGuildCastleGold). A player whose club owns
// no club house (level < 5 under the user-approved rule) gets result 1 (the client reads nothing more).
@PacketId(CMSGGuildCastleInfo.PACKET_ID)
public class GuildCastleInfoRequestPacketHandler implements PacketHandler<FTConnection, CMSGGuildCastleInfo> {
    @Override
    public void handle(FTConnection connection, CMSGGuildCastleInfo packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        int[] info = ServiceManager.getInstance().getGuildService().castleInfo(client.getPlayer().getId());
        connection.sendTCP(new S2CGuildCastleInfoAnswerPacket((byte) (info[0] == 0 ? 0 : 1), 0, 0, (byte) info[1], info[2]));
    }
}
