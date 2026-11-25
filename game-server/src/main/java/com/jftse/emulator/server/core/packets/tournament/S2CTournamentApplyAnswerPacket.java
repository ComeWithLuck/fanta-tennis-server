package com.jftse.emulator.server.core.packets.tournament;

import com.jftse.server.core.protocol.Packet;

// S2C 0x26B2 (5c7330): i8 result; 0 -> MSG_TOURNEY_SUCCESS_TO_APPLY (my state 1); -2..-8 -> MSG_TOURNEY_FAILED_TO_APPLY_02..08
// "%s (%d)"; -5 also reads a UTF-16 string (at most 100 characters) shown as "[%s] %s (%d)". notes-v176.
public class S2CTournamentApplyAnswerPacket extends Packet {
    public S2CTournamentApplyAnswerPacket(byte result, String title) {
        super((char) 0x26B2);

        this.write(result);
        if (result == -5) {
            String text = title == null ? "" : title;
            this.write(text.length() > 100 ? text.substring(0, 100) : text);
        }
    }
}
