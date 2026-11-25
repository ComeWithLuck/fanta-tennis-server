package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.core.matchplay.event.Fireable;
import com.jftse.emulator.server.core.matchplay.event.RunnableEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.constants.GameMode;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ConcurrentLinkedDeque;

@Getter
@Setter
public class GameSession {
    public GameSession() {
        clients = new ConcurrentLinkedDeque<>();
        fireables = new ConcurrentLinkedDeque<>();
    }

    private MatchplayGame matchplayGame;
    private int players;
    private int lastBallHitByPlayer = -1;
    private long timeLastBallWasHit = -1;
    private int timesCourtChanged = 0;
    private ConcurrentLinkedDeque<FTClient> clients;
    private ConcurrentLinkedDeque<Fireable> fireables;
    private volatile RunnableEvent countDownRunnable;
    private int mode;
    // The client that got SetHost(1) at start (RoomStartGamePacketHandler) or by a handover (GameSessionManager.handOverHost).
    private volatile FTClient hostClient;

    public void setMatchplayGame(MatchplayGame game) {
        this.matchplayGame = game;

        if (matchplayGame instanceof MatchplayBasicGame) {
            mode = GameMode.BASIC;
        } else if (matchplayGame instanceof MatchplayBattleGame) {
            mode = GameMode.BATTLE;
        } else {
            mode = GameMode.GUARDIAN;
        }
    }

    public boolean isBasicMode() {
        return mode == GameMode.BASIC;
    }

    public boolean isBattleMode() {
        return mode == GameMode.BATTLE;
    }

    public boolean isGuardianMode() {
        return mode == GameMode.GUARDIAN;
    }

    public boolean isValid() {
        return matchplayGame != null;
    }

    public FTClient getClientByPlayerId(long playerId) {
        return clients.stream()
                .filter(c -> c.hasPlayer() && c.getPlayer().getId() == playerId)
                .findFirst()
                .orElse(null);
    }

    public void clearCountDownRunnable() {
        if (this.getCountDownRunnable() != null) {
            this.getFireables().remove(this.getCountDownRunnable());
            this.getCountDownRunnable().setCancelled(true);
            this.setCountDownRunnable(null);
        }
    }
}