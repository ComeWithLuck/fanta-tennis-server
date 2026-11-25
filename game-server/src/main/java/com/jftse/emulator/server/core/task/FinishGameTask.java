package com.jftse.emulator.server.core.task;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.thread.AbstractTask;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class FinishGameTask extends AbstractTask {
    private final FTConnection connection;

    private final GameSession boundSession;

    public FinishGameTask(FTConnection connection) {
        this(connection, connection.getClient() == null ? null : connection.getClient().getActiveGameSession());
    }

    public FinishGameTask(FTConnection connection, GameSession boundSession) {
        this.connection = connection;
        this.boundSession = boundSession;
    }

    @Override
    public void run() {
        // notes-v389 (user-approved host handover, 2026-11-23): a task bound to a client that left the session acts through
        // the session's current host (GameSessionManager.actingConnection).
        final FTConnection connection = GameSessionManager.actingConnection(boundSession, this.connection);
        if (connection == null) return;

        GameSession gameSession = connection.getClient().getActiveGameSession();
        if (gameSession == null) return;

        MatchplayGame game = gameSession.getMatchplayGame();

        if (game != null && !game.getFinished().get()) {
            game.getScheduledFutures().forEach(sf -> sf.cancel(false));
            game.getScheduledFutures().clear();

            game.getHandleable().onEnd(connection.getClient());
        }
    }
}
