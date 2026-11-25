package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentGameTimeOver;

// C2S 0x203E {} - Gui_InGame's play time count (S 0x203D) reached 0 (4a04fc, sent once per client) (notes-v198).
@PacketId(CMSGTournamentGameTimeOver.PACKET_ID)
public class TournamentGameTimeOverPacketHandler implements PacketHandler<FTConnection, CMSGTournamentGameTimeOver> {
    @Override
    public void handle(FTConnection connection, CMSGTournamentGameTimeOver packet) {
        TournamentRooms.gameTimeOver(connection.getClient());
    }
}
