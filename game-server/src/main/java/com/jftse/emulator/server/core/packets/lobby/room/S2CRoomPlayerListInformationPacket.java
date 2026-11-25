package com.jftse.emulator.server.core.packets.lobby.room;

import com.jftse.emulator.server.core.client.EquippedItemParts;
import com.jftse.emulator.server.core.client.GuildView;
import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.utils.BattleUtils;
import com.jftse.entities.database.model.player.EquippedItemStats;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

import java.util.List;

public class S2CRoomPlayerListInformationPacket extends Packet {
    public S2CRoomPlayerListInformationPacket(List<RoomPlayer> roomPlayerList) {
        this(roomPlayerList, null, null);
    }

    /** Serialize a proposed Pet change without temporarily mutating the room. */
    public S2CRoomPlayerListInformationPacket(List<RoomPlayer> roomPlayerList, RoomPlayer petOwner, PetView proposedPet) {
        super(PacketOperations.S2CRoomPlayerInformation);

        this.write((char) roomPlayerList.size());
        for (RoomPlayer roomPlayer : roomPlayerList) {
            EquippedItemParts equippedItemParts = roomPlayer.getEquippedItemPartsIDX();
            EquippedItemStats equippedItemStats = roomPlayer.getEquippedItemStats();
            PetView pet = roomPlayer == petOwner ? proposedPet : roomPlayer.getPet();

            boolean isSpectator = roomPlayer.getPosition() > 3;
            this.write(roomPlayer.getPosition());
            this.write(roomPlayer.getName());
            this.write((byte) roomPlayer.getLevel());
            this.write(roomPlayer.isGameMaster());
            this.write(roomPlayer.isMaster());
            this.write(roomPlayer.isReady());
            this.write(roomPlayer.isFitting());
            this.write((byte) roomPlayer.getPlayerType());
            this.write(isSpectator);
            this.write(pet != null);

            GuildView guild = roomPlayer.getGuild();
            this.write(guild != null ? guild.name() : "");

            if (guild != null) {
                this.write(guild.logoBackgroundId());
                this.write(guild.logoBackgroundColor());
                this.write(guild.logoPatternId());
                this.write(guild.logoPatternColor());
                this.write(guild.logoMarkId());
                this.write(guild.logoMarkColor());
            } else {
                for (int i = 0; i < 6; i++)
                    this.write(0);
            }

            this.write((byte) 0);
            this.write(roomPlayer.getCoupleName());
            this.write(0);
            this.write(roomPlayer.getBettingSide()); // bet side: 0 red, 1 blue, 0xff none (client 0x494313; was 0 = red)
            // The four equipped emblems (notes-v185 E3; were zeros).
            com.jftse.emulator.server.core.packets.emblem.S2CEmblemSlotPacket.write(this, roomPlayer.getPlayerId());

            this.write((BattleUtils.calculatePlayerHp(roomPlayer.getLevel()) + equippedItemStats.getAddHp() + roomPlayer.getBonusHp()));

            // status points
            this.write((byte) roomPlayer.getStrength());
            this.write((byte) roomPlayer.getStamina());
            this.write((byte) roomPlayer.getDexterity());
            this.write((byte) roomPlayer.getWillpower());
            // enchant added status points
            this.write((byte) (equippedItemStats.getEnchantStr() + equippedItemStats.getStrength()));
            this.write((byte) (equippedItemStats.getEnchantSta() + equippedItemStats.getStamina()));
            this.write((byte) (equippedItemStats.getEnchantDex() + equippedItemStats.getDexterity()));
            this.write((byte) (equippedItemStats.getEnchantWil() + equippedItemStats.getWillpower()));
            // ??
            for (int i = 5; i < 13; i++) {
                this.write((byte) 0);
            }
            // element??
            this.write((byte) 0);
            this.write((byte) 0);

            // earrings added status points
            this.write(0);
            this.write((byte) 0);
            this.write((byte) 0);
            this.write((byte) 0);
            this.write((byte) 0);
            // cards added status points
            this.write(0);
            this.write((byte) 0);
            this.write((byte) 0);
            this.write((byte) 0);
            this.write((byte) 0);
            // ??
            for (int i = 5; i < 13; ++i) {
                this.write((byte) 0);
            }
            // ??
            for (int i = 5; i < 13; ++i) {
                this.write((byte) 0);
            }
            /* end - status points */

            this.write(equippedItemParts.hair());
            this.write(equippedItemParts.face());
            this.write(equippedItemParts.dress());
            this.write(equippedItemParts.pants());
            this.write(equippedItemParts.socks());
            this.write(equippedItemParts.shoes());
            this.write(equippedItemParts.gloves());
            this.write(equippedItemParts.racket());
            this.write(equippedItemParts.glasses());
            this.write(equippedItemParts.bag());
            this.write(equippedItemParts.hat());
            this.write(equippedItemParts.dye());

            if (pet != null) {
                this.write(pet.name());
                this.write((byte) pet.level());
                this.write((byte) pet.type());
                this.write(pet.hp());
                this.write((byte) pet.strength());
                this.write((byte) pet.stamina());
                this.write((byte) pet.dexterity());
                this.write((byte) pet.willpower());
                this.write(pet.hunger());
                this.write(pet.energy());
            }
        }
    }
}
