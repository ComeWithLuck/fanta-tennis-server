package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.client.*;
import com.jftse.emulator.server.core.constants.*;
import com.jftse.emulator.server.core.life.room.*;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.*;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.player.EquippedItemStats;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.shared.packets.pet.CMSGRequestPet;
import io.netty.channel.*;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real handler/room/serializers, mocked transport and player data only.
 * No Spring context, persistence, sockets, runtime JAR or service startup. */
class RoomRequestPetPacketHandlerTest {
    private GameManager previous, manager;
    private Room room;
    private FTClient owner, guest;
    private final List<FTClient> clients=new ArrayList<>();
    private final Map<FTConnection,List<List<IPacket>>> sent=new IdentityHashMap<>();
    private final List<Boolean> successfulSendLocks=new ArrayList<>();
    private final Map<FTClient,FTPlayer> players=new IdentityHashMap<>();

    @BeforeEach void setup(){
        previous=GameManager.getInstance();manager=mock(GameManager.class);
        ReflectionTestUtils.setField(GameManager.class,"instance",manager);
        room=new Room();room.setRoomId((short)27);room.setMode((byte)0);room.setPlayers((byte)2);room.setAllowBattlemon((byte)1);
        owner=member(10,0,true);guest=member(20,1,false);
        when(manager.getClientsInRoom(anyShort())).thenAnswer(call->List.copyOf(clients));
        // This allows meaningful REDs against the original generic broadcast,
        // while the candidate sends one ACK+roster composite per recipient.
        doAnswer(call->{IPacket packet=call.getArgument(0);for(FTClient c:List.copyOf(clients))c.getConnection().sendTCP(packet);return null;})
            .when(manager).sendPacketToAllClientsInSameRoom(any(IPacket.class),any(FTConnection.class));
    }
    @AfterEach void restore(){ReflectionTestUtils.setField(GameManager.class,"instance",previous);}
    private FTClient member(long id,int position,boolean master){
        FTPlayer player=mock(FTPlayer.class);when(player.getId()).thenReturn(id);when(player.getName()).thenReturn("Owner"+id);
        when(player.getCoupleName()).thenReturn("");when(player.getItemStats()).thenReturn(new EquippedItemStats());
        when(player.getItemPartsItemIndex()).thenReturn(new EquippedItemParts(0,0,0,0,0,0,0,0,0,0,0,0,0));
        FTClient client=new FTClient();client.refreshPlayer(player);client.setActiveRoom(room);players.put(client,player);
        FTConnection connection=mock(FTConnection.class);when(connection.getClient()).thenReturn(client);client.setConnection(connection);
        sent.put(connection,new ArrayList<>());capture(connection,null);
        RoomPlayer rp=new RoomPlayer(player);rp.setPosition((short)position);rp.setMaster(master);room.getRoomPlayerList().add(rp);
        room.getPositions().set(position,RoomPositionState.InUse);clients.add(client);return client;
    }
    private void capture(FTConnection connection,RuntimeException synchronousFailure){
        doAnswer(call->{
            IPacket[] packets=invocationPackets(call);
            if(packets[0].getPacketId()==0x1d57&&result(packets[0])==0){
                successfulSendLocks.add(Thread.holdsLock(room));if(synchronousFailure!=null)throw synchronousFailure;
            }
            sent.get(connection).add(List.of(packets));
            return new DefaultChannelPromise(mock(Channel.class),ImmediateEventExecutor.INSTANCE).setSuccess();
        }).when(connection).sendTCP(any());
    }
    private PetView select(FTClient client,int type,String name){
        Pet p=new Pet();p.setId(90L+type);p.setType((byte)type);p.setName(name);p.setLevel((byte)12);p.setHp(280);
        p.setStrength((byte)17);p.setStamina((byte)23);p.setDexterity((byte)31);p.setWillpower((byte)47);p.setHunger(141);p.setEnergy(92);
        client.setActivePet(p);return client.getActivePet();
    }
    private PetView select(FTClient client){return select(client,1,"Pet ผู้เล่น");}
    private void request(FTClient client,int slot){new RoomRequestPetPacketHandler().handle(client.getConnection(),CMSGRequestPet.builder().slot((byte)slot).build());}
    private static IPacket[] invocationPackets(org.mockito.invocation.InvocationOnMock call){
        Object[] arguments=call.getArguments();
        return arguments.length==1&&arguments[0] instanceof IPacket[] ? (IPacket[])arguments[0] : Arrays.copyOf(arguments,arguments.length,IPacket[].class);
    }
    private static int result(IPacket p){return p.toBytes()[8]&255;}
    private List<IPacket> packets(FTClient client){return sent.get(client.getConnection()).stream().flatMap(List::stream).toList();}
    private void onlyFailure(FTClient client,int expected){
        var out=packets(client);assertEquals(1,out.size());assertEquals(0x1d57,out.get(0).getPacketId());assertEquals(expected,result(out.get(0)));
        assertEquals(11,out.get(0).toBytes().length);
    }
    private List<IPacket> pair(FTClient client){
        var writes=sent.get(client.getConnection());assertEquals(1,writes.size());var pair=writes.get(0);
        assertEquals(2,pair.size());assertEquals(0x1d57,pair.get(0).getPacketId());assertEquals(0,result(pair.get(0)));assertEquals(0x1394,pair.get(1).getPacketId());return pair;
    }
    private static String string(ByteBuffer b){StringBuilder out=new StringBuilder();for(char c;(c=b.getChar())!=0;)out.append(c);return out.toString();}
    private static Map<Integer,Boolean> rosterPets(IPacket packet){
        ByteBuffer b=ByteBuffer.wrap(packet.toBytes()).order(ByteOrder.LITTLE_ENDIAN);b.position(8);int count=b.getChar();Map<Integer,Boolean> out=new LinkedHashMap<>();
        for(int i=0;i<count;i++){
            int position=b.getShort();string(b);b.get();for(int j=0;j<4;j++)b.get();b.get();b.get();boolean hasPet=b.get()!=0;
            string(b);b.position(b.position()+25);string(b);b.position(b.position()+13+4+8+42+48);
            if(hasPet){string(b);b.position(b.position()+2+4+4+4+4);}out.put(position,hasPet);
        }
        assertFalse(b.hasRemaining());return out;
    }

    @Test void addCommitsActualOwnerAndSendsOneOrderedRealAckRosterPairToEveryPeerUnderLock(){
        PetView chosen=select(owner);request(owner,0);assertSame(chosen,owner.getRoomPlayer().getPet());assertNull(guest.getRoomPlayer().getPet());
        for(FTClient client:List.of(owner,guest)){
            var pair=pair(client);byte[] ack=pair.get(0).toBytes();assertEquals(1,ack[9]);assertEquals(0,ack[10]);
            assertEquals(Map.of(0,true,1,false),rosterPets(pair.get(1)));
        }
        assertFalse(successfulSendLocks.isEmpty());assertTrue(successfulSendLocks.stream().allMatch(Boolean::booleanValue));
    }
    @Test void removalIgnoresAddOnlySelectionAllowAndFreeSlotGuardsAndCarriesTheRemovedPet(){
        PetView old=select(owner);owner.getRoomPlayer().setPet(old);owner.setActivePet(null);room.setAllowBattlemon((byte)0);member(30,2,false);
        request(owner,0);assertNull(owner.getRoomPlayer().getPet());
        byte[] ack=pair(owner).get(0).toBytes();assertEquals(0,ack[9]);assertEquals(0,ack[10]);
        ByteBuffer body=ByteBuffer.wrap(ack).order(ByteOrder.LITTLE_ENDIAN);body.position(11);assertEquals(old.name(),string(body));
        assertEquals(Map.of(0,false,1,false,2,false),rosterPets(pair(guest).get(1)));
    }
    @Test void invalidAndSpoofedSlotsCannotChangeEitherOwnersPet(){
        select(owner);for(int slot:new int[]{-1,1,2,3,127}){
            request(owner,slot);assertNull(owner.getRoomPlayer().getPet());assertNull(guest.getRoomPlayer().getPet());onlyFailure(owner,2);sent.get(owner.getConnection()).clear();
        }
        assertTrue(packets(guest).isEmpty());
    }
    @Test void nonMembersAndMissingPlayersNeverMutateOrBroadcast(){
        select(owner);room.getRoomPlayerList().remove(owner.getRoomPlayer());request(owner,0);assertTrue(packets(guest).isEmpty());
        FTConnection empty=mock(FTConnection.class);new RoomRequestPetPacketHandler().handle(empty,CMSGRequestPet.builder().slot((byte)0).build());
        owner.refreshPlayer(null);request(owner,0);assertTrue(packets(guest).isEmpty());
    }
    @Test void everyNonIdleRoomPhaseRejectsWithoutChangingAnExistingPet(){
        PetView chosen=select(owner);owner.getRoomPlayer().setPet(chosen);
        for(int status:new int[]{RoomStatus.StartingGame,RoomStatus.InitializingGame,RoomStatus.AnimationSkipped,RoomStatus.Running,RoomStatus.RelayConnectionSuccess}){
            room.setStatus(status);request(owner,0);assertSame(chosen,owner.getRoomPlayer().getPet());onlyFailure(owner,2);sent.get(owner.getConnection()).clear();
        }
        assertTrue(packets(guest).isEmpty());
    }
    @Test void unsupportedModeOrCapacityIsAnExplicitFenceForBothAddAndRemoval(){
        PetView chosen=select(owner);
        // Battle (mode 1) two-human rooms admit Pets since 2026-10-31 (user-approved); Guardian (2) stays fenced.
        for(int[] domain:new int[][]{{2,2},{1,4},{0,4},{9,2}}){
            room.setMode((byte)domain[0]);room.setPlayers((byte)domain[1]);request(owner,0);assertNull(owner.getRoomPlayer().getPet());onlyFailure(owner,2);sent.get(owner.getConnection()).clear();
            owner.getRoomPlayer().setPet(chosen);request(owner,0);assertSame(chosen,owner.getRoomPlayer().getPet());onlyFailure(owner,2);sent.get(owner.getConnection()).clear();owner.getRoomPlayer().setPet(null);
        }
    }
    @Test void battleTwoHumanRoomAdmitsAndRemovesThePet(){
        room.setMode((byte)1);PetView chosen=select(owner);
        request(owner,0);assertSame(chosen,owner.getRoomPlayer().getPet());sent.get(owner.getConnection()).clear();
        request(owner,0);assertNull(owner.getRoomPlayer().getPet());
    }
    @Test void addOnlyGuardsKeepTheirSpecificExistingWireResults(){
        room.setAllowBattlemon((byte)0);request(owner,0);onlyFailure(owner,4);sent.get(owner.getConnection()).clear();
        room.setAllowBattlemon((byte)1);request(owner,0);onlyFailure(owner,1);sent.get(owner.getConnection()).clear();
        select(owner);member(30,2,false);request(owner,0);onlyFailure(owner,3);assertNull(owner.getRoomPlayer().getPet());
    }
    @Test void ownerOneUsesCanonicalSlotOneAndCompanionActorThree(){
        PetView chosen=select(guest);request(guest,1);assertSame(chosen,guest.getRoomPlayer().getPet());
        assertEquals(1,pair(owner).get(0).toBytes()[10]);assertEquals(Map.of(0,false,1,true),rosterPets(pair(guest).get(1)));
    }
    @Test void realAckSerializationFailureLeavesTheRoomUnchangedBeforeResultFive(){
        select(owner,1,"X".repeat(20000));request(owner,0);assertNull(owner.getRoomPlayer().getPet());onlyFailure(owner,5);assertTrue(packets(guest).isEmpty());
    }
    @Test void anyRecipientRosterSerializationFailurePreventsCommitAndAllSuccessSends(){
        select(owner);when(players.get(guest).getItemStats()).thenReturn(null);request(owner,0);
        assertNull(owner.getRoomPlayer().getPet());onlyFailure(owner,5);assertTrue(packets(guest).isEmpty());
    }
    @Test void synchronousPostCommitSendFailureDoesNotRollbackOrRejectAndDoesNotSkipOtherPeers(){
        PetView chosen=select(owner);capture(owner.getConnection(),new IllegalStateException("test transport closed"));request(owner,0);
        assertSame(chosen,owner.getRoomPlayer().getPet());assertTrue(packets(owner).isEmpty());assertEquals(Map.of(0,true,1,false),rosterPets(pair(guest).get(1)));
    }
    @Test void failedWriteFutureCannotTurnACommittedChangeIntoRejection(){
        PetView chosen=select(owner);
        doAnswer(call->{IPacket[] packets=invocationPackets(call);sent.get(owner.getConnection()).add(List.of(packets));
            return new DefaultChannelPromise(mock(Channel.class),ImmediateEventExecutor.INSTANCE).setFailure(new IllegalStateException("test asynchronous failure"));
        }).when(owner.getConnection()).sendTCP(any());
        request(owner,0);assertSame(chosen,owner.getRoomPlayer().getPet());pair(owner);pair(guest);
    }
    @Test void rosterPreparationPreservesInvisibleGmPrivacyPerRecipient(){
        FTClient gm=member(99,9,false);select(owner);request(owner,0);
        assertFalse(rosterPets(pair(owner).get(1)).containsKey(9));assertFalse(rosterPets(pair(guest).get(1)).containsKey(9));assertTrue(rosterPets(pair(gm).get(1)).containsKey(9));
    }
    @Test void anotherRoomObjectReusingTheSameRoomIdDoesNotReceiveTheChange(){
        FTClient stale=member(99,5,false);Room other=new Room();other.setRoomId(room.getRoomId());stale.setActiveRoom(other);
        select(owner);request(owner,0);assertTrue(packets(stale).isEmpty());pair(owner);pair(guest);
    }
}
