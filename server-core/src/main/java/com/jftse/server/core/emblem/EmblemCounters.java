package com.jftse.server.core.emblem;

// The character counters the quest conditions read (client 0x45e090, jump table 0x45e8a0), from what JFTSE records (notes-v185).
// win / lose: PlayerStatistic basic / battle records (client: stats record of the quest's GameMode; BETTING has no JFTSE record [I]).
// Shots: PlayerStatistic. tutorial: tutorials cleared (client byte [0x7b2b28], +1 per lesson cleared in order 0x549414) [I].
// battleMonLevel: the highest pet level (client: the pet's +0x54) [I]. Fishes / Fruits / Transmutes / Furniture / PerfectGame /
// enchant counts are not recorded by JFTSE: 0 [I].
public record EmblemCounters(int basicWin, int basicLose, int battleWin, int battleLose, int guardBreak, int smash, int slice,
                             int chargeShot, int lob, int skillShot, int serviceAce, int returnAce, int tutorial, int battleMonLevel,
                             int characterLevel, boolean couple, int couplePoint) {
    public static final int BASIC = 0, BATTLE = 1;

    // The counter of a condition name (Total and plain read the same counter; the plain one is taken against the accept baseline).
    public int value(String name, int gameMode) {
        String base = name.startsWith("Total") ? name.substring(5) : name;
        return switch (base) {
            case "WinCount" -> gameMode == BASIC ? basicWin : gameMode == BATTLE ? battleWin : 0;
            case "LoseCount" -> gameMode == BASIC ? basicLose : gameMode == BATTLE ? battleLose : 0;
            case "GuardBreak" -> guardBreak;
            case "Smash" -> smash;
            case "Slice" -> slice;
            case "ChargeShot" -> chargeShot;
            case "Lob" -> lob;
            case "SkillShot" -> skillShot;
            case "ServiceAce" -> serviceAce;
            case "ReturnAce" -> returnAce;
            case "CouplePoint" -> couplePoint;
            default -> 0;
        };
    }

    // S2C 0x2292 "set" entries (even type, table 0x493250): GuardBreak 6, Smash 10, Slice 12, ChargeShot 14, Lob 16, SkillShot 18,
    // ServiceAce 20, ReturnAce 22. Fishes 24 / Fruits 26 / Transmutes 28 / enchant 34, 36 stay unsent (no JFTSE record) [I].
    public int[][] packetEntries() {
        return new int[][]{{6, guardBreak}, {10, smash}, {12, slice}, {14, chargeShot}, {16, lob}, {18, skillShot}, {20, serviceAce},
                {22, returnAce}};
    }
}
