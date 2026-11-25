package com.jftse.emulator.server.core.packets.emblem;

import com.jftse.entities.database.model.emblem.EmblemQuestLog;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

import java.util.List;

// S2C 0x226B (client 0x4966e0 -> 0x4608c0, notes-v176): i16 status; -1 = no log entries (the client clears its request flag);
// else u16 count and per entry {i16 questIndex, u8 inProgress, i16 complete, 4 x {u8 T, i16 C}}. The client reads T only for its
// debug print (it evaluates the quest data's own ConditionType) [C 0x45e1ab]; C is the accept baseline (base1..4). An index the
// client's quest data lacks aborts the rest of the list (0x4609a9), so only logged indexes are sent.
public class S2CEmblemListAnswerPacket extends Packet {
    public S2CEmblemListAnswerPacket(List<EmblemQuestLog> log) {
        super(PacketOperations.S2CEmblemListAnswer);

        if (log == null || log.isEmpty()) {
            this.write((short) -1);
            return;
        }
        this.write((short) 0);
        this.write((char) log.size());
        for (EmblemQuestLog e : log) {
            this.write(e.getQuestIndex().shortValue());
            this.write((byte) (Boolean.TRUE.equals(e.getInProgress()) ? 1 : 0));
            this.write((short) Math.min(Short.MAX_VALUE, e.getCompleteCount() == null ? 0 : e.getCompleteCount()));
            int[] base = {n(e.getBase1()), n(e.getBase2()), n(e.getBase3()), n(e.getBase4())};
            for (int b : base) {
                this.write((byte) 0); // T [I]: unused by the client
                this.write((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, b)));
            }
        }
    }

    private static int n(Integer v) { return v == null ? 0 : v; }
}
