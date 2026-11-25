package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.common.service.ConfigService;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.handler.lobby.room.*;
import com.jftse.emulator.server.core.handler.matchplay.RoomStartGamePacketHandler;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.shared.packets.lobby.room.*;
import com.jftse.server.core.shared.packets.matchplay.CMSGStartGame;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomAccessPolicyTest {
    private GameManager oldManager,manager;
    private ServiceManager oldServices,services;
    private ConfigService oldConfig,config;
    private Room room;
    private FTClient owner,guest;

    @BeforeEach void setup() {
        oldManager=GameManager.getInstance();oldServices=ServiceManager.getInstance();oldConfig=ConfigService.getInstance();
        manager=mock(GameManager.class);services=mock(ServiceManager.class);config=mock(ConfigService.class);
        ReflectionTestUtils.setField(GameManager.class,"instance",manager);
        ReflectionTestUtils.setField(ServiceManager.class,"instance",services);
        ReflectionTestUtils.setField(ConfigService.class,"instance",config);
        when(manager.getClientsInRoom(anyShort())).thenReturn(List.of());
        when(config.getValue("game.map.allow.snowmoon",false)).thenReturn(false);
        room=new Room();room.setMode((byte)GameMode.BASIC);room.setPlayers((byte)2);room.setRoomName("original");room.setMap((byte)1);room.setPrivate(true);room.setPassword("old");
        owner=participant(1,0,true);guest=participant(2,1,false);
    }
    @AfterEach void restore() {
        ReflectionTestUtils.setField(GameManager.class,"instance",oldManager);
        ReflectionTestUtils.setField(ServiceManager.class,"instance",oldServices);
        ReflectionTestUtils.setField(ConfigService.class,"instance",oldConfig);
    }
    private FTClient participant(long id,int slot,boolean master) {
        FTPlayer player=mock(FTPlayer.class);when(player.getId()).thenReturn(id);
        FTClient client=new FTClient();client.refreshPlayer(player);client.setActiveRoom(room);
        FTConnection connection=mock(FTConnection.class);when(connection.getClient()).thenReturn(client);client.setConnection(connection);
        RoomPlayer rp=new RoomPlayer(player);rp.setPosition((short)slot);rp.setMaster(master);room.getRoomPlayerList().add(rp);return client;
    }
    private List<Consumer<FTConnection>> edits() {
        return List.of(
            c->new RoomNameChangePacketHandler().handle(c,CMSGRoomChangeName.builder().roomName("new").build()),
            c->new RoomIsPrivateChangePacketHandler().handle(c,CMSGRoomChangeIsPrivate.builder().password("new-key").build()),
            c->new RoomMapChangeRequestPacketHandler().handle(c,CMSGRoomChangeMap.builder().map((byte)3).build()),
            c->new RoomLevelRangeChangePacketHandler().handle(c,CMSGRoomChangeLevelRange.builder().levelRange((byte)5).build()),
            c->new RoomSkillFreeChangePacketHandler().handle(c,CMSGRoomChangeSkillFree.builder().enable(true).build()),
            c->new RoomQuickSlotChangePacketHandler().handle(c,CMSGRoomChangeQuickSlot.builder().enable(true).build()),
            c->new RoomAllowBattlemonChangePacketHandler().handle(c,CMSGRoomChangeAllowBattlemon.builder().allowBattlemon((byte)1).build())
        );
    }
    private void unchanged() {
        assertEquals("original",room.getRoomName());assertEquals("old",room.getPassword());assertTrue(room.isPrivate());assertEquals(1,room.getMap());
        assertEquals(0,room.getLevelRange());assertFalse(room.isSkillFree());assertFalse(room.isQuickSlot());assertEquals(0,room.getAllowBattlemon());
    }
    @Test void allSevenOptionHandlersRejectNonownersAndEveryActivePhaseWithoutBroadcast() {
        for(var edit:edits())edit.accept(guest.getConnection());unchanged();
        for(int phase:new int[]{RoomStatus.StartingGame,RoomStatus.InitializingGame,RoomStatus.AnimationSkipped,RoomStatus.Running,RoomStatus.RelayConnectionSuccess}) {
            room.setStatus(phase);for(var edit:edits())edit.accept(owner.getConnection());unchanged();
        }
        verifyNoInteractions(manager);
    }
    @Test void allSevenOwnerOptionsStillMutateNormallyInAnIdleRoom() {
        for(var edit:edits())edit.accept(owner.getConnection());
        assertEquals("new",room.getRoomName());assertEquals("new-key",room.getPassword());assertEquals(3,room.getMap());assertEquals(5,room.getLevelRange());
        assertTrue(room.isSkillFree());assertTrue(room.isQuickSlot());assertEquals(1,room.getAllowBattlemon());
    }
    @Test void membershipAndSpectatorSlotsCannotImpersonateTheOwner() {
        FTClient outsider=participant(99,9,true);assertFalse(RoomAccessPolicy.mayConfigure(outsider,room));assertFalse(RoomAccessPolicy.mayReady(outsider,room));
        room.getRoomPlayerList().remove(outsider.getRoomPlayer());assertFalse(RoomAccessPolicy.mayConfigure(outsider,room));
        outsider.setActiveRoom(null);assertFalse(RoomAccessPolicy.mayConfigure(outsider,room));assertFalse(RoomAccessPolicy.mayConfigure(new FTClient(),room));
        room.getRoomPlayerList().remove(owner.getRoomPlayer());assertFalse(RoomAccessPolicy.mayConfigure(owner,room));
    }
    @Test void singlesRequireTheOwnerAndOneReadyOpponentWithNoFittingPlayer() {
        assertFalse(RoomAccessPolicy.mayStart(owner,room));guest.getRoomPlayer().setReady(true);assertTrue(RoomAccessPolicy.mayStart(owner,room));
        assertFalse(RoomAccessPolicy.mayStart(guest,room));owner.getRoomPlayer().setFitting(true);assertFalse(RoomAccessPolicy.mayStart(owner,room));
        owner.getRoomPlayer().setFitting(false);guest.getRoomPlayer().setFitting(true);assertFalse(RoomAccessPolicy.mayStart(owner,room));
        guest.getRoomPlayer().setFitting(false);room.getRoomPlayerList().remove(guest.getRoomPlayer());assertFalse(RoomAccessPolicy.mayStart(owner,room));
    }
    @Test void doublesRequireAllFourUniquePlayingSlotsAndIgnoreSpectatorsReadiness() {
        room.setPlayers((byte)4);guest.getRoomPlayer().setReady(true);assertFalse(RoomAccessPolicy.mayStart(owner,room));
        FTClient third=participant(3,2,false),fourth=participant(4,3,false);third.getRoomPlayer().setReady(true);assertFalse(RoomAccessPolicy.mayStart(owner,room));
        fourth.getRoomPlayer().setReady(true);assertTrue(RoomAccessPolicy.mayStart(owner,room));participant(5,5,false);assertTrue(RoomAccessPolicy.mayStart(owner,room));
        fourth.getRoomPlayer().setPosition((short)2);assertFalse(RoomAccessPolicy.mayStart(owner,room));
    }
    @Test void guardianCanStartWithOneHumanAndOwnershipTransferUsesCurrentRoomRoles() {
        room.setMode((byte)GameMode.GUARDIAN);room.setPlayers((byte)4);room.getRoomPlayerList().remove(guest.getRoomPlayer());assertTrue(RoomAccessPolicy.mayStart(owner,room));
        RoomPlayer rp=owner.getRoomPlayer();rp.setMaster(false);assertFalse(RoomAccessPolicy.mayConfigure(owner,room));rp.setMaster(true);assertTrue(RoomAccessPolicy.mayConfigure(owner,room));
    }
    @Test void rejectedStartsAnswerTheNativeRefusalCodeAndCannotChangeRoomPhaseOrCreateASession() {
        RoomStartGamePacketHandler handler=new RoomStartGamePacketHandler();
        // owner: the guest is not Ready -> -3 MSG_EVERYBODY_MUST_PRESS_READY; guest: not the master -> -1 MSG_NOT_ROOM_MASTER (4cc6d0).
        java.util.Map<FTClient,Short> expected=java.util.Map.of(owner,(short)-3,guest,(short)-1);
        for(FTClient requester:List.of(owner,guest)) {
            handler.handle(requester.getConnection(),CMSGStartGame.builder().build());assertEquals(RoomStatus.NotRunning,room.getStatus());assertNull(requester.getGameSessionId());
            ArgumentCaptor<Packet> packet=ArgumentCaptor.forClass(Packet.class);verify(requester.getConnection()).sendTCP(packet.capture());
            assertEquals((char)0x17e6,packet.getValue().getPacketId());assertEquals(Character.valueOf((char)(short)expected.get(requester)),packet.getValue().read(Character.class));
        }
    }
    @Test void startRefusalCodesFollowTheNativeTexts() {
        // Basic singles: alone -> -2 need more players; guest seated but not Ready -> -3; guest in a fitting room -> -3; ready -> allowed.
        room.getRoomPlayerList().remove(guest.getRoomPlayer());assertEquals(RoomAccessPolicy.START_NEED_MORE_PLAYERS,RoomAccessPolicy.startRefusal(owner,room));
        room.getRoomPlayerList().add(guest.getRoomPlayer());assertEquals(RoomAccessPolicy.START_EVERYBODY_MUST_PRESS_READY,RoomAccessPolicy.startRefusal(owner,room));
        guest.getRoomPlayer().setReady(true);guest.getRoomPlayer().setFitting(true);assertEquals(RoomAccessPolicy.START_EVERYBODY_MUST_PRESS_READY,RoomAccessPolicy.startRefusal(owner,room));
        guest.getRoomPlayer().setFitting(false);assertEquals(RoomAccessPolicy.START_ALLOWED,RoomAccessPolicy.startRefusal(owner,room));
        assertEquals(RoomAccessPolicy.START_NOT_ROOM_MASTER,RoomAccessPolicy.startRefusal(guest,room));
        // Two players on one slot -> -5 invalid teams; an odd capacity -> -4 invalid number of users.
        guest.getRoomPlayer().setPosition((short)0);assertEquals(RoomAccessPolicy.START_INVALID_TEAMS,RoomAccessPolicy.startRefusal(owner,room));
        guest.getRoomPlayer().setPosition((short)1);room.setPlayers((byte)3);assertEquals(RoomAccessPolicy.START_INVALID_NUM_USERS,RoomAccessPolicy.startRefusal(owner,room));
        // Guardian: one human may start; a not-Ready guest still answers -3.
        room.setPlayers((byte)4);room.setMode((byte)GameMode.GUARDIAN);guest.getRoomPlayer().setReady(false);assertEquals(RoomAccessPolicy.START_EVERYBODY_MUST_PRESS_READY,RoomAccessPolicy.startRefusal(owner,room));
        room.getRoomPlayerList().remove(guest.getRoomPlayer());assertEquals(RoomAccessPolicy.START_ALLOWED,RoomAccessPolicy.startRefusal(owner,room));
        // Not an idle member (room running): the old silent 0.
        room.setStatus(RoomStatus.Running);assertEquals(RoomAccessPolicy.START_REFUSED_SILENT,RoomAccessPolicy.startRefusal(owner,room));
    }
    @Test void readyChangesDuringPlayAreIgnoredAndTheirBusyFlagAlwaysClears() {
        room.setStatus(RoomStatus.Running);new RoomReadyChangeRequestPacketHandler().handle(guest.getConnection(),CMSGRoomChangeReady.builder().ready(true).build());
        assertFalse(guest.getRoomPlayer().isReady());assertFalse(guest.getIsGoingReady().get());verifyNoInteractions(manager);
        room.setStatus(RoomStatus.NotRunning);new RoomReadyChangeRequestPacketHandler().handle(guest.getConnection(),CMSGRoomChangeReady.builder().ready(true).build());assertTrue(guest.getRoomPlayer().isReady());assertFalse(guest.getIsGoingReady().get());
    }
}
