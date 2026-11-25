package com.jftse.emulator.server.core.handler.event;

import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.CMSGNewYearEvent;

// C2S 0x213E {} after an hour of play with Config NewYearEvent (Gui_Main 0x4af346): HourlyPlayEvent (notes-v179).
@PacketId(CMSGNewYearEvent.PACKET_ID)
public class NewYearEventPacketHandler implements PacketHandler<FTConnection, CMSGNewYearEvent> {
    @Override
    public void handle(FTConnection connection, CMSGNewYearEvent packet) {
        HourlyPlayEvent.handle(connection, HourlyPlayEvent.NEW_YEAR, "event.newYear.hourlyGold");
    }
}
