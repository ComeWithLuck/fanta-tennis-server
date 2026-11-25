package com.jftse.emulator.server.core.packets.pet;

import com.jftse.server.core.protocol.Packet;


public class S2CPetReviveAnswerPacket extends Packet {
    public S2CPetReviveAnswerPacket(short result) {
        // GuiPop_PetRebirth waits for 0x1527 after its 0x1526 (575b47).
        super((char) 0x1527);

        this.write(result);
    }
}
