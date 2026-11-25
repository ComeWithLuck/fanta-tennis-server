package com.jftse.emulator.server.core.handler.guild;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.guild.CMSGGuildCastleChangeInformation;
import com.jftse.server.core.shared.packets.guild.SMSGGuildCastleChangeInformation;

// Castle tab btGuildCastleGoldChange (55e1cb): C 0x2046 {int, int, byte access, int fee} (the record as edited) -> S 0x2047
// (5596d0 reads the result's first byte): 0 MSG_GUILD_CASTLE_INFO_CHANGE_SUCCESS, -1 MSG_CAN_NOT_FIND_GUILD,
// -2 MSG_GUILD_CASTLE_INFO_CHANGE_FAILED_2, -10 DB error. JFTSE rule [I]: only the master of a club that owns its club house;
// access 0..3, fee 0..1000 (the slider's range) (notes-v405).
@PacketId(CMSGGuildCastleChangeInformation.PACKET_ID)
public class GuildCastleChangeInfoRequestPacketHandler implements PacketHandler<FTConnection, CMSGGuildCastleChangeInformation> {
    @Override
    public void handle(FTConnection connection, CMSGGuildCastleChangeInformation packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        int result;
        try {
            result = ServiceManager.getInstance().getGuildService().changeCastle(client.getPlayer().getId(),
                    Byte.toUnsignedInt(packet.getAccessLimit()), packet.getAdmissionFee());
        } catch (Exception e) {
            result = -10;
        }
        connection.sendTCP(SMSGGuildCastleChangeInformation.builder().result(result).build());
    }
}
