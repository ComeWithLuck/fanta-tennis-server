package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.manager.RelayManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.shared.packets.relay.CMSGBallAnimation;
import com.jftse.server.core.shared.packets.relay.CMSGPlayerAnimation;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

class SpectatorRelayTest {
    @Test void spectatorPacketsAreDiscardedBeforeTranslationOrRallyStatistics() {
        FTConnection connection = mock(FTConnection.class);
        FTClient spectator = mock(FTClient.class);
        when(connection.getClient()).thenReturn(spectator);
        when(spectator.isSpectator()).thenReturn(true);
        CMSGBallAnimation ball = mock(CMSGBallAnimation.class);
        CMSGPlayerAnimation animation = mock(CMSGPlayerAnimation.class);
        new BallAnimationHandler().handle(connection, ball);
        new PlayerAnimationHandler().handle(connection, animation);
        verifyNoInteractions(ball, animation);
        verify(spectator, never()).getGameSessionId();
    }

    @Test void playingAnimationStillReachesPlayersAndSpectators() {
        RelayManager previous = RelayManager.getInstance(), manager = new RelayManager();
        ReflectionTestUtils.setField(RelayManager.class, "instance", manager);
        try {
            FTClient sender = mock(FTClient.class), viewer = mock(FTClient.class);
            FTConnection source = mock(FTConnection.class), destination = mock(FTConnection.class);
            when(source.getClient()).thenReturn(sender);
            when(sender.getGameSessionId()).thenReturn(Optional.of(71));
            ReflectionTestUtils.setField(manager, "sessionMap", new ConcurrentHashMap<>(Map.of(71, new ConcurrentLinkedDeque<>(List.of(viewer)))));
            when(viewer.getConnection()).thenReturn(destination);
            new PlayerAnimationHandler().handle(source, CMSGPlayerAnimation.builder().playerPosition((char)0).build());
            verify(destination).sendTCP(any(com.jftse.server.core.shared.packets.relay.SMSGPlayerAnimation.class));
        } finally {
            ReflectionTestUtils.setField(RelayManager.class, "instance", previous);
        }
    }
}
