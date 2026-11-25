package com.jftse.emulator.server.core.handler.emblem;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.emblem.S2CEmblemCountersPacket;
import com.jftse.emulator.server.core.packets.emblem.S2CEmblemListAnswerPacket;
import com.jftse.emulator.server.core.packets.emblem.S2CEmblemSlotPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.EmblemQuestService;
import com.jftse.server.core.shared.packets.emblem.CMSGEmblemList;

// C2S 0x226A (Gui_EmblemQuest activate 0x49b4ee / MyInfo emblem tab 0x572272) -> S2C 0x226B with the character's quest log
// (-1 when it has none). notes-v176, user-approved 2026-11-11 (was an empty stub, so the Quest scene never got its list).
// notes-v185: 0x2292 counters go first, so the client's progress reads JFTSE's records [I: order].
@PacketId(CMSGEmblemList.PACKET_ID)
public class EmblemListRequestPacketHandler implements PacketHandler<FTConnection, CMSGEmblemList> {
    @Override
    public void handle(FTConnection connection, CMSGEmblemList packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        long playerId = client.getPlayer().getId();
        sendLog(connection, ServiceManager.getInstance().getEmblemQuestService(), playerId);
    }

    // 0x2292 then 0x226B: after the first list the client never asks again, so every change is pushed this way (notes-v185).
    public static void sendLog(FTConnection connection, EmblemQuestService service, long playerId) {
        connection.sendTCP(new S2CEmblemCountersPacket(service.counters(playerId)));
        connection.sendTCP(new S2CEmblemListAnswerPacket(service.log(playerId)));
        // E3: the slots follow (MyInfo's emblem tab asks 0x226A when opened; nothing else sends them) [I].
        connection.sendTCP(new S2CEmblemSlotPacket(service.slots(playerId)));
    }
}
