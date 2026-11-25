package com.jftse.emulator.server.core.matchplay;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerReward {
    private int playerPosition;
    private int exp;
    private int gold;
    private Integer baseExp;
    private Integer baseGold;
    private int couplePoints;
    private int rankingPoints;
    // 0x1846 +0x48 / +0x4c: the club points / club gold this match gave the player's club (ClubMatchReward, notes-v401).
    private int clubPoints;
    private int clubGold;
    @Deprecated
    private int productIndex;
    @Deprecated
    private int productAmount;
    private int activeBonuses;

    public int getBaseExp() { return baseExp == null ? exp : baseExp; }
    public int getBaseGold() { return baseGold == null ? gold : baseGold; }
    public int getBonusExp() { return exp - getBaseExp(); }
    public int getBonusGold() { return gold - getBaseGold(); }

    public PlayerReward(int playerPosition) {
        this.playerPosition = playerPosition;
        this.exp = 0;
        this.gold = 0;
        this.couplePoints = 0;
        this.rankingPoints = 0;
        this.productIndex = 0;
        this.productAmount = 0;
        this.activeBonuses = 0;
    }
}
