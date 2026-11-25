package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.life.room.RoomBetting;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.life.room.*;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.*;

@PacketId(CMSGRoomChangePosition.PACKET_ID)
public class RoomPositionChangeRequestPacketHandler implements PacketHandler<FTConnection, CMSGRoomChangePosition> {
    @Override
    public void handle(FTConnection connection, CMSGRoomChangePosition packet) {
        FTClient client = connection.getClient();
        if (client == null || !client.hasPlayer() || !client.getIsChangingSlot().compareAndSet(false, true)) return;
        try {
            Room room = client.getActiveRoom();
            if (room == null) return;
            synchronized (room) {
                RoomPlayer player = RoomAccessPolicy.idleMember(client, room);
                if (player == null) return;
                short from = player.getPosition(), to = packet.getPosition();
                if (from == to || to < 0 || to >= room.getPositions().size() || to == 4 ||
                        (to < 4 && to >= room.getPlayers()) || (to == 9 && !client.isGameMaster()) ||
                        player.isFitting() || player.getPet() != null || room.getPositions().get(to) == RoomPositionState.Locked ||
                        player.getBettingSide() != RoomBetting.NONE) { // a bettor keeps the seat (MSG_CAN_NOT_CHANGE_POSITION_AT_BETTING [I])
                    RoomRosterUpdates.sendSeatResult(room, client);
                    return;
                }
                RoomPlayer other = room.getRoomPlayerList().stream().filter(p -> p.getPosition() == to).findFirst().orElse(null);
                if ((other != null && (other.getBettingSide() != RoomBetting.NONE || !player.isMaster() || other.isFitting() || other.getPet() != null || from == 9 && !other.isGameMaster())) ||
                        (other == null && room.getPositions().get(to) != RoomPositionState.Free)) {
                    RoomRosterUpdates.sendSeatResult(room, client);
                    return;
                }
                player.setPosition(to);
                if (other == null) {
                    room.getPositions().set(from, from == 9 ? RoomPositionState.Locked : RoomPositionState.Free);
                    room.getPositions().set(to, RoomPositionState.InUse);
                    GameManager.getInstance().sendPacketToAllClientsInSameRoom(SMSGRoomChangePosition.builder()
                            .result((char) 0).oldPosition(from).newPosition(to).build(), connection);
                } else {
                    other.setPosition(from);
                    GameManager.getInstance().sendPacketToAllClientsInSameRoom(SMSGRoomSwapPosition.builder()
                            .oldPosition(from).newPosition(to).build(), connection);
                    connection.sendTCP(SMSGRoomChangePosition.builder().result((char)0).oldPosition(to).newPosition(to).build());
                }
                // A team change invalidates readiness even when moving to an empty seat.
                for (RoomPlayer member : room.getRoomPlayerList()) {
                    member.setReady(false);
                    GameManager.getInstance().sendPacketToAllClientsInSameRoom(SMSGRoomChangeReady.builder()
                            .position(member.getPosition()).ready(false).build(), connection);
                }
                RoomRosterUpdates.sendToAll(room);
            }
        } finally {
            client.getIsChangingSlot().set(false);
        }
    }
}
