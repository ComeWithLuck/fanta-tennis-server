package com.jftse.emulator.server.core.packets.tournament;

import com.jftse.server.core.protocol.Packet;

// S2C 0x26B8 (GuiPop_TourneyApplyTeam 5c45b0, notes-v373): i8 result; 0 -> the popup drops the team,
// MSG_TOURNEY_SUCCESS_TO_CANCEL; else MSG_TOURNEY_FAILED_TO_CANCEL "%s (%d)".
public class S2CTournamentTeamCancelAnswerPacket extends Packet {
    public S2CTournamentTeamCancelAnswerPacket(byte result) {
        super((char) 0x26B8);

        this.write(result);
    }
}
