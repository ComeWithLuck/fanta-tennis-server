package com.jftse.emulator.server.core.packets.tournament;

import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.service.TournamentBracketService;

import java.util.List;

// S2C 0x26C1 bracket teams (Gui_TournamentMenu 5c6bf0 / Gui_TournamentBracket 5c5e40 -> 5cafd0) and 0x26C3 bracket results
// (5c6cc0 / 5c5f00 -> 5c9d20), notes-v184. Header: u8 result (0x26C1 non-zero / 0x26C3 0xff -> MSG_TOURNEY_MATCH_MADE_NOT_YET),
// u32 tournament id, u8 isFinal, u8 group. Teams: u8 entry type (1 individual), i16 count (= the record's team count), count x
// UTF-16 name (at most 12 characters; an individual entry puts it in the team's second name). Results: i16 teams - 1, then per
// internal node {i8 winner team index or -1, u8 howWin}.
public class S2CTournamentBracketPacket extends Packet {
    public static S2CTournamentBracketPacket teams(int tournamentId, boolean isFinal, int group, List<String> names) {
        S2CTournamentBracketPacket p = new S2CTournamentBracketPacket((char) 0x26C1, tournamentId, isFinal, group);
        p.write((byte) 1);
        p.write((short) names.size());
        for (String name : names)
            p.write(name.length() > 12 ? name.substring(0, 12) : name);
        return p;
    }

    /** Club tournament teams: entry type 0, then per team three names (5cafd0: club, member, partner) (notes-v401). */
    public static S2CTournamentBracketPacket clubTeams(int tournamentId, boolean isFinal, int group, List<String[]> teams) {
        S2CTournamentBracketPacket p = new S2CTournamentBracketPacket((char) 0x26C1, tournamentId, isFinal, group);
        p.write((byte) 0);
        p.write((short) teams.size());
        for (String[] team : teams)
            for (String name : team)
                p.write(name.length() > 12 ? name.substring(0, 12) : name);
        return p;
    }

    public static S2CTournamentBracketPacket results(int tournamentId, boolean isFinal, int group, TournamentBracketService.Bracket b) {
        S2CTournamentBracketPacket p = new S2CTournamentBracketPacket((char) 0x26C3, tournamentId, isFinal, group);
        p.write((short) (b.teams() - 1));
        for (int i = 0; i < b.teams() - 1; i++) {
            p.write((byte) b.winners()[i]);
            p.write((byte) b.hows()[i]);
        }
        return p;
    }

    private S2CTournamentBracketPacket(char id, int tournamentId, boolean isFinal, int group) {
        super(id);
        this.write((byte) 0);
        this.write(tournamentId);
        this.write((byte) (isFinal ? 1 : 0));
        this.write((byte) group);
    }
}
