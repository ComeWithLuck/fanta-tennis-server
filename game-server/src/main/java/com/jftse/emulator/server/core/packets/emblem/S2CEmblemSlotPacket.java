package com.jftse.emulator.server.core.packets.emblem;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

// S2C 0x2297 (0x4966e0 -> 0x4932d0, notes-v185 E3): 4 x i16 -> char +0x30a..+0x310 (log "Emblem Slot 0..3"), then scene event 0x34.
// The only answer to 0x2295 (no result code). Also sent after every 0x226B list [I], since nothing else gives a character its slots.
public class S2CEmblemSlotPacket extends Packet {
    public S2CEmblemSlotPacket(short[] slots) {
        super(PacketOperations.S2CEmblemSlotAnswer);
        write(this, slots);
    }

    // The four slot shorts as the room player record (0x494313..0x49432a) and 0x139D carry them.
    public static void write(Packet packet, short[] slots) {
        for (int i = 0; i < 4; i++)
            packet.write(i < slots.length ? slots[i] : (short) 0);
    }

    public static void write(Packet packet, long playerId) {
        write(packet, ServiceManager.getInstance().getEmblemQuestService().slots(playerId));
    }
}
