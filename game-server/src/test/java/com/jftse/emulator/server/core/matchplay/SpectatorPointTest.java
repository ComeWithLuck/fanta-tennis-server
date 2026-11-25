package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.handler.matchplay.MatchplayPointPacketHandler;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.shared.packets.matchplay.CMSGPoint;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class SpectatorPointTest {
    @Test void spectatorAndReservedPositionsCannotAwardPointsButPlayingSeatsStillDispatch() {
        FTConnection connection = mock(FTConnection.class);
        FTClient client = mock(FTClient.class);
        RoomPlayer member = mock(RoomPlayer.class);
        GameSession session = mock(GameSession.class);
        MatchplayGame game = mock(MatchplayGame.class);
        MatchplayHandleable handler = mock(MatchplayHandleable.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getRoomPlayer()).thenReturn(member);
        when(client.getActiveGameSession()).thenReturn(session);
        when(session.getMatchplayGame()).thenReturn(game);
        when(game.getFinished()).thenReturn(new AtomicBoolean(false));
        when(game.getHandleable()).thenReturn(handler);
        CMSGPoint packet = CMSGPoint.builder().pointsTeam((byte)0).unk0((byte)0).ballState((byte)4).playerPosition((byte)4).build();
        var point = new MatchplayPointPacketHandler();
        for (short slot : new short[]{-1,4,5,6,7,8,9}) {
            when(member.getPosition()).thenReturn(slot);
            point.handle(connection, packet);
        }
        verifyNoInteractions(handler);
        for (short slot : new short[]{0,1,2,3}) {
            when(member.getPosition()).thenReturn(slot);
            point.handle(connection, packet);
        }
        verify(handler, times(4)).onPoint(client, packet);
    }
}
