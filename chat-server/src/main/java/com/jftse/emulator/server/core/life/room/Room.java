package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.constants.RoomStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class Room {
    public Room() {
        bannedPlayers = new ConcurrentLinkedDeque<>();
        invitedPlayerIds = new ConcurrentLinkedDeque<>();
        roomPlayerList = new ConcurrentLinkedDeque<>();
        status = RoomStatus.NotRunning;
    }

    private short roomId;
    private String roomName;
    private byte roomType;
    private byte allowBattlemon;
    private byte mode;
    private byte rule;
    private byte players;
    private boolean isPrivate;
    private boolean skillFree;
    private boolean quickSlot;
    private byte level;
    private byte levelRange;
    private char bettingType;
    private int bettingAmount;
    private byte map;
    private int ball;
    private String password;
    private ConcurrentLinkedDeque<Long> bannedPlayers;
    private ConcurrentLinkedDeque<Long> invitedPlayerIds;
    private ConcurrentLinkedDeque<RoomPlayer> roomPlayerList;
    private int status;

    // A club house room (roomType 1, mode 3; notes-v405): its club and that club's entry settings, written into the room list
    // row (0x138E, client reader 46cd00: wstring club name, byte access, int fee).
    private Long clubHouseGuildId;
    private String clubHouseName;
    private byte clubHouseAccess;
    private int clubHouseFee;

    // Guardian
    private boolean isHardMode; // Guardians are very strong
    private boolean isArcade; // You have to play against all guardians there are
    private boolean isRandomGuardians; // Always random guardians are spawned.
}
