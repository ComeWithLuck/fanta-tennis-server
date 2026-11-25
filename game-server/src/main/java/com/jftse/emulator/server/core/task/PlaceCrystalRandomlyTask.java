package com.jftse.emulator.server.core.task;

import com.jftse.emulator.server.core.constants.GameFieldSide;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.RunnableEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayPlaceSkillCrystal;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.matchplay.battle.SkillCrystal;
import com.jftse.server.core.thread.AbstractTask;

import java.awt.geom.Point2D;
import java.util.concurrent.atomic.AtomicInteger;

public class PlaceCrystalRandomlyTask extends AbstractTask {
    private final FTConnection connection;
    private final short gameFieldSide;
    // The match this chain belongs to. A chain from an earlier match must not continue in the client's next session
    // (a Guardian session's intervals are 0 until onStart, so a carried chain looped every tick).
    private final GameSession gameSession;

    private final EventHandler eventHandler;

    public PlaceCrystalRandomlyTask(FTConnection connection, short gameFieldSide) {
        this(connection, gameFieldSide, connection.getClient() == null ? null : connection.getClient().getActiveGameSession());
    }

    public PlaceCrystalRandomlyTask(FTConnection connection) {
        this(connection, (short) -1);
    }

    public PlaceCrystalRandomlyTask(FTConnection connection, short gameFieldSide, GameSession gameSession) {
        this.connection = connection;
        this.gameFieldSide = gameFieldSide;
        this.gameSession = gameSession;

        eventHandler = GameManager.getInstance().getEventHandler();
    }

    @Override
    public void run() {
        // notes-v389 (user-approved host handover, 2026-11-23): a task bound to a client that left the session acts through
        // the session's current host (GameSessionManager.actingConnection).
        final FTConnection connection = GameSessionManager.actingConnection(gameSession, this.connection);
        if (connection == null) return;

        MatchplayGame game = gameSession.getMatchplayGame();
        if (game == null || game.getFinished().get()) return;
        boolean isBattleGame = gameSession.getMatchplayGame() instanceof MatchplayBattleGame;

        Point2D point = isBattleGame ? this.getRandomPoint(gameFieldSide) : this.getRandomPoint();

        AtomicInteger lastCrystalId = isBattleGame ?
                ((MatchplayBattleGame) game).getLastCrystalId() :
                ((MatchplayGuardianGame) game).getLastCrystalId();
        int crystalId = lastCrystalId.getAndUpdate(id -> id >= 100 ? 0 : id + 1);

        SkillCrystal skillCrystal = new SkillCrystal(crystalId);
        if (isBattleGame)
            ((MatchplayBattleGame) game).getSkillCrystals().add(skillCrystal);
        else
            ((MatchplayGuardianGame) game).getSkillCrystals().add(skillCrystal);

        S2CMatchplayPlaceSkillCrystal placeSkillCrystal = new S2CMatchplayPlaceSkillCrystal((short) skillCrystal.getId(), point);
        GameManager.getInstance().sendPacketToAllClientsInSameGameSession(placeSkillCrystal, connection);

        RunnableEvent runnableEvent;
        if (isBattleGame)
            runnableEvent = eventHandler.createRunnableEvent(new DespawnCrystalTask(connection, skillCrystal, gameFieldSide, gameSession), ((MatchplayBattleGame) game).getCrystalDeSpawnInterval().get());
        else
            runnableEvent = eventHandler.createRunnableEvent(new DespawnCrystalTask(connection, skillCrystal, (short) -1, gameSession), ((MatchplayGuardianGame) game).getCrystalDeSpawnInterval().get());

        gameSession.getFireables().push(runnableEvent);
        eventHandler.offer(runnableEvent);
    }

    private Point2D getRandomPoint() {
        int negator = (int) (Math.random() * 2) == 0 ? -1 : 1;
        float xPos = (float) (Math.random() * 60) * negator;
        float yPos = (short) (Math.random() * 120) * -1;
        yPos = Math.abs(yPos) < 10 ? -10 : yPos;
        return new Point2D.Float(xPos, yPos);
    }

    private Point2D getRandomPoint(short gameFieldSide) {
        int negator = (int) (Math.random() * 2) == 0 ? -1 : 1;
        float xPos = (float) (Math.random() * 60) * negator;

        float yPos = (short) (Math.random() * 120);
        if (gameFieldSide == GameFieldSide.RedTeam) {
            yPos = (short) (Math.random() * 120) * -1;
            yPos = Math.abs(yPos) < 10 ? -10 : yPos;
        } else if (gameFieldSide == GameFieldSide.BlueTeam) {
            yPos = Math.abs(yPos) < 10 ? 10 : yPos;
        }

        return new Point2D.Float(xPos, yPos);
    }
}
