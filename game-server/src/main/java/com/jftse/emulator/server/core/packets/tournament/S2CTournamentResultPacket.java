package com.jftse.emulator.server.core.packets.tournament;

import com.jftse.server.core.protocol.Packet;

// One-byte tournament answers (notes-v176):
//   0x26B4 cancel (5c7530): 0 -> MSG_TOURNEY_SUCCESS_TO_CANCEL (my state 0), else MSG_TOURNEY_FAILED_TO_CANCEL "(code)";
//   0x26C1 / 0x26C3 bracket teams / results (5c6bf0 / 5c6cc0): non-zero / 0xff -> MSG_TOURNEY_MATCH_MADE_NOT_YET;
//   0x26C7 play (5c7610): -1 MSG_TOURNEY_FAILED_TO_JOIN, -2 _JOIN_02, other non-zero _JOIN_03 "(code)".
public class S2CTournamentResultPacket extends Packet {
    //   0x2417 GM spectate (5c7710): 0 nothing (the pushed 0x138C room join follows), -1..-6 MSG_TOURNEY_FAILED_TO_JOIN_FOR_GM_01..06
    //   "%s :%d", other non-zero "GM Spectator failed :%d".
    public static final char CANCEL = 0x26B4, BRACKET_TEAMS = 0x26C1, BRACKET_RESULTS = 0x26C3, PLAY = 0x26C7, GM_SPECTATE = 0x2417;

    public S2CTournamentResultPacket(char packetId, byte result) {
        super(packetId);

        this.write(result);
    }
}
