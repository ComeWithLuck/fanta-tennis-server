package com.jftse.emulator.server.core.service.impl;

import com.jftse.emulator.common.service.ConfigService;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.handler.player.PlayerStatusPointChangePacketHandler;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.singleplay.tutorial.TutorialGame;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.account.Account;
import com.jftse.entities.database.model.player.EquippedItemStats;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.model.tutorial.Tutorial;
import com.jftse.entities.database.model.tutorial.TutorialProgress;
import com.jftse.entities.database.repository.tutorial.TutorialProgressRepository;
import com.jftse.entities.database.repository.tutorial.TutorialRepository;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.service.ItemRewardService;
import com.jftse.server.core.service.LevelService;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.service.impl.LevelServiceImpl;
import com.jftse.server.core.shared.packets.player.CMSGChangePlayerStatPoints;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TutorialLevelPointsTest {
    @Test
    void tutorialLevelUpPublishesAuthoritativeTotalsThenAllowsSpendingThePointInTheSameSession() {
        ServiceManager previousManager = ServiceManager.getInstance();
        ConfigService previousConfig = ConfigService.getInstance();
        ServiceManager manager = mock(ServiceManager.class);
        ConfigService config = mock(ConfigService.class);
        ReflectionTestUtils.setField(ServiceManager.class, "instance", manager);
        ReflectionTestUtils.setField(ConfigService.class, "instance", config);
        try {
            when(config.getValue("player.level.max", 60)).thenReturn(60);
            Player entity = new Player(); entity.setId(1L); entity.setPlayerType((byte) 0);
            entity.setLevel((byte) 5); entity.setExpPoints(40); entity.setGold(900);
            Account account = new Account(); account.setId(2L); entity.setAccount(account);
            Pocket pocket = new Pocket(); pocket.setId(3L); entity.setPocket(pocket);
            PlayerStatistic statistic = new PlayerStatistic(); statistic.setId(4L); entity.setPlayerStatistic(statistic);
            FTPlayer player = FTPlayer.init(entity);
            ReflectionTestUtils.setField(player, "itemStats", new EquippedItemStats());
            PlayerService persistence = mock(PlayerService.class);
            when(manager.getPlayerService()).thenReturn(persistence);
            when(persistence.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(persistence.findWithPocketById(1L)).thenReturn(entity);

            Tutorial tutorial = new Tutorial(); tutorial.setTutorialIndex(3);
            tutorial.setRewardGold(100); tutorial.setRewardExp((byte) 5);
            TutorialProgress progress = new TutorialProgress(); progress.setSuccess(1); progress.setAttempts(1);
            TutorialRepository tutorials = mock(TutorialRepository.class);
            TutorialProgressRepository progresses = mock(TutorialProgressRepository.class);
            when(tutorials.findByTutorialIndex(3)).thenReturn(Optional.of(tutorial));
            when(progresses.findByPlayerIdAndTutorial(1L, tutorial)).thenReturn(Optional.of(progress));
            ItemRewardService rewards = mock(ItemRewardService.class);
            when(rewards.getItemRewardTutorial(tutorial, progress)).thenReturn(List.of());
            when(rewards.prepareRewardItemList(entity, List.of())).thenReturn(List.of());
            when(rewards.getRewardGold(true, 100, true)).thenReturn(100);
            when(rewards.getRewardExp(true, 5, true)).thenReturn(5);
            LevelService levels = mock(LevelService.class);
            when(levels.getLevel(anyInt(), anyInt(), anyByte())).thenReturn((byte) 6);
            LevelServiceImpl award = new LevelServiceImpl(null, persistence, null);
            doAnswer(invocation -> award.setNewLevelStatusPoints(invocation.getArgument(0), invocation.getArgument(1)))
                    .when(levels).setNewLevelStatusPoints(anyByte(), any(Player.class));
            FTClient client = mock(FTClient.class); FTConnection connection = mock(FTConnection.class);
            TutorialGame game = new TutorialGame(3); game.setEndTime(new Date(game.getStartTime().getTime() + 78_000));
            when(client.getActiveTutorialGame()).thenReturn(game); when(client.getPlayer()).thenReturn(player);
            when(client.hasPlayer()).thenReturn(true);
            when(client.getAp()).thenReturn(new AtomicInteger(50)); when(connection.getClient()).thenReturn(client);
            List<IPacket> sent = new ArrayList<>();
            doAnswer(invocation -> {
                for (Object argument : invocation.getArguments()) {
                    if (argument instanceof IPacket[] packets) sent.addAll(Arrays.asList(packets));
                    else sent.add((IPacket) argument);
                }
                return null;
            }).when(connection).sendTCP(any(IPacket.class));

            new TutorialServiceImpl(tutorials, progresses, rewards, levels, persistence).finishGame(connection);

            List<Packet> packets = sent.stream().map(packet -> new Packet(packet.toBytes())).toList();
            assertEquals(List.of(0x2212, 0x1B6E, 0x22B8, 0x1B61), packets.stream().map(packet -> (int) packet.getPacketId()).toList());
            assertEquals(1, packets.get(1).getData()[54]);
            assertEquals(45, ByteBuffer.wrap(packets.get(2).getData()).order(ByteOrder.LITTLE_ENDIAN).getInt(1));
            assertEquals(1000, ByteBuffer.wrap(packets.get(3).getData()).order(ByteOrder.LITTLE_ENDIAN).getInt(4));
            assertEquals(1, player.getStatusPoints());

            new PlayerStatusPointChangePacketHandler().handle(connection, CMSGChangePlayerStatPoints.builder()
                    .strength((byte) 0).stamina((byte) 0).dexterity((byte) 1).willpower((byte) 0).statusPoints((byte) 0).build());
            assertEquals(1, player.getDexterity()); assertEquals(0, player.getStatusPoints());
            assertEquals(1, entity.getDexterity().intValue()); assertEquals(0, entity.getStatusPoints().intValue());
        } finally {
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousManager);
            ReflectionTestUtils.setField(ConfigService.class, "instance", previousConfig);
        }
    }
}
