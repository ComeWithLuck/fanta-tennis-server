package com.jftse.emulator.server.core.handler.emblem;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.EmblemQuestService;
import com.jftse.server.core.shared.packets.emblem.CMSGEmblemAccept;

// C2S 0x2274 {u16 questIndex} (btAccept, 0x49ab7d; emblems 1000..1999 never sent). The client has no accept result packet: it waits
// 20 s for 0x226B (0x469ef0) and else leaves the scene, so 0x226B goes back whether the quest started or not (notes-v185).
@PacketId(CMSGEmblemAccept.PACKET_ID)
public class EmblemAcceptRequestPacketHandler implements PacketHandler<FTConnection, CMSGEmblemAccept> {
    @Override
    public void handle(FTConnection connection, CMSGEmblemAccept packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        long playerId = client.getPlayer().getId();
        EmblemQuestService service = ServiceManager.getInstance().getEmblemQuestService();
        service.accept(playerId, packet.getQuestIndex() & 0xFFFF);
        EmblemListRequestPacketHandler.sendLog(connection, service, playerId);
    }
}
