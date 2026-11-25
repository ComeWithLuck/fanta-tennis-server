package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomPlayerListInformationPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomChangePosition;
import java.util.List;

/** A full native roster also acknowledges a seat request that lost a race. */
public final class RoomRosterUpdates {
    private RoomRosterUpdates() { }

    public static void send(Room room, FTClient client) {
        if (client.getActiveRoom() != room || !client.hasPlayer() || client.getConnection() == null) return;
        client.getConnection().sendTCP(new S2CRoomPlayerListInformationPacket(visiblePlayers(room, client)));
    }

    private static List<RoomPlayer> visiblePlayers(Room room, FTClient client) {
        return room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() != 9 || player.getPlayerId() == client.getPlayer().getId())
                .toList();
    }

    /** Caller holds the room monitor. All constructor/byte serialization can
     * fail before the proposed Pet is committed. The returned packet is detached. */
    public static Packet preparePetSnapshot(Room room, FTClient client, RoomPlayer owner, PetView proposedPet) {
        if (client.getActiveRoom() != room || !client.hasPlayer() || client.getConnection() == null)
            throw new IllegalStateException("Room Pet snapshot recipient changed");
        return new Packet(new S2CRoomPlayerListInformationPacket(visiblePlayers(room, client), owner, proposedPet).toBytes());
    }

    public static void sendToAll(Room room) {
        for (FTClient client : GameManager.getInstance().getClientsInRoom(room.getRoomId())) send(room, client);
    }

    public static void sendSeatResult(Room room, FTClient client) {
        RoomPlayer player = client.getRoomPlayer();
        if (player == null || client.getActiveRoom() != room || client.getConnection() == null) return;
        // Native already uses old==new/result0 for an unchanged seat refresh.
        client.getConnection().sendTCP(SMSGRoomChangePosition.builder().result((char)0)
                .oldPosition(player.getPosition()).newPosition(player.getPosition()).build());
        send(room, client);
    }
}
