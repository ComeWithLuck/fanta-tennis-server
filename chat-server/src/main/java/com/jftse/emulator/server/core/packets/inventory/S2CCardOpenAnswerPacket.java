package com.jftse.emulator.server.core.packets.inventory;

import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.item.EItemUseType;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

// S2C 0x1DE4 (client 54ce00, notes-v175): byte status; status != 0 -> the client only refreshes (no message); 0 -> short n (the
// client keeps at most 1) and n pocket records (the 0x1B69 / 0x106E record) - the drawn card.
public class S2CCardOpenAnswerPacket extends Packet {
    public static final byte SUCCESS = 0;
    public static final byte FAILED = 1;

    public S2CCardOpenAnswerPacket(byte status, PlayerPocket card) {
        super(PacketOperations.S2CCardOpenAnswer);

        this.write(status);
        if (status != SUCCESS || card == null)
            return;
        this.write((char) 1);
        this.write((int) card.getId().longValue());
        this.write(EItemCategory.valueOf(card.getCategory()).getValue());
        this.write(card.getItemIndex());
        this.write(card.getUseType().equals("N/A") ? (byte) 0 : EItemUseType.valueOf(card.getUseType().toUpperCase()).getValue());
        this.write(card.getItemCount());
        this.write(card.getCreated());
        this.write(card.getEnchantStr().byteValue());
        this.write(card.getEnchantSta().byteValue());
        this.write(card.getEnchantDex().byteValue());
        this.write(card.getEnchantWil().byteValue());
        this.write(card.getEnchantElement().byteValue());
        this.write(card.getEnchantLevel().byteValue());
    }
}
