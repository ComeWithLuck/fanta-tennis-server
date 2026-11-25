package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.constants.PacketEventType;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.Fireable;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.protocol.Packet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BasicSessionLifecycleTest {
    private GameSessionManager previous, manager;
    private final EventHandler events = new EventHandler();

    @BeforeEach void setup() {
        previous = GameSessionManager.getInstance();
        manager = new GameSessionManager(); manager.init(); events.init();
    }
    @AfterEach void restore() { ReflectionTestUtils.setField(GameSessionManager.class, "instance", previous); }

    private GameSession session(FTClient... clients) {
        GameSession session = new GameSession();
        MatchplayBasicGame game = mock(MatchplayBasicGame.class);
        when(game.getFinished()).thenReturn(new AtomicBoolean());
        when(game.getScheduledFutures()).thenReturn(new ConcurrentLinkedDeque<>());
        session.setMatchplayGame(game);
        int id = manager.addGameSession(session);
        for (FTClient client : clients) { client.setActiveGameSession(id); session.getClients().add(client); }
        return session;
    }

    private FTClient client(Room room) {
        FTClient client = new FTClient(); client.setActiveRoom(room); client.setConnection(mock(FTConnection.class)); return client;
    }

    @Test void participantExitCancelsSessionTasksAndDetachesEveryPeerWithoutAwards() {
        Room room = new Room(); room.setStatus(RoomStatus.Running);
        FTClient host = client(room), guest = client(room); GameSession session = session(host, guest);
        int id = host.getGameSessionId();
        ScheduledFuture<?> future = mock(ScheduledFuture.class); session.getMatchplayGame().getScheduledFutures().add(future);
        Fireable event = mock(Fireable.class); session.getFireables().add(event);
        assertTrue(manager.cancelAbandonedBasicSession(id, session, room, (short) 1));
        assertNull(manager.getGameSessionBySessionId(id)); assertNull(host.getGameSessionId()); assertNull(guest.getGameSessionId());
        assertTrue(session.getClients().isEmpty()); assertTrue(session.getFireables().isEmpty());
        assertEquals(RoomStatus.NotRunning, room.getStatus()); verify(future).cancel(false); verify(event).setCancelled(true);
        verify(session.getMatchplayGame(), never()).getMatchRewards(); assertTrue(manager.getMatchplayRewardList().isEmpty());
        assertFalse(manager.cancelAbandonedBasicSession(id, session, room, (short) 1));
    }

    @Test void spectatorExitAndCompletedGameDoNotCancelAnActiveOrAwardedSession() {
        Room room = new Room(); FTClient host = client(room); GameSession session = session(host); int id = host.getGameSessionId();
        assertFalse(manager.cancelAbandonedBasicSession(id, session, room, (short) 4)); assertSame(session, host.getActiveGameSession());
        session.getMatchplayGame().getFinished().set(true);
        assertFalse(manager.cancelAbandonedBasicSession(id, session, room, (short) 0)); assertSame(session, host.getActiveGameSession());
    }

    @Test void delayedServeCannotFireAfterCancellationButOrdinarySessionDeliveryWorks() {
        Room room = new Room(); FTClient host = client(room); GameSession session = session(host); Packet serve = new Packet((char) 0x183e);
        var current = events.createMatchPacketEvent(host, serve, PacketEventType.FIRE_DELAYED, 6000, true);
        current.fire(); verify(host.getConnection()).sendTCP(serve);
        var stale = events.createMatchPacketEvent(host, serve, PacketEventType.FIRE_DELAYED, 6000, true);
        assertTrue(manager.cancelAbandonedBasicSession(host.getGameSessionId(), session, room, (short) 0));
        stale.fire(); verify(host.getConnection(), times(1)).sendTCP(serve);
    }

    @Test void normalResultAndReturnStillArriveAfterSessionIsDetached() {
        Room room = new Room(); FTClient host = client(room); session(host); Packet result = new Packet((char) 0x1848), back = new Packet((char) 0x1780);
        var resultEvent = events.createMatchPacketEvent(host, result, PacketEventType.DEFAULT, 0);
        var returnEvent = events.createMatchPacketEvent(host, back, PacketEventType.FIRE_DELAYED, 12000);
        host.setActiveGameSession(null); resultEvent.fire(); returnEvent.fire();
        verify(host.getConnection()).sendTCP(result); verify(host.getConnection()).sendTCP(back);
    }

    @Test void anOldReturnCannotInterruptARematchInTheSameRoom() {
        Room room = new Room(); FTClient host = client(room); session(host); Packet back = new Packet((char) 0x1780);
        var stale = events.createMatchPacketEvent(host, back, PacketEventType.FIRE_DELAYED, 12000);
        host.setActiveGameSession(null); session(host); stale.fire(); verifyNoInteractions(host.getConnection());
    }

    @Test void leavingAndRejoiningTheExactSameRoomObjectInvalidatesOldReturnPackets() {
        Room room = new Room(); FTClient host = client(room); session(host); Packet back = new Packet((char) 0x1780);
        var stale = events.createMatchPacketEvent(host, back, PacketEventType.FIRE_DELAYED, 12000);
        host.setActiveGameSession(null); host.setActiveRoom(null); host.setActiveRoom(room);
        stale.fire(); verifyNoInteractions(host.getConnection());
    }
}
