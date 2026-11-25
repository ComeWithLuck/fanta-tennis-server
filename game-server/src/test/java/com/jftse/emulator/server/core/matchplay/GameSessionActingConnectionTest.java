package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * notes-v389: session tasks (guardian attacks, crystals, match timer, finish) are bound to the client that started them. After
 * the host left (user-approved host handover, 2026-11-23) they must act through the session's current host, else any
 * remaining client, instead of stopping. No Spring, sockets or services.
 */
class GameSessionActingConnectionTest {
    private static FTClient client(FTConnection connection, GameSession activeSession) {
        FTClient c = mock(FTClient.class);
        when(c.getConnection()).thenReturn(connection);
        when(c.getActiveGameSession()).thenReturn(activeSession);
        when(connection.getClient()).thenReturn(c);
        return c;
    }

    @Test
    void boundClientStillInTheSessionKeepsActing() {
        GameSession session = new GameSession();
        FTConnection a = mock(FTConnection.class);
        FTClient starter = client(a, session);
        session.getClients().add(starter);
        assertSame(a, GameSessionManager.actingConnection(session, a));
    }

    @Test
    void departedStarterIsReplacedByTheCurrentHost() {
        GameSession session = new GameSession();
        FTConnection a = mock(FTConnection.class), b = mock(FTConnection.class), c = mock(FTConnection.class);
        client(a, null); // left: its active game session was cleared
        FTClient other = client(c, session), host = client(b, session);
        session.getClients().add(other);
        session.getClients().add(host);
        session.setHostClient(host);
        assertSame(b, GameSessionManager.actingConnection(session, a));
    }

    @Test
    void withoutAHostAnyRemainingClientActsAndNobodyLeftStopsTheTask() {
        GameSession session = new GameSession();
        FTConnection a = mock(FTConnection.class), c = mock(FTConnection.class);
        client(a, null);
        FTClient other = client(c, session);
        session.getClients().add(other);
        assertSame(c, GameSessionManager.actingConnection(session, a));
        session.getClients().clear();
        assertNull(GameSessionManager.actingConnection(session, a));
        assertNull(GameSessionManager.actingConnection(null, a));
    }
}
