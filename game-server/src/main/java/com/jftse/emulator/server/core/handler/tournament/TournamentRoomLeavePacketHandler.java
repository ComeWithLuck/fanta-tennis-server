package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.handler.lobby.room.RoomLeaveRequestPacketHandler;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomLeave;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentRoomLeave;

// C2S 0x240E {} - Exit in a tournament room (4c8150 -> 4bcae0 instead of 0x1771); the room leave answer 0x1772 then sends the
// client back to Gui_TournamentMenu (4c6630 -> 4bcde0 clears the tournament-room flag). Handled as the room leave (notes-v191).
@PacketId(CMSGTournamentRoomLeave.PACKET_ID)
public class TournamentRoomLeavePacketHandler implements PacketHandler<FTConnection, CMSGTournamentRoomLeave> {
    @Override
    public void handle(FTConnection connection, CMSGTournamentRoomLeave packet) {
        new RoomLeaveRequestPacketHandler().handle(connection, CMSGRoomLeave.builder().build());
    }
}
