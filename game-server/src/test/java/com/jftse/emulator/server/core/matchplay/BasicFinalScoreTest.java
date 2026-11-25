package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.life.match.RallyResult;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.core.matchplay.handler.MatchplayBasicModeHandler;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayTeamWinsPoint;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayTeamWinsSet;
import com.jftse.emulator.server.core.rabbit.MatchRallyStatsConsumer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.shared.packets.matchplay.CMSGPoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BasicFinalScoreTest {
    @Test void finalNativePointAndSetReachAllSessionMembersBeforeResultProcessing() {
        GameManager oldManager = GameManager.getInstance(), manager = mock(GameManager.class);
        ServiceManager oldServices = ServiceManager.getInstance(), services = mock(ServiceManager.class);
        ReflectionTestUtils.setField(GameManager.class, "instance", manager);
        ReflectionTestUtils.setField(ServiceManager.class, "instance", services);
        try {
            MatchRallyStatsConsumer stats = mock(MatchRallyStatsConsumer.class);
            when(manager.getMatchRallyStatsConsumer()).thenReturn(stats);
            when(stats.onPoint(anyInt(),anyBoolean())).thenReturn(RallyResult.empty());
            MatchplayBasicGame game = new MatchplayBasicGame((byte)4);
            game.getSetsRedTeam().set(1);game.getSetsBlueTeam().set(1);game.getPointsRedTeam().set(3);
            FTClient client = mock(FTClient.class);FTConnection connection = mock(FTConnection.class);
            GameSession session = mock(GameSession.class);
            when(client.getActiveGameSession()).thenReturn(session);when(session.getPlayers()).thenReturn(4);
            when(client.getActiveRoom()).thenReturn(new Room());when(client.getGameSessionId()).thenReturn(71);
            when(client.getConnection()).thenReturn(connection);
            List<Object> sequence = new ArrayList<>();
            doAnswer(call->{sequence.add(call.getArgument(0));return null;}).when(manager).sendPacketToAllClientsInSameGameSession(any(),eq(connection));
            MatchplayBasicModeHandler handler = new MatchplayBasicModeHandler(game) {
                @Override public void onEnd(FTClient ignored) { sequence.add("result"); }
            };
            handler.onPoint(client,CMSGPoint.builder().pointsTeam((byte)0).unk0((byte)0).ballState((byte)4).playerPosition((byte)4).build());
            assertTrue(game.getFinished().get());assertEquals(3,sequence.size());
            assertInstanceOf(S2CMatchplayTeamWinsPoint.class,sequence.get(0));
            S2CMatchplayTeamWinsSet sets = assertInstanceOf(S2CMatchplayTeamWinsSet.class,sequence.get(1));
            assertEquals(2,sets.getData()[0]);assertEquals(1,sets.getData()[1]);assertEquals("result",sequence.get(2));
        } finally {
            ReflectionTestUtils.setField(GameManager.class,"instance",oldManager);
            ReflectionTestUtils.setField(ServiceManager.class,"instance",oldServices);
        }
    }
}
