package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.constants.GameMode;
import java.util.List;
import java.util.stream.IntStream;

/** Evaluate while holding the room monitor, together with the state change. */
public final class RoomAccessPolicy {
    private RoomAccessPolicy() { }

    public static RoomPlayer idleMember(FTClient client, Room room) {
        if (client == null || room == null || client.getActiveRoom() != room ||
                !client.hasPlayer() || room.getStatus() != RoomStatus.NotRunning) return null;
        RoomPlayer player = client.getRoomPlayer();
        return player != null && player.getPosition() >= 0 && player.getPosition() < room.getPositions().size() ? player : null;
    }

    private static RoomPlayer idleParticipant(FTClient client, Room room) {
        RoomPlayer player = idleMember(client, room);
        return player != null && player.getPosition() < 4 ? player : null;
    }

    public static boolean mayReady(FTClient client, Room room) {
        RoomPlayer player = idleParticipant(client, room);
        return player != null && !player.isFitting();
    }

    public static boolean mayConfigure(FTClient client, Room room) {
        RoomPlayer player = idleParticipant(client, room);
        return player != null && player.isMaster();
    }

    public static boolean mayStart(FTClient client, Room room) {
        return startRefusal(client, room) == START_ALLOWED;
    }

    // S2CRoomStartGameAck (0x17E6) refusal codes, as the native client reads them in 4cc6d0 (MsgBox text per code):
    // -1 MSG_NOT_ROOM_MASTER, -2 MSG_NEED_MORE_PLAYERS, -3 MSG_EVERYBODY_MUST_PRESS_READY, -4 MSG_INVAILD_NUM_USERS,
    // -5 MSG_INVAILD_TEAMS. 0 is the old silent refusal, kept for a requester that is not an idle member of the room
    // (no room text applies). Which check yields which code is inferred from the texts [I]; the codes and texts are native.
    public static final short START_ALLOWED = 1;
    public static final short START_REFUSED_SILENT = 0;
    public static final short START_NOT_ROOM_MASTER = -1;
    public static final short START_NEED_MORE_PLAYERS = -2;
    public static final short START_EVERYBODY_MUST_PRESS_READY = -3;
    public static final short START_INVALID_NUM_USERS = -4;
    public static final short START_INVALID_TEAMS = -5;

    /** START_ALLOWED when the requester may start now, else the 0x17E6 code to answer with. */
    public static short startRefusal(FTClient client, Room room) {
        RoomPlayer requester = idleParticipant(client, room);
        if (requester == null) return START_REFUSED_SILENT;
        if (!requester.isMaster()) return START_NOT_ROOM_MASTER;
        List<RoomPlayer> players = room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() >= 0 && player.getPosition() < 4).toList();
        if (players.stream().filter(RoomPlayer::isMaster).count() != 1) return START_REFUSED_SILENT;
        if (room.getMode() == GameMode.BASIC) {
            int capacity = room.getPlayers();
            if (capacity != 2 && capacity != 4) return START_INVALID_NUM_USERS;
            if (players.size() < capacity) return START_NEED_MORE_PLAYERS;
            if (players.size() > capacity) return START_INVALID_NUM_USERS;
            if (!IntStream.range(0, capacity)
                    .allMatch(slot -> players.stream().filter(player -> player.getPosition() == slot).count() == 1)) return START_INVALID_TEAMS;
        } else if (players.isEmpty()) {
            // Guardian / Battle permit fewer humans than the room's maximum capacity.
            return START_NEED_MORE_PLAYERS;
        }
        if (players.stream().anyMatch(player -> player.isFitting() || !player.isMaster() && !player.isReady())) return START_EVERYBODY_MUST_PRESS_READY;
        return START_ALLOWED;
    }
}
