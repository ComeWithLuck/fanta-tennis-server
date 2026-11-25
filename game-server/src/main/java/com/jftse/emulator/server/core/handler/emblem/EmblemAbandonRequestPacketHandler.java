package com.jftse.emulator.server.core.handler.emblem;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.EmblemQuestService;
import com.jftse.server.core.shared.packets.emblem.CMSGEmblemAbandon;

// C2S 0x2278 {u16 questIndex} (btAbandon, 0x49ac46). Like accept, no result packet: 0x226B goes back within the client's 20 s wait
// whether anything changed or not (notes-v185).
@PacketId(CMSGEmblemAbandon.PACKET_ID)
public class EmblemAbandonRequestPacketHandler implements PacketHandler<FTConnection, CMSGEmblemAbandon> {
    @Override
    public void handle(FTConnection connection, CMSGEmblemAbandon packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        long playerId = client.getPlayer().getId();
        EmblemQuestService service = ServiceManager.getInstance().getEmblemQuestService();
        service.abandon(playerId, packet.getQuestIndex() & 0xFFFF);
        EmblemListRequestPacketHandler.sendLog(connection, service, playerId);
    }
}
