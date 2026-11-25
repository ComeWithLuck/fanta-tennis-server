package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomJoinResult;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.RoomManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreateQuick;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomJoin;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Gui_Lobby2 Quick Start (btQuickBasic / Battle / Pet / Guardian, control ids 18..21 -> 4a6c70): the client sends
 * {roomType, mode, players, flag} - Basic (0, 0, 0), Battle (0, 1, 0), Pet (2, -1, 0), Guardian (0, 2, 0) - and keeps
 * them at +0x1f48. The answer is the ordinary room join answer 0x138C (4a7750): 0 enters the room, -3 makes the client
 * create its own room from the kept settings (4a78b5 -> 4a6aa0 -> 4a6550, a CMSG_RoomCreate with a ROOMNAME_01..10
 * name), the other codes are the join refusals. So the server looks for a room to join and answers -3 when there is none.
 */
@PacketId(CMSGRoomCreateQuick.PACKET_ID)
public class RoomCreateQuickRequestPacketHandler implements PacketHandler<FTConnection, CMSGRoomCreateQuick> {
    private static final char NO_ROOM_FOUND = (char) -3;

    @Override
    public void handle(FTConnection connection, CMSGRoomCreateQuick packet) {
        FTClient client = connection.getClient();
        // prevent multiple room creations, this might have to be adjusted into a "room join answer"
        if (client.getActiveRoom() != null || !client.hasPlayer())
            return;

        if (!client.getIsJoiningOrLeavingRoom().compareAndSet(false, true)) {
            return;
        }

        try {
            RoomManager roomManager = GameManager.getInstance().getRoomManager();
            for (Room room : candidates(roomManager, client, packet)) {
                RoomJoinResult joinResult = roomManager.joinRoom(client, room.getRoomId(), (byte) 0, "");
                if (joinResult.result() != 0)
                    continue; // taken or closed meanwhile: try the next one

                Room joined = joinResult.room();
                SMSGRoomJoin answer = SMSGRoomJoin.builder()
                        .result((char) 0)
                        .roomType(joined.getRoomType())
                        .mode(joined.getMode())
                        .mapId(joined.getMap())
                        .build();
                connection.sendTCP(answer);
                roomManager.sendRoomInformation(connection, joined, GameManager.getInstance().getClientsInRoom(joined.getRoomId()));

                GameManager.getInstance().updateLobbyRoomListForAllClients(connection);
                GameManager.getInstance().refreshLobbyPlayerListForAllClients();
                return;
            }

            SMSGRoomJoin noRoom = SMSGRoomJoin.builder()
                    .result(NO_ROOM_FOUND)
                    .roomType((byte) 0)
                    .mode((byte) 0)
                    .mapId((byte) 0)
                    .build();
            connection.sendTCP(noRoom);
        } finally {
            client.getIsJoiningOrLeavingRoom().set(false);
        }
    }

    // Public, waiting rooms of the asked type and mode (mode -1: any) with a free player seat (0..3), not tournament or
    // town-square rooms, inside the room's level range (levelRange <= 0 = none: the web / JFTSE make-room sends 0, native's
    // quick room 5, 4a6ad3 -> 4a6550 arg 14); the fullest first, then the oldest (lowest id). [I: the original
    // server's choice is not in the client.]
    private static List<Room> candidates(RoomManager roomManager, FTClient client, CMSGRoomCreateQuick packet) {
        final int level = client.getPlayer().getLevel();
        return roomManager.getRooms().stream()
                .filter(r -> !roomManager.isTownSquare(r))
                .filter(r -> r.getRoomType() == packet.getRoomType() && r.getRoomType() != RoomType.CHAT)
                .filter(r -> packet.getMode() < 0 || r.getMode() == packet.getMode())
                .filter(r -> r.getTournamentId() == null && r.getTournamentMatchId() == null)
                .filter(r -> r.getStatus() == RoomStatus.NotRunning && !r.isPrivate())
                .filter(r -> !r.getBannedPlayers().contains(client.getPlayer().getId()))
                // notes-v391: -1 = no range (native 56a850 sends -1 for an unchecked box); 0 = the room level only.
                .filter(r -> r.getLevelRange() < 0 || Math.abs(level - r.getLevel()) <= r.getLevelRange())
                .filter(r -> IntStream.range(0, Math.min(4, r.getPositions().size())).anyMatch(i -> r.getPositions().get(i) == RoomPositionState.Free))
                .sorted(Comparator.comparingInt((Room r) -> -r.getRoomPlayerList().size()).thenComparingInt(Room::getRoomId))
                .toList();
    }
}
