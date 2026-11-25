package com.jftse.emulator.server.core.handler.event;

import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.CMSGRiceCakeEvent;

// C2S 0x213F {} after an hour of play with Config RiceCakeEvent (Gui_Main 0x4af396): HourlyPlayEvent (notes-v179).
@PacketId(CMSGRiceCakeEvent.PACKET_ID)
public class RiceCakeEventPacketHandler implements PacketHandler<FTConnection, CMSGRiceCakeEvent> {
    @Override
    public void handle(FTConnection connection, CMSGRiceCakeEvent packet) {
        HourlyPlayEvent.handle(connection, HourlyPlayEvent.RICE_CAKE, "event.riceCake.hourlyGold");
    }
}
