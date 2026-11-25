package com.jftse.emulator.server.core.packets.inventory;

import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

// S2C 0x1DE5 (client 4b6080, notes-v174): byte status; 0 -> byte count = the extra card slots (the client shows count + 1);
// 0xFF -> MSG_CARD_LAST_SLOT "You cannot add more than 4 slots."; any other status is silent.
public class S2CExpandCardSlotPacket extends Packet {
    public static final byte SUCCESS = 0;
    public static final byte LAST_SLOT = (byte) 0xFF;

    public S2CExpandCardSlotPacket(byte status, byte count) {
        super(PacketOperations.S2CExpandCardSlot);

        this.write(status);
        this.write(count);
    }
}
