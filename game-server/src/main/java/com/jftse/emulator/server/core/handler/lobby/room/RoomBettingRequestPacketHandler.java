package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomBetting;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.betting.CMSGBetting;

// C2S 0x1DB0 {u8 betMode, u8 side} (Gui_RoomGame btBettingRed 4cc2ee / btBettingBlue 4cc358) -> S2C 0x1DB1 {u8 result};
// on success the stake is taken and the room gets 0x1DB4 {position, side}. Rules: RoomBetting (notes-v179).
@PacketId(CMSGBetting.PACKET_ID)
public class RoomBettingRequestPacketHandler implements PacketHandler<FTConnection, CMSGBetting> {
    @Override
    public void handle(FTConnection connection, CMSGBetting packet) {
        FTClient client = connection.getClient();
        if (client == null || !client.hasPlayer())
            return;
        Room room = client.getActiveRoom();
        if (room == null) {
            connection.sendTCP(RoomBettingPackets.result((char) 0x1DB1, RoomBetting.CANNOT));
            return;
        }
        synchronized (room) {
            RoomPlayer rp = client.getRoomPlayer();
            byte result = RoomBetting.check(room, rp, packet.getBetMode(), packet.getSide(), client.getPlayer().getGold());
            if (result == RoomBetting.OK) {
                rp.setBettingSide(packet.getSide());
                RoomBettingPackets.gold(client, -room.getBettingAmount());
            }
            connection.sendTCP(RoomBettingPackets.result((char) 0x1DB1, result));
            if (result == RoomBetting.OK)
                RoomBettingPackets.broadcast(room, RoomBettingPackets.placed(rp.getPosition(), packet.getSide()));
        }
    }
}
