package com.jftse.server.core.emblem;

import com.jftse.entities.database.model.emblem.EmblemQuestLog;

import java.util.List;
import java.util.function.IntUnaryOperator;

// Server side of the client's quest rules (notes-v185; client addresses from the emblem RE). Pure.
public final class EmblemQuestRules {
    public static final int MAX_IN_PROGRESS = 3; // 0x45da60 / panel gate
    // ConditionType values counted 1/1 (pass / fail): Tutorial 30, BattleMonLevel 31, CharacterLevel 32, IsCouple 37 (0x45e0b5..).
    public static boolean passFail(int type) { return type == 30 || type == 31 || type == 32 || type == 37; }

    // 0x227D failure results (EMBLEMQUEST_COMPLETE_QUEST_RESULT_FAILED_n -> -n).
    public static final byte INVALID_INDEX = -2, LEVEL = -3, NO_ENTRY = -4, HAVE_EMBLEM = -5, NOT_IN_PROGRESS = -6, BAD_PRODUCT = -7,
            NOT_DONE = -9, INVENTORY_FULL = -10;

    private EmblemQuestRules() {}

    public static EmblemQuestLog entry(List<EmblemQuestLog> log, int index) {
        return log.stream().filter(e -> e.getQuestIndex() == index).findFirst().orElse(null);
    }

    static int complete(EmblemQuestLog e) { return e == null || e.getCompleteCount() == null ? 0 : e.getCompleteCount(); }

    static boolean inProgress(EmblemQuestLog e) { return e != null && Boolean.TRUE.equals(e.getInProgress()); }

    public static int base(EmblemQuestLog e, int slot) {
        Integer v = switch (slot) {
            case 0 -> e.getBase1();
            case 1 -> e.getBase2();
            case 2 -> e.getBase3();
            default -> e.getBase4();
        };
        return v == null ? 0 : v;
    }

    // done / total of 0x45e090: plain counters against the baseline only while in progress, Total ones directly; pass / fail types 1/1;
    // required items min(held, qty) / qty; WinPercentage 1/1; WorldQuest stages 1 each (JFTSE keeps no world-quest clears: 0 [I]).
    // held(product) = the pocket count usable for that required product.
    public static int[] progress(EmblemQuestData.Quest q, EmblemQuestLog entry, EmblemCounters c, IntUnaryOperator held) {
        int done = 0, total = 0;
        for (int i = 0; i < 4; i++) {
            EmblemQuestData.Condition cond = q.conditions()[i];
            if (cond.type() == 0)
                continue;
            if (passFail(cond.type())) {
                boolean ok = switch (cond.type()) {
                    case 30 -> c.tutorial() >= cond.count();
                    case 31 -> c.battleMonLevel() >= cond.count();
                    case 32 -> c.characterLevel() >= cond.count();
                    default -> c.couple();
                };
                done += ok ? 1 : 0;
                total += 1;
                continue;
            }
            int value = c.value(cond.name(), q.gameMode());
            int raw = cond.total() ? value : inProgress(entry) ? value - base(entry, i) : 0;
            done += Math.max(0, Math.min(raw, cond.count()));
            total += cond.count();
        }
        for (EmblemQuestData.Require r : q.require()) {
            done += Math.min(held.applyAsInt(r.product()), r.qty());
            total += r.qty();
        }
        if (q.winPercentage() > 0) {
            int win = c.value("WinCount", q.gameMode()), lose = c.value("LoseCount", q.gameMode());
            done += win + lose > 0 && win * 100.0 / (win + lose) >= q.winPercentage() ? 1 : 0;
            total += 1;
        }
        total += q.worldQuest().size();
        return new int[]{done, total};
    }

    // Accept (client gates: event 0x82 ignores emblems; panel 0x58a270 enables Accept when in-progress < 3, not (completed and not
    // repeatable), level met; slot state 0 = a prerequisite not completed). The server re-checks all of them [I: native rules].
    public static boolean canAccept(EmblemQuestData.Quest q, List<EmblemQuestLog> log, int level) {
        if (q == null || !q.enable() || EmblemQuestData.isEmblem(q.index()) || level < q.level())
            return false;
        EmblemQuestLog e = entry(log, q.index());
        if (inProgress(e) || complete(e) > 0 && !q.repeat())
            return false;
        if (log.stream().filter(EmblemQuestRules::inProgress).count() >= MAX_IN_PROGRESS)
            return false;
        return q.prereq().stream().allMatch(p -> complete(entry(log, p)) > 0);
    }

    // The 0x227D result before items / pocket space (those need the pocket): 0 or a failure code.
    public static byte completeCheck(EmblemQuestData.Quest q, EmblemQuestLog e, int level) {
        if (q == null || !q.enable())
            return INVALID_INDEX;
        if (level < q.level())
            return LEVEL;
        if (EmblemQuestData.isEmblem(q.index()))
            return complete(e) > 0 ? HAVE_EMBLEM : 0;
        if (e == null)
            return NO_ENTRY;
        if (!inProgress(e))
            return NOT_IN_PROGRESS;
        return 0;
    }

    // Item rewards are given unless the quest was completed before and ItemRewardRepeat is No (panel greys them, GUI_NO_REWARD_REPEAT).
    public static boolean itemRewards(EmblemQuestData.Quest q, EmblemQuestLog e) { return complete(e) == 0 || q.itemRepeat(); }
}
