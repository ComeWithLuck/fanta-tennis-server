package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.client.EquippedCardSlots;
import com.jftse.emulator.server.core.client.EquippedSpecialSlots;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplaySetExperienceGainInfoData;
import com.jftse.entities.database.model.home.AccountHome;
import com.jftse.server.core.service.HomeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BasicRewardBreakdownTest {
    @Test void houseBonusRowsPreserveTheExistingRoundedAwardAndNativePacketOffsets() {
        ServiceManager previous=ServiceManager.getInstance(),services=mock(ServiceManager.class);ReflectionTestUtils.setField(ServiceManager.class,"instance",services);
        try {
            HomeService homes=mock(HomeService.class);AccountHome home=new AccountHome();home.setBasicBonusExp((byte)7);home.setBasicBonusGold((byte)11);
            when(services.getHomeService()).thenReturn(homes);when(homes.findAccountHomeByAccountId(anyLong())).thenReturn(home);
            RoomPlayer first=mock(RoomPlayer.class),second=mock(RoomPlayer.class);when(second.getPosition()).thenReturn((short)1);
            when(first.getEquippedSpecialSlots()).thenReturn(new EquippedSpecialSlots(0,0,0,0,0));when(first.getEquippedCardSlots()).thenReturn(new EquippedCardSlots(0,0,0,0,0));
            MatchplayBasicGame game=mock(MatchplayBasicGame.class);when(game.getSetsRedTeam()).thenReturn(new AtomicInteger(2));when(game.getSetsBlueTeam()).thenReturn(new AtomicInteger());when(game.isRedTeam((short)0)).thenReturn(true);
            doCallRealMethod().when(game).addBonusesToRewards(any(),any());PlayerReward reward=new PlayerReward(0);reward.setExp(1001);reward.setGold(900);
            game.addBonusesToRewards(new ConcurrentLinkedDeque<>(List.of(first,second)),List.of(reward));
            assertEquals(306,reward.getExp());assertEquals(285,reward.getGold());assertEquals(286,reward.getBaseExp());assertEquals(257,reward.getBaseGold());
            assertEquals(20,reward.getBonusExp());assertEquals(28,reward.getBonusGold());
            byte[] packet=new S2CMatchplaySetExperienceGainInfoData((byte)1,92,reward,(byte)11,first).toBytes();ByteBuffer data=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);data.position(10);
            for(int expected:new int[]{286,257,20,28,306,285})assertEquals(expected,data.getInt());
            PlayerReward legacy=new PlayerReward(0);legacy.setExp(10);assertEquals(10,legacy.getBaseExp());assertEquals(0,legacy.getBonusExp());
        } finally {ReflectionTestUtils.setField(ServiceManager.class,"instance",previous);}
    }
}
