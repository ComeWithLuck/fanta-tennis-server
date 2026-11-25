package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentWaitTimeOver;

// C2S 0x2411 {} - Gui_RoomGame's wait time (S 0x2410 code 0 with auto set) ran out (4c8cb0, sent once) (notes-v198).
@PacketId(CMSGTournamentWaitTimeOver.PACKET_ID)
public class TournamentWaitTimeOverPacketHandler implements PacketHandler<FTConnection, CMSGTournamentWaitTimeOver> {
    @Override
    public void handle(FTConnection connection, CMSGTournamentWaitTimeOver packet) {
        TournamentRooms.waitOver(connection.getClient());
    }
}
