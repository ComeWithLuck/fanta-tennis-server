package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.task.AutoItemRewardPickerTask;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.item.Product;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.server.core.service.*;
import com.jftse.server.core.shared.packets.matchplay.SMSGPickupItemReward;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchplayRewardClaimsTest {
    private GameSessionManager previous, manager;
    private ServiceManager previousServices, services;
    private final Room room = new Room();
    private final List<FTClient> clients = new ArrayList<>();

    @BeforeEach void setup() {
        previous = GameSessionManager.getInstance();manager = new GameSessionManager();manager.init();
        previousServices = ServiceManager.getInstance();services = mock(ServiceManager.class);
        ReflectionTestUtils.setField(ServiceManager.class,"instance",services);
        client((short)0);client((short)1);
    }
    @AfterEach void restore() {
        ReflectionTestUtils.setField(GameSessionManager.class,"instance",previous);
        ReflectionTestUtils.setField(ServiceManager.class,"instance",previousServices);
    }
    private FTClient client(short position) {
        FTPlayer player = mock(FTPlayer.class);when(player.getId()).thenReturn(10L+position);when(player.getPocketId()).thenReturn(100L+position);
        RoomPlayer rp = mock(RoomPlayer.class);when(rp.getPlayerId()).thenReturn(10L+position);when(rp.getPosition()).thenReturn(position);room.getRoomPlayerList().add(rp);
        FTClient client = new FTClient();client.refreshPlayer(player);client.setActiveRoom(room);client.setActiveGameSession(7);client.setConnection(mock(FTConnection.class));clients.add(client);return client;
    }
    private MatchplayReward reward(int product, int quantity) {
        MatchplayReward reward = new MatchplayReward();
        for(byte slot=0;slot<4;slot++)reward.getSlotRewards().put(slot,new MatchplayReward.ItemReward(product,quantity,1.0));
        manager.addMatchplayReward(room,clients,7,reward);return reward;
    }
    @Test void onePlayerCannotTakeTwoRewardsAndASlotCannotBeTakenTwice() {
        MatchplayReward reward = reward(0,0);
        assertTrue(reward.claimItem(clients.get(0),(byte)0).newlyClaimed());
        var duplicate = reward.claimItem(clients.get(0),(byte)1);assertFalse(duplicate.newlyClaimed());assertEquals(0,duplicate.slot());
        var collision = reward.claimItem(clients.get(1),(byte)0);assertFalse(collision.newlyClaimed());assertEquals(0,collision.reward().getClaimedPlayerPosition());
        assertTrue(reward.claimItem(clients.get(1),(byte)1).newlyClaimed());assertTrue(reward.allEligiblePlayersClaimed());
    }
    @Test void invalidSlotsAndSpectatorsCannotClaim() {
        FTClient spectator = client((short)4);MatchplayReward reward = reward(0,0);
        assertNull(reward.claimItem(spectator,(byte)0));assertNull(reward.claimItem(clients.get(0),(byte)-1));assertNull(reward.claimItem(clients.get(0),(byte)4));
        assertTrue(reward.getSlotRewards().values().stream().noneMatch(item->item.getClaimed().get()));
    }
    @Test void leavingRejoiningOrStartingAnotherMatchInvalidatesOldLoot() {
        MatchplayReward reward = reward(0,0);FTClient first=clients.get(0),second=clients.get(1);
        first.setActiveRoom(null);first.setActiveRoom(room);assertNull(reward.claimAnyItem(first));
        second.setActiveGameSession(null);assertTrue(reward.isEligible(second));
        second.setActiveGameSession(8);assertNull(reward.claimAnyItem(second));assertTrue(reward.allEligiblePlayersClaimed());
    }
    @Test void aLateClaimNotificationCannotReachARecipientInANewSession() {
        MatchplayReward reward = reward(0,0);var claim=reward.claimItem(clients.get(0),(byte)0);
        clients.get(1).setActiveGameSession(8);reward.notifyClaim(claim);
        verify(clients.get(0).getConnection()).sendTCP(any(SMSGPickupItemReward.class));verifyNoInteractions(clients.get(1).getConnection());
    }
    @Test void concurrentManualAndAutomaticPicksStillAwardOnlyOneSlotPerPlayer() throws Exception {
        MatchplayReward reward = reward(0,0);ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            List<Future<MatchplayReward.ItemClaim>> attempts=new ArrayList<>();
            for(int i=0;i<64;i++){byte slot=(byte)(i%4);attempts.add(pool.submit(()->reward.claimItem(clients.get(0),slot)));}
            int awards=0;for(var attempt:attempts)if(attempt.get().newlyClaimed())awards++;
            assertEquals(1,awards);assertFalse(reward.claimAnyItem(clients.get(0)).newlyClaimed());assertTrue(reward.claimAnyItem(clients.get(1)).newlyClaimed());
        } finally { pool.shutdownNow(); }
    }
    @Test void anOldAutomaticTaskCannotClaimOrRemoveTheNextRewardWithTheSameRoomNumber() {
        MatchplayReward old=reward(0,0);AutoItemRewardPickerTask task=new AutoItemRewardPickerTask(new ConcurrentLinkedDeque<>(clients),room.getRoomId());
        MatchplayReward next=reward(0,0);task.run();assertSame(next,manager.getMatchplayReward(room.getRoomId()));
        assertTrue(old.getSlotRewards().values().stream().noneMatch(item->item.getClaimed().get()));
        assertTrue(next.getSlotRewards().values().stream().noneMatch(item->item.getClaimed().get()));assertFalse(manager.removeMatchplayReward(room.getRoomId(),old));
    }
    @Test void automaticPositiveRewardsSaveOnePocketItemPerEligiblePlayerAndCleanUp() {
        ProductService products=mock(ProductService.class);PlayerPocketService pockets=mock(PlayerPocketService.class);PocketService pocketService=mock(PocketService.class);
        when(services.getProductService()).thenReturn(products);when(services.getPlayerPocketService()).thenReturn(pockets);when(services.getPocketService()).thenReturn(pocketService);
        Product product=new Product();product.setProductIndex(123);product.setItem0(1101);product.setCategory("QUICK");product.setUseType("Count");when(products.findProductByProductItemIndex(123)).thenReturn(product);
        when(pocketService.findById(anyLong())).thenAnswer(call->{Pocket pocket=new Pocket();pocket.setId(call.getArgument(0));return pocket;});
        List<PlayerPocket> saved=new ArrayList<>();AtomicInteger ids=new AtomicInteger(900);
        when(pockets.save(any(PlayerPocket.class))).thenAnswer(call->{PlayerPocket item=call.getArgument(0);item.setId((long)ids.incrementAndGet());item.setCreated(new Date());saved.add(item);return item;});
        MatchplayReward reward=reward(123,2);new AutoItemRewardPickerTask(new ConcurrentLinkedDeque<>(clients),room.getRoomId()).run();
        assertEquals(2,saved.size());assertTrue(saved.stream().allMatch(item->item.getItemCount()==2&&item.getItemIndex()==1101));assertEquals(2,saved.stream().map(item->item.getPocket().getId()).distinct().count());
        assertNull(manager.getMatchplayReward(room.getRoomId()));assertTrue(reward.allEligiblePlayersClaimed());
    }
}
