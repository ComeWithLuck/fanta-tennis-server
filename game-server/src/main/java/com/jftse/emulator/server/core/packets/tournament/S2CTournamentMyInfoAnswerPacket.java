package com.jftse.emulator.server.core.packets.tournament;

import com.jftse.server.core.protocol.Packet;

import java.util.Date;

// S2C 0x26BF (5c78c0 -> 5c8120): u8 result; 0 -> u32 tournamentId, u8 myState (MY_TOURNEY_STATE_*: 0 not applied, 1 applied,
// 2 entered, 3 dropped, 4 winner), u8 (+0x1dd, 0), three UTF-16 names of at most 12 characters (partner, opponent A, opponent
// B), u8 qualifying group, u8 canPlay (enables btPlay), two FILETIMEs (the round time, shown when both are set).
// -1 / -2 / -3 -> MSG_MY_TOURNEY_FAILED_03 "(code)" (the client's switch falls through). notes-v176: no brackets yet, so
// the names are empty, canPlay 0 and the times 0 [I].
public class S2CTournamentMyInfoAnswerPacket extends Packet {
    public S2CTournamentMyInfoAnswerPacket(int tournamentId, byte myState, byte group) {
        this(tournamentId, myState, group, "", false);
    }

    // opponent: "opponent A" (5c8c00 shows it for my state 2); canPlay enables btPlay (5c7dbb) - an open match (notes-v186).
    public S2CTournamentMyInfoAnswerPacket(int tournamentId, byte myState, byte group, String opponent, boolean canPlay) {
        this(tournamentId, myState, group, opponent, canPlay, null, null);
    }

    // roundStart / roundEnd: 5c8120 keeps them at the tournament object +0x230 / +0x238; the menu shows the round time when
    // both are set and the tournament room's stRoundEndTime shows +0x238 (4ca869, notes-v191 / v193). null -> 0.
    public S2CTournamentMyInfoAnswerPacket(int tournamentId, byte myState, byte group, String opponent, boolean canPlay, Date roundStart, Date roundEnd) {
        super((char) 0x26BF);

        this.write((byte) 0);
        this.write(tournamentId);
        this.write(myState);
        this.write((byte) 0);
        this.write("");
        this.write(opponent == null ? "" : opponent.length() > 12 ? opponent.substring(0, 12) : opponent);
        this.write("");
        this.write(group);
        this.write((byte) (canPlay ? 1 : 0));
        TournamentRecordWriter.time(this, roundStart);
        TournamentRecordWriter.time(this, roundEnd);
    }

    public S2CTournamentMyInfoAnswerPacket(byte failure) {
        super((char) 0x26BF);

        this.write(failure);
    }
}
