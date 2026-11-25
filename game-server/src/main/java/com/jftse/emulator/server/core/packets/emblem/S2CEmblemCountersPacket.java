package com.jftse.emulator.server.core.packets.emblem;

import com.jftse.server.core.emblem.EmblemCounters;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

// S2C 0x2292 (Gui_Main 0x4b0130 -> 0x4ad730 -> 0x493090, notes-v185): u16 count, count x {i8 type, i32 value}; an odd type adds,
// an even type sets (table 0x493250). Sent as "set" entries before every 0x226B so the client's quest progress uses JFTSE's counters.
public class S2CEmblemCountersPacket extends Packet {
    public S2CEmblemCountersPacket(EmblemCounters counters) {
        super(PacketOperations.S2CEmblemCounters);

        int[][] entries = counters.packetEntries();
        this.write((char) entries.length);
        for (int[] e : entries) {
            this.write((byte) e[0]);
            this.write(e[1]);
        }
    }
}
