package com.jftse.server.core.service;

import com.jftse.entities.database.model.emblem.EmblemQuestLog;
import com.jftse.server.core.emblem.EmblemCounters;
import com.jftse.server.core.emblem.EmblemQuestData;

import java.util.List;

// Emblem / quest log (notes-v176 E1, notes-v185 E2).
public interface EmblemQuestService {
    List<EmblemQuestLog> log(long playerId);

    EmblemQuestLog entry(long playerId, int questIndex);

    EmblemQuestLog save(EmblemQuestLog entry);

    EmblemQuestData data();

    EmblemCounters counters(long playerId);

    // C2S 0x2274: starts the quest when the rules allow it (baselines = the plain counters now). Returns whether it changed the log.
    boolean accept(long playerId, int questIndex);

    // C2S 0x2278: stops an in-progress quest (baselines cleared). Returns whether it changed the log.
    boolean abandon(long playerId, int questIndex);

    // After a successful 0x227C: completeCount + 1, not in progress, baselines cleared (an emblem gets its entry here).
    EmblemQuestLog markCompleted(long playerId, int questIndex);

    // E3: the four equipped emblems (quest index or 0), cached; written in the room player record / 0x139D / 0x2297.
    short[] slots(long playerId);

    // C2S 0x2295: keeps only owned emblems (log index 1000..1999 with completeCount > 0, MyInfo 0x45dba0), each once [I]; saves and
    // returns the stored slots.
    short[] applySlots(long playerId, short[] slots);
}
