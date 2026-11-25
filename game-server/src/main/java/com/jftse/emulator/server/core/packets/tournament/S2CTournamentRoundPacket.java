package com.jftse.emulator.server.core.packets.tournament;

import com.jftse.server.core.protocol.Packet;

import java.util.Date;

// Tournament room packets read by the global manager 0x7b5390 (notes-v191):
//  0x2413 (5cb350) round info: u32 tournament id (+0x1c, must name a listed tournament for the room texts), u8, u8, u32 x 4
//    (stored, never read), u8 finished (+0x3a: the room's Ready greyed), u32 max play time in minutes (+0x34, room
//    stGameMaxPlayTime "%s\n%s" GUI_TOURNEY_MAX_PLAY_TIME + a duration).
//  0x2415 (5cb430) score: u32 tournament id, u8 red games won, u8 blue games won (stGameScoreRed / Blue digits 0..8); event 0x90.
//  0x2414 (5cb490) game result: u32 tournament id, u8 finished (FINISHED_END instead of ROUND_END), u8 winning team (0 red,
//    1 blue, other draw), u8 (unused), u8 games needed to win, u8 red, u8 blue; event 0x91 -> cGameResultTourney (58fb80).
// Wait time and play time (notes-v198):
//  0x2410 (Gui_RoomGame 4c8800, "ProcessPacket_9232_ReadyUserGameWaitTime") i16 code: 0 -> u8 auto (+0xca6), FILETIME start
//    (+0xcb0), end (+0xcb8), now (+0xcc0): stGameWaitStart / End (4ca5b3, "%s : %s", local time of day), stGameCurrentTime /
//    stGameRemainTime (4c8b40, now advanced by the room tick 4c9090, remain = end - now as a duration); when remain reaches 0
//    with auto set the client sends C 0x2411 once (4c8cb0). 1 -> all cleared (4c5d90, the labels alone). 2 ->
//    MSG_TOURNEY_FINISHED_MATCH_GAME. Other codes print a debug line.
//  0x203D (Gui_InGame 49ec70) u32 seconds: stGameTimeCount shown and counted down (4a044a); at 0 the client sends C 0x203E once.
public class S2CTournamentRoundPacket extends Packet {
    public static S2CTournamentRoundPacket info(int tournamentId, boolean finished, int maxPlayMinutes) {
        S2CTournamentRoundPacket p = new S2CTournamentRoundPacket((char) 0x2413);
        p.write(tournamentId);
        p.write((byte) 0);
        p.write((byte) 0);
        for (int i = 0; i < 4; i++) p.write(0);
        p.write((byte) (finished ? 1 : 0));
        p.write(maxPlayMinutes);
        return p;
    }

    public static S2CTournamentRoundPacket score(int tournamentId, int red, int blue) {
        S2CTournamentRoundPacket p = new S2CTournamentRoundPacket((char) 0x2415);
        p.write(tournamentId);
        p.write((byte) red);
        p.write((byte) blue);
        return p;
    }

    public static S2CTournamentRoundPacket result(int tournamentId, boolean finished, int winTeam, int need, int red, int blue) {
        S2CTournamentRoundPacket p = new S2CTournamentRoundPacket((char) 0x2414);
        p.write(tournamentId);
        p.write((byte) (finished ? 1 : 0));
        p.write((byte) winTeam);
        p.write((byte) 0);
        p.write((byte) need);
        p.write((byte) red);
        p.write((byte) blue);
        return p;
    }

    public static final short WAIT_TIME = 0, WAIT_CLEAR = 1, WAIT_FINISHED = 2;

    public static S2CTournamentRoundPacket waitTime(boolean auto, Date start, Date end, Date now) {
        S2CTournamentRoundPacket p = new S2CTournamentRoundPacket((char) 0x2410);
        p.write(WAIT_TIME);
        p.write((byte) (auto ? 1 : 0));
        TournamentRecordWriter.time(p, start);
        TournamentRecordWriter.time(p, end);
        TournamentRecordWriter.time(p, now);
        return p;
    }

    public static S2CTournamentRoundPacket waitCode(short code) {
        S2CTournamentRoundPacket p = new S2CTournamentRoundPacket((char) 0x2410);
        p.write(code);
        return p;
    }

    public static S2CTournamentRoundPacket gameTime(int seconds) {
        S2CTournamentRoundPacket p = new S2CTournamentRoundPacket((char) 0x203D);
        p.write(seconds);
        return p;
    }

    private S2CTournamentRoundPacket(char id) {
        super(id);
    }
}
