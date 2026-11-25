package com.jftse.emulator.server.core.handler.emblem;

import com.jftse.emulator.common.utilities.ResourceUtil;
import com.jftse.entities.database.model.emblem.EmblemQuestLog;
import com.jftse.server.core.emblem.EmblemCounters;
import com.jftse.server.core.emblem.EmblemQuestData;
import com.jftse.server.core.emblem.EmblemQuestRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** notes-v185: the client's quest data as the server loads it, and the accept / complete / progress rules. */
class EmblemQuestRulesTest {
    static EmblemQuestData data() throws Exception { return EmblemQuestData.load(ResourceUtil.getResource("res/emblem/Ini3_EmblemQuest.xml")); }

    static EmblemCounters counters(int smash, int slice, int charge, int lob, int level) {
        return new EmblemCounters(3, 4, 5, 6, 0, smash, slice, charge, lob, 0, 0, 0, 11, 0, level, false, 0);
    }

    static EmblemQuestLog entry(int index, boolean inProgress, int complete, int... base) {
        EmblemQuestLog e = new EmblemQuestLog();
        e.setQuestIndex(index); e.setInProgress(inProgress); e.setCompleteCount(complete);
        e.setBase1(base.length > 0 ? base[0] : 0); e.setBase2(base.length > 1 ? base[1] : 0);
        e.setBase3(base.length > 2 ? base[2] : 0); e.setBase4(base.length > 3 ? base[3] : 0);
        return e;
    }

    @Test void loadsTheClientData() throws Exception {
        EmblemQuestData d = data();
        assertEquals(148, d.quests().size());
        assertEquals(18, d.quests().stream().filter(q -> q.enable() && q.index() < 1000).count());
        EmblemQuestData.Quest q5 = d.quest(5);
        assertEquals(List.of("Smash", "Slice", "ChargeShot", "Lob"), List.of(q5.conditions()).stream().map(EmblemQuestData.Condition::name).toList());
        assertEquals(9, q5.conditions()[0].type());
        assertEquals(3, q5.level());
        assertEquals(0, q5.gameMode());
        assertEquals(List.of(2, 3), d.quest(14).prereq());
        assertEquals(4, d.quest(6).require().size());
        assertNotNull(d.rewardRow(0, 1));
        assertNull(d.rewardRow(0, 0));
        assertTrue(EmblemQuestData.isEmblem(1000));
        assertFalse(EmblemQuestData.isEmblem(2000));
    }

    @Test void plainConditionsCountFromTheAcceptBaselineTotalsDirectly() throws Exception {
        EmblemQuestData d = data();
        EmblemQuestData.Quest q5 = d.quest(5); // Smash 5, Slice 5, ChargeShot 5, Lob 5
        int[] p = EmblemQuestRules.progress(q5, entry(5, true, 0, 10, 10, 10, 10), counters(13, 20, 10, 9, 3), x -> 0);
        assertArrayEquals(new int[]{3 + 5 + 0 + 0, 20}, p);
        assertArrayEquals(new int[]{0, 20}, EmblemQuestRules.progress(q5, null, counters(13, 20, 10, 9, 3), x -> 0));
        EmblemQuestData.Quest totalSmash = d.quest(1000);
        assertArrayEquals(new int[]{100, 100}, EmblemQuestRules.progress(totalSmash, null, counters(250, 0, 0, 0, 1), x -> 0));
        // pass / fail: CharacterLevel 10 (quest 10), Tutorial 11 (quest 1)
        assertArrayEquals(new int[]{0, 1}, EmblemQuestRules.progress(d.quest(10), null, counters(0, 0, 0, 0, 9), x -> 0));
        assertArrayEquals(new int[]{1, 1}, EmblemQuestRules.progress(d.quest(1), null, counters(0, 0, 0, 0, 1), x -> 0));
        // required items capped at qty (quest 83: 4623 x 5)
        assertArrayEquals(new int[]{5, 5}, EmblemQuestRules.progress(d.quest(83), null, counters(0, 0, 0, 0, 1), x -> 9));
    }

    @Test void acceptGates() throws Exception {
        EmblemQuestData d = data();
        List<EmblemQuestLog> log = new ArrayList<>();
        assertTrue(EmblemQuestRules.canAccept(d.quest(5), log, 3));
        assertFalse(EmblemQuestRules.canAccept(d.quest(5), log, 2), "level");
        assertFalse(EmblemQuestRules.canAccept(d.quest(1000), log, 50), "emblems are not accepted");
        assertFalse(EmblemQuestRules.canAccept(d.quest(3), log, 50), "prerequisite 2 not completed");
        log.add(entry(2, false, 1));
        assertTrue(EmblemQuestRules.canAccept(d.quest(3), log, 50));
        assertFalse(EmblemQuestRules.canAccept(d.quest(2), log, 50), "completed, not repeatable");
        log.add(entry(4, true, 0)); log.add(entry(8, true, 0)); log.add(entry(9, true, 0));
        assertFalse(EmblemQuestRules.canAccept(d.quest(3), log, 50), "three in progress");
        assertFalse(EmblemQuestRules.canAccept(d.quest(4), log, 50), "already in progress");
    }

    @Test void completeResultCodes() throws Exception {
        EmblemQuestData d = data();
        assertEquals(EmblemQuestRules.INVALID_INDEX, EmblemQuestRules.completeCheck(d.quest(99999), null, 50));
        assertEquals(EmblemQuestRules.INVALID_INDEX, EmblemQuestRules.completeCheck(d.quest(17), null, 50)); // Enable 0
        assertEquals(EmblemQuestRules.LEVEL, EmblemQuestRules.completeCheck(d.quest(5), entry(5, true, 0), 2));
        assertEquals(EmblemQuestRules.NO_ENTRY, EmblemQuestRules.completeCheck(d.quest(5), null, 3));
        assertEquals(EmblemQuestRules.NOT_IN_PROGRESS, EmblemQuestRules.completeCheck(d.quest(5), entry(5, false, 1), 3));
        assertEquals(0, EmblemQuestRules.completeCheck(d.quest(5), entry(5, true, 0), 3));
        assertEquals(0, EmblemQuestRules.completeCheck(d.quest(1020), null, 1), "an emblem needs no entry");
        assertEquals(EmblemQuestRules.HAVE_EMBLEM, EmblemQuestRules.completeCheck(d.quest(1020), entry(1020, false, 1), 1));
        assertTrue(EmblemQuestRules.itemRewards(d.quest(5), entry(5, true, 0)));
        assertFalse(EmblemQuestRules.itemRewards(d.quest(5), entry(5, true, 1)), "quest 5: ItemRewardRepeat No");
        assertTrue(EmblemQuestRules.itemRewards(d.quest(8), entry(8, true, 3)));
    }

    @Test void countersPacketEntriesAreSetTypes() {
        for (int[] e : counters(1, 2, 3, 4, 5).packetEntries())
            assertEquals(0, e[0] % 2, "even type = set (0x493250)");
    }
}
