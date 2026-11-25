package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.client.EquippedItemParts;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.handler.lobby.room.*;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.player.EquippedItemStats;
import com.jftse.server.core.shared.packets.lobby.room.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomSeatChangesTest {
    private GameManager previous, manager;
    private Room room;
    private FTClient owner, guest;
    private final List<FTClient> clients = new ArrayList<>();

    @BeforeEach void setup() {
        previous = GameManager.getInstance();
        manager = mock(GameManager.class);
        ReflectionTestUtils.setField(GameManager.class, "instance", manager);
        room = new Room(); room.setPlayers((byte) 4);
        owner = member(1, 0, true); guest = member(2, 1, false);
        when(manager.getClientsInRoom(anyShort())).thenAnswer(call -> List.copyOf(clients));
    }
    @AfterEach void restore() { ReflectionTestUtils.setField(GameManager.class, "instance", previous); }

    private FTClient member(long id, int slot, boolean master) {
        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(id); when(player.getName()).thenReturn("Player" + id);
        when(player.getCoupleName()).thenReturn(""); when(player.getItemStats()).thenReturn(new EquippedItemStats());
        when(player.getItemPartsItemIndex()).thenReturn(new EquippedItemParts(0,0,0,0,0,0,0,0,0,0,0,0,0));
        FTClient client = new FTClient(); client.refreshPlayer(player); client.setActiveRoom(room);
        FTConnection connection = mock(FTConnection.class); when(connection.getClient()).thenReturn(client); client.setConnection(connection);
        RoomPlayer rp = new RoomPlayer(player); rp.setPosition((short)slot); rp.setMaster(master); rp.setReady(true);
        room.getRoomPlayerList().add(rp); room.getPositions().set(slot, RoomPositionState.InUse); clients.add(client); return client;
    }
    private void move(FTClient client, int slot) {
        new RoomPositionChangeRequestPacketHandler().handle(client.getConnection(), CMSGRoomChangePosition.builder().position((short)slot).build());
    }
    private void close(FTClient client, int slot, boolean value) {
        new RoomSlotCloseRequestPacketHandler().handle(client.getConnection(), CMSGRoomCloseSlot.builder().slot((byte)slot).close(value).build());
    }
    private void kick(FTClient client, int slot) {
        new RoomKickPlayerRequestPacketHandler().handle(client.getConnection(), CMSGRoomKickPlayer.builder().position((short)slot).build());
    }
    @Test void movingToAFreeSeatResetsEveryReadyAndKeepsTheSlotMapConsistent() {
        move(guest, 2);
        assertEquals(2, guest.getRoomPlayer().getPosition()); assertEquals(RoomPositionState.Free, room.getPositions().get(1));
        assertEquals(RoomPositionState.InUse, room.getPositions().get(2));
        assertTrue(room.getRoomPlayerList().stream().noneMatch(RoomPlayer::isReady));
        assertFalse(guest.getIsChangingSlot().get()); verify(owner.getConnection()).sendTCP(any(com.jftse.emulator.server.core.packets.lobby.room.S2CRoomPlayerListInformationPacket.class));
    }
    @Test void onlyTheOwnerCanSwapAnOccupiedSeatAndRolesTravelWithThePlayer() {
        move(guest, 0); assertEquals(1, guest.getRoomPlayer().getPosition());
        move(owner, 1); assertEquals(1, owner.getRoomPlayer().getPosition()); assertEquals(0, guest.getRoomPlayer().getPosition());
        assertTrue(owner.getRoomPlayer().isMaster()); assertFalse(guest.getRoomPlayer().isMaster());
        assertFalse(owner.getIsChangingSlot().get());
    }
    @Test void invalidLockedReservedAndFittingMovesCannotAlterMembershipOrStickTheBusyFlag() {
        for (int slot : new int[]{-1,4,9,10,32767}) { move(guest,slot); assertEquals(1,guest.getRoomPlayer().getPosition()); assertFalse(guest.getIsChangingSlot().get()); }
        close(owner,2,true);move(guest,2);assertEquals(1,guest.getRoomPlayer().getPosition());
        guest.getRoomPlayer().setFitting(true);move(guest,3);assertEquals(1,guest.getRoomPlayer().getPosition());
        assertFalse(guest.getIsChangingSlot().get());
    }
    @Test void closingAnOccupiedSlotOrChangingSlotsAsAGuestIsRejectedButAnOwnerCanReopenAnEmptySlot() {
        close(owner,1,true);assertEquals(RoomPositionState.InUse,room.getPositions().get(1));
        close(guest,2,true);assertEquals(RoomPositionState.Free,room.getPositions().get(2));
        close(owner,2,true);assertEquals(RoomPositionState.Locked,room.getPositions().get(2));
        close(owner,2,false);assertEquals(RoomPositionState.Free,room.getPositions().get(2));
        for(int slot:new int[]{-1,4,9,10,127})close(owner,slot,true);
        room.setPlayers((byte)2);room.getPositions().set(2,RoomPositionState.Locked);close(owner,2,false);
        assertEquals(RoomPositionState.Locked,room.getPositions().get(2));assertFalse(owner.getIsClosingSlot().get());
    }
    @Test void activeMatchPhasesRejectMovesSlotChangesAndKicksWithoutBroadcasting() {
        for(int phase:new int[]{RoomStatus.StartingGame,RoomStatus.InitializingGame,RoomStatus.AnimationSkipped,RoomStatus.Running,RoomStatus.RelayConnectionSuccess}){
            room.setStatus(phase);move(owner,1);close(owner,2,true);kick(owner,1);
            assertEquals(0,owner.getRoomPlayer().getPosition());assertEquals(RoomPositionState.Free,room.getPositions().get(2));assertTrue(room.getBannedPlayers().isEmpty());
        }
        verifyNoInteractions(manager);assertFalse(owner.getIsClosingSlot().get());assertFalse(owner.getIsChangingSlot().get());
    }
    @Test void kickingRequiresTheOwnerRejectsSelfAndBansBeforeRemovingTheTarget() {
        kick(guest,0);kick(owner,0);assertTrue(room.getBannedPlayers().isEmpty());verifyNoInteractions(manager);
        doAnswer(call -> {
            assertTrue(Thread.holdsLock(room));assertTrue(room.getBannedPlayers().contains(guest.getPlayer().getId()));
            room.getRoomPlayerList().remove(guest.getRoomPlayer());guest.setActiveRoom(null);return null;
        }).when(manager).handleRoomPlayerChanges(guest.getConnection(),true);
        kick(owner,1);assertNull(guest.getActiveRoom());assertEquals(1,room.getRoomPlayerList().size());
        verify(guest.getConnection()).sendTCP(any(SMSGRoomLeave.class));verify(guest.getConnection()).sendTCP(any(SMSGRoomJoin.class));
    }
    @Test void twoSimultaneousClaimsForOneEmptySeatCannotCreateDuplicatePositions() throws Exception {
        FTClient third=member(3,3,false);CountDownLatch start=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->{start.await();move(guest,2);return true;});
            var b=executor.submit(()->{start.await();move(third,2);return true;});start.countDown();a.get(5,TimeUnit.SECONDS);b.get(5,TimeUnit.SECONDS);
        }
        var slots=room.getRoomPlayerList().stream().map(RoomPlayer::getPosition).toList();
        assertEquals(3,new HashSet<>(slots).size());assertEquals(1,slots.stream().filter(slot->slot==2).count());
        assertFalse(guest.getIsChangingSlot().get());assertFalse(third.getIsChangingSlot().get());
    }
}
