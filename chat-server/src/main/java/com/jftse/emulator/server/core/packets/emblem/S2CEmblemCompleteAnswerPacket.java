package com.jftse.emulator.server.core.packets.emblem;

import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.item.EItemUseType;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

import java.util.List;

// S2C 0x227D (0x49b0a0, notes-v185): u8 result; -1..-11 -> EMBLEMQUEST_COMPLETE_QUEST_RESULT_FAILED_1..11. 0 -> u8 level (stored at
// char +0x1ae), i32 exp (+0x244), i32 gold (the money setter 0x494a70) - all absolute - then i16 n and n pocket records (the 0x1B69
// record, upserted by 0x5bab30). No success popup: the slot / panel change comes from the 0x226B sent after it.
public class S2CEmblemCompleteAnswerPacket extends Packet {
    public S2CEmblemCompleteAnswerPacket(byte result) {
        super(PacketOperations.S2CEmblemCompleteAnswer);
        this.write(result);
    }

    public S2CEmblemCompleteAnswerPacket(byte level, int exp, int gold, List<PlayerPocket> items) {
        super(PacketOperations.S2CEmblemCompleteAnswer);

        this.write((byte) 0);
        this.write(level);
        this.write(exp);
        this.write(gold);
        this.write((short) items.size());
        for (PlayerPocket item : items) {
            this.write((int) item.getId().longValue());
            this.write(EItemCategory.valueOf(item.getCategory()).getValue());
            this.write(item.getItemIndex());
            this.write(item.getUseType().equals("N/A") ? (byte) 0 : EItemUseType.valueOf(item.getUseType().toUpperCase()).getValue());
            this.write(item.getItemCount());
            this.write(item.getCreated());
            this.write(item.getEnchantStr().byteValue());
            this.write(item.getEnchantSta().byteValue());
            this.write(item.getEnchantDex().byteValue());
            this.write(item.getEnchantWil().byteValue());
            this.write(item.getEnchantElement().byteValue());
            this.write(item.getEnchantLevel().byteValue());
        }
    }
}
