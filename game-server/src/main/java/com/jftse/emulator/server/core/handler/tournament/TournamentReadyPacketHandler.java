package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.handler.lobby.room.RoomReadyChangeRequestPacketHandler;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeReady;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentReady;

// C2S 0x26C8 {u8 ready}: Gui_RoomGame's Ready in a tournament room (4cbaf6: with Config Tournament and the tournament-room flag
// the client sends it instead of 0x1775). Handled as the room ready change (notes-v186).
@PacketId(CMSGTournamentReady.PACKET_ID)
public class TournamentReadyPacketHandler implements PacketHandler<FTConnection, CMSGTournamentReady> {
    @Override
    public void handle(FTConnection connection, CMSGTournamentReady packet) {
        new RoomReadyChangeRequestPacketHandler().handle(connection, CMSGRoomChangeReady.builder().ready(packet.getReady() != 0).build());
        TournamentRooms.startWhenReady(connection.getClient().getActiveRoom());
    }
}
