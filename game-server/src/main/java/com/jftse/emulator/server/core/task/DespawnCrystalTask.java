package com.jftse.emulator.server.core.task;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.RunnableEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayLetCrystalDisappear;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.matchplay.battle.SkillCrystal;
import com.jftse.server.core.thread.AbstractTask;

import java.util.concurrent.ConcurrentLinkedDeque;

public class DespawnCrystalTask extends AbstractTask {
    private final FTConnection connection;
    private final SkillCrystal skillCrystal;
    private final short gameFieldSide;
    // The match this chain belongs to (see PlaceCrystalRandomlyTask).
    private final GameSession gameSession;

    private final EventHandler eventHandler;

    public DespawnCrystalTask(FTConnection connection, SkillCrystal skillCrystal, short gameFieldSide) {
        this(connection, skillCrystal, gameFieldSide, connection.getClient() == null ? null : connection.getClient().getActiveGameSession());
    }

    public DespawnCrystalTask(FTConnection connection, SkillCrystal skillCrystal) {
        this(connection, skillCrystal, (short) -1);
    }

    public DespawnCrystalTask(FTConnection connection, SkillCrystal skillCrystal, short gameFieldSide, GameSession gameSession) {
        this.connection = connection;
        this.skillCrystal = skillCrystal;
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

        boolean isCrystalStillAvailable =
                isBattleGame && (((MatchplayBattleGame) game).getSkillCrystals().stream().anyMatch(x -> x.getId() == skillCrystal.getId())) ||
                !isBattleGame && (((MatchplayGuardianGame) game).getSkillCrystals().stream().anyMatch(x -> x.getId() == skillCrystal.getId()));

        if (isCrystalStillAvailable) {
            S2CMatchplayLetCrystalDisappear letCrystalDisappearPacket = new S2CMatchplayLetCrystalDisappear((short) skillCrystal.getId());
            GameManager.getInstance().sendPacketToAllClientsInSameGameSession(letCrystalDisappearPacket, connection);

            ConcurrentLinkedDeque<SkillCrystal> skillCrystals = isBattleGame ? ((MatchplayBattleGame) game).getSkillCrystals() : ((MatchplayGuardianGame) game).getSkillCrystals();
            skillCrystals.removeIf(sc -> sc.getId() == skillCrystal.getId());

            RunnableEvent runnableEvent;
            if (isBattleGame)
                runnableEvent = eventHandler.createRunnableEvent(new PlaceCrystalRandomlyTask(connection, gameFieldSide, gameSession), ((MatchplayBattleGame) game).getCrystalSpawnInterval().get());
            else
                runnableEvent = eventHandler.createRunnableEvent(new PlaceCrystalRandomlyTask(connection, (short) -1, gameSession), ((MatchplayGuardianGame) game).getCrystalSpawnInterval().get());

            gameSession.getFireables().push(runnableEvent);
            eventHandler.offer(runnableEvent);
        }
    }
}
