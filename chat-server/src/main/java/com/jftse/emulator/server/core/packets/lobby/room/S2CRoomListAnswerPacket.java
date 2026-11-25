package com.jftse.emulator.server.core.packets.lobby.room;

import com.jftse.emulator.common.utilities.BitKit;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

import java.util.List;

public class S2CRoomListAnswerPacket extends Packet {
    public S2CRoomListAnswerPacket(List<Room> roomList) {
        super(PacketOperations.S2CRoomListAnswer);

        this.write((char) roomList.size());
        for (Room room : roomList) {
            int playerCount = room.getRoomPlayerList().size();

            this.write(room.getRoomId());
            this.write(room.getRoomName());
            this.write(room.getRoomType());
            this.write(room.getMode());
            this.write(room.getRule());
            this.write((byte) 0); // betting mode
            this.write((byte) 0); // betting coins
            this.write(room.getBettingAmount());
            this.write(room.getBall());
            this.write(room.getPlayers());
            this.write(room.isPrivate());
            this.write(room.getLevel());
            this.write(room.getLevelRange());
            this.write(room.getAllowBattlemon()); // allow battlemon
            this.write(room.getMap());
            this.write(room.isSkillFree());
            this.write(room.isQuickSlot());
            // 46cd00 (46ce78): a roomType 1 / mode 3 row carries the club house fields before the player count.
            if (room.getRoomType() == 1 && room.getMode() == 3) {
                this.write(room.getClubHouseName() == null ? "" : room.getClubHouseName());
                this.write(room.getClubHouseAccess());
                this.write(room.getClubHouseFee());
            }
            this.write(BitKit.fromUnsignedInt(playerCount));
            this.write((byte) 0); // ?
            this.write((byte) 0); // ?
        }
    }
}