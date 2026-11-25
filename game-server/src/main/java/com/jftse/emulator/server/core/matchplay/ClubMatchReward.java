package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.manager.ServiceManager;
import lombok.extern.log4j.Log4j2;

// Club points / club gold of a finished match (notes-v401). The native result record carries them (0x1846 +0x48 club points,
// +0x4c club gold: GuiCtrl_GameResultDetail "Club point" / "Club gold"), but their rates were native-server rules that the
// client does not hold. User-approved server rule (2026-11-24) [I]: a club member's finished match adds 10 % of the member's
// total Gold reward to the club gold and 1 club point (2 on a win); a player without a club gets 0 / 0.
@Log4j2
public final class ClubMatchReward {
    public static final int GOLD_PERCENT = 10, POINTS = 1, WIN_POINTS = 2;

    private ClubMatchReward() {}

    public static int gold(int memberGold) {
        return Math.max(0, memberGold) * GOLD_PERCENT / 100;
    }

    public static int points(boolean won) {
        return won ? WIN_POINTS : POINTS;
    }

    /** Credits the player's club (if any) and puts the amounts into the reward for the 0x1846 club fields. */
    public static void apply(long playerId, PlayerReward reward, boolean won) {
        if (reward == null)
            return;
        int gold = gold(reward.getGold()), points = points(won);
        try {
            if (!ServiceManager.getInstance().getGuildService().addMatchReward(playerId, gold, points)) {
                gold = 0;
                points = 0;
            }
        } catch (Exception e) {
            log.warn("club match reward for player {} failed: {}", playerId, e.getMessage());
            gold = 0;
            points = 0;
        }
        reward.setClubGold(gold);
        reward.setClubPoints(points);
    }
}
