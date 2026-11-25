package com.jftse.emulator.server.core.packets.pet;

import com.jftse.server.core.protocol.Packet;


public class S2CPetNameChangeAnswerPacket extends Packet {
    public S2CPetNameChangeAnswerPacket(short result) {
        // GuiPop_PetRename waits for 0x1525 after its 0x1524 (5760c7) and only closes on it.
        super((char) 0x1525);

        this.write(result);
    }
}
