package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.common.utilities.StringUtils;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomAccessPolicy;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomInformationPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeIsPrivate;

@PacketId(CMSGRoomChangeIsPrivate.PACKET_ID)
public class RoomIsPrivateChangePacketHandler implements PacketHandler<FTConnection, CMSGRoomChangeIsPrivate> {
    @Override
    public void handle(FTConnection connection, CMSGRoomChangeIsPrivate packet) {
        FTClient client = connection.getClient();

        String password = packet.getPassword();
        Room room = client.getActiveRoom();
        if (room != null) {
            synchronized (room) {
                if (!RoomAccessPolicy.mayConfigure(client, room)) return;
                if (StringUtils.isEmpty(password)) {
                    room.setPassword(null);
                    room.setPrivate(false);
                } else {
                    room.setPassword(password);
                    room.setPrivate(true);
                }
            }

            S2CRoomInformationPacket roomInformationPacket = new S2CRoomInformationPacket(room);
            GameManager.getInstance().getClientsInRoom(room.getRoomId()).forEach(c -> {
                if (c.getConnection() != null) {
                    c.getConnection().sendTCP(roomInformationPacket);
                }
            });
        }
    }
}
