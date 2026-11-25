package com.jftse.emulator.server.core.handler.emblem;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.emblem.S2CEmblemSlotPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.emblem.CMSGEmblemSlotApply;

// C2S 0x2295 {4 x u16} (GuiPop_MyInfo btEmblemApply, refused by the client itself in a room: GUIPOP_MYINFO_EMBLEM_CANNOT_APPLY)
// -> S2C 0x2297 with the stored slots (owned emblems only). notes-v185 E3, user-approved 2026-11-11.
@PacketId(CMSGEmblemSlotApply.PACKET_ID)
public class EmblemSlotApplyRequestPacketHandler implements PacketHandler<FTConnection, CMSGEmblemSlotApply> {
    @Override
    public void handle(FTConnection connection, CMSGEmblemSlotApply packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        short[] slots = ServiceManager.getInstance().getEmblemQuestService().applySlots(client.getPlayer().getId(),
                new short[]{packet.getSlot1(), packet.getSlot2(), packet.getSlot3(), packet.getSlot4()});
        connection.sendTCP(new S2CEmblemSlotPacket(slots));
    }
}
