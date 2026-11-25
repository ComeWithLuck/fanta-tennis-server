package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomAccessPolicy;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCloseSlot;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomCloseSlot;

@PacketId(CMSGRoomCloseSlot.PACKET_ID)
public class RoomSlotCloseRequestPacketHandler implements PacketHandler<FTConnection, CMSGRoomCloseSlot> {
    @Override
    public void handle(FTConnection connection, CMSGRoomCloseSlot packet) {
        FTClient client = connection.getClient();
        if (client == null || !client.getIsClosingSlot().compareAndSet(false, true)) return;
        try {
            Room room = client.getActiveRoom();
            if (room == null) return;
            synchronized (room) {
                int slot = packet.getSlot();
                if (!RoomAccessPolicy.mayConfigure(client, room) || slot < 0 || slot >= room.getPositions().size() || slot == 4 || slot == 9 ||
                        (slot < 4 && slot >= room.getPlayers())) return;
                if (room.getPositions().get(slot) == RoomPositionState.InUse ||
                        room.getRoomPlayerList().stream().anyMatch(player -> player.getPosition() == slot)) {
                    // Return the existing state if a join won the race with this close.
                    connection.sendTCP(SMSGRoomCloseSlot.builder().slot(packet.getSlot()).close(false).build());
                    return;
                }
                room.getPositions().set(slot, packet.getClose() ? RoomPositionState.Locked : RoomPositionState.Free);
                var answer = SMSGRoomCloseSlot.builder().slot(packet.getSlot()).close(packet.getClose()).build();
                for (FTClient member : GameManager.getInstance().getClientsInRoom(room.getRoomId())) {
                    if (member.getConnection() != null) member.getConnection().sendTCP(answer);
                }
            }
        } finally {
            client.getIsClosingSlot().set(false);
        }
    }
}
