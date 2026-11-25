package com.jftse.emulator.server.core.packets.tournament;

import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.service.TournamentService;

import java.util.Date;

// The tournament record as the client reads it (notes-v176): the full list record of S2C 0x26B0 (parser 5ca470) and the
// header of S2C 0x26AE (5c9040: the same fields from the entry byte through +0x78, without the id and the item tables).
// Times are FILETIME (Packet.write(Date)); a missing time is written as 0 (the client skips 0 times in 5c8c00).
// The unnamed u32 +0x58 / +0x5c / u8 +0x60 / u32 +0x64 and the two 16 x {u32, u32} tables (+0xa4 / +0x124, round items [I])
// are written as 0.
public final class TournamentRecordWriter {
    private TournamentRecordWriter() {}

    static void time(Packet packet, Date date) {
        if (date == null) packet.write(0L);
        else packet.write(date);
    }

    public static void header(Packet packet, Tournament t, byte state) {
        packet.write(t.getEntryType() == null ? (byte) 1 : t.getEntryType());
        packet.write(t.getGameMode() == null ? (byte) 0 : t.getGameMode());
        packet.write(t.getTitle() == null ? "" : t.getTitle());
        time(packet, t.getApplyStart());
        time(packet, t.getApplyEnd());
        time(packet, t.getTotalStart());
        time(packet, t.getTotalEnd());
        packet.write(0);
        packet.write(0);
        packet.write((byte) 0);
        packet.write(0);
        time(packet, t.getRoundTime());
        packet.write(state);
        packet.write(Boolean.TRUE.equals(t.getHasQualifying()));
        packet.write(t.getMaxTeams() == null ? 0 : t.getMaxTeams());
        packet.write(t.getQualifyingGroups() == null ? 0 : t.getQualifyingGroups());
    }

    public static void record(Packet packet, Tournament t, byte state) {
        packet.write(t.getId().intValue());
        header(packet, t, state);
        for (int[] reward : TournamentService.rewards(t.getRewards())) {
            packet.write(reward[0]);
            packet.write(reward[1]);
        }
        for (int i = 0; i < 64; i++)
            packet.write(0);
    }
}
