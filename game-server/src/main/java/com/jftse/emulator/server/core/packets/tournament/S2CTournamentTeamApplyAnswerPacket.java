package com.jftse.emulator.server.core.packets.tournament;

import com.jftse.server.core.protocol.Packet;

// S2C 0x26B6 (GuiPop_TourneyApplyTeam 5c45b0, notes-v373): i8 result; 0 -> i32 teamId (the popup adds the pair,
// MSG_TOURNEY_SUCCESS_TO_APPLY); -1 / -2 / -3 / -22..-30 -> their messages "%s (%d)" (TournamentService TEAM_*).
public class S2CTournamentTeamApplyAnswerPacket extends Packet {
    public S2CTournamentTeamApplyAnswerPacket(byte result, long teamId) {
        super((char) 0x26B6);

        this.write(result);
        if (result == 0)
            this.write((int) teamId);
    }
}
