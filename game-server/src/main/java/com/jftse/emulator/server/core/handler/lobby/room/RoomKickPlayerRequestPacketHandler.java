package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.life.room.RoomAccessPolicy;
import com.jftse.emulator.server.core.life.room.RoomRosterUpdates;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.packets.lobby.room.S2CLeaveRoomWithPositionPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomKickPlayer;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomJoin;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomLeave;

import java.util.List;

@PacketId(CMSGRoomKickPlayer.PACKET_ID)
public class RoomKickPlayerRequestPacketHandler implements PacketHandler<FTConnection, CMSGRoomKickPlayer> {
    @Override
    public void handle(FTConnection connection, CMSGRoomKickPlayer packet) {
        FTClient ftClient = connection.getClient();

        if (ftClient == null || !ftClient.hasPlayer()) return;
        final Room room = ftClient.getActiveRoom();
        if (room == null) return;
        synchronized (room) {
            if (!RoomAccessPolicy.mayConfigure(ftClient, room) || packet.getPosition() < 0 || packet.getPosition() >= 9) return;
            RoomPlayer playerToKick = room.getRoomPlayerList().stream()
                    .filter(rp -> rp.getPosition() == packet.getPosition())
                    .findAny()
                    .orElse(null);

            if (playerToKick != null && playerToKick.getPlayerId() != ftClient.getPlayer().getId()) {
                final List<FTClient> clientsInRoom = GameManager.getInstance().getClientsInRoom(room.getRoomId());
                final FTClient client = clientsInRoom.stream()
                        .filter(c -> c.hasPlayer() && c.getPlayer().getId() == playerToKick.getPlayerId())
                        .findFirst()
                        .orElse(null);

                if (client != null && client.getConnection() != null && client.getActiveRoom() == room) {
                    room.getBannedPlayers().add(client.getPlayer().getId());
                    SMSGRoomLeave answer = SMSGRoomLeave.builder().result((short) 0).build();
                    client.getConnection().sendTCP(answer);

                    GameManager.getInstance().handleRoomPlayerChanges(client.getConnection(), true);

                    SMSGRoomJoin roomJoinAnswerPacket = SMSGRoomJoin.builder()
                            .result((char) -4)
                            .roomType((byte) 0)
                            .mode((byte) 0)
                            .mapId((byte) 0)
                            .build();
                    client.getConnection().sendTCP(roomJoinAnswerPacket);
                    RoomRosterUpdates.sendToAll(room);
                    return;
                }
            }
            if (playerToKick == null || playerToKick.getPlayerId() != ftClient.getPlayer().getId()) {
                connection.sendTCP(new S2CLeaveRoomWithPositionPacket(packet.getPosition()));
            }
            RoomRosterUpdates.send(room, ftClient);
        }
    }
}
