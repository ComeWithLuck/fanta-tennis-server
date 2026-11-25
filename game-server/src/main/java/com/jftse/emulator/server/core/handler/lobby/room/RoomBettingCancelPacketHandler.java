package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomBetting;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.betting.CMSGBettingCancel;

// C2S 0x1DB2 {} (btBettingCancel 4cc3c2) -> S2C 0x1DB3 {u8: 0 ok, 0xff MSG_BETTING_CANEL_FAILED_01 (no bet), 0xfe _02 (the
// match is starting)}; on success the stake is returned and the room gets 0x1DB5 {position}. notes-v179.
@PacketId(CMSGBettingCancel.PACKET_ID)
public class RoomBettingCancelPacketHandler implements PacketHandler<FTConnection, CMSGBettingCancel> {
    @Override
    public void handle(FTConnection connection, CMSGBettingCancel packet) {
        FTClient client = connection.getClient();
        if (client == null || !client.hasPlayer())
            return;
        Room room = client.getActiveRoom();
        if (room == null) {
            connection.sendTCP(RoomBettingPackets.result((char) 0x1DB3, RoomBetting.CANCEL_FAILED_01));
            return;
        }
        synchronized (room) {
            RoomPlayer rp = client.getRoomPlayer();
            byte result = RoomBetting.checkCancel(room, rp);
            if (result == RoomBetting.CANCEL_OK) {
                rp.setBettingSide(RoomBetting.NONE);
                RoomBettingPackets.gold(client, room.getBettingAmount());
            }
            connection.sendTCP(RoomBettingPackets.result((char) 0x1DB3, result));
            if (result == RoomBetting.CANCEL_OK)
                RoomBettingPackets.broadcast(room, RoomBettingPackets.cleared(rp.getPosition()));
        }
    }
}
