package com.jftse.emulator.server.core.client;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.entities.database.model.account.Account;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.service.impl.LevelServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FTPlayerLevelTest {
    @Test
    void newlyAwardedPointsReachTheLivePlayerCacheForBothLevelUpdatePaths() {
        ServiceManager previous = ServiceManager.getInstance();
        ReflectionTestUtils.setField(ServiceManager.class, "instance", mock(ServiceManager.class));
        try {
            for (boolean combined : new boolean[]{false, true}) {
                Player entity = new Player();
                entity.setId(1L);
                entity.setPlayerType((byte) 0);
                entity.setLevel((byte) 5);
                Account account = new Account(); account.setId(2L); entity.setAccount(account);
                Pocket pocket = new Pocket(); pocket.setId(3L); entity.setPocket(pocket);
                PlayerStatistic statistic = new PlayerStatistic(); statistic.setId(4L); entity.setPlayerStatistic(statistic);
                FTPlayer player = FTPlayer.init(entity);
                PlayerService persistence = mock(PlayerService.class);
                when(persistence.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));
                LevelServiceImpl levels = new LevelServiceImpl(null, persistence, null);

                levels.setNewLevelStatusPoints((byte) 6, entity);
                assertEquals(1, entity.getStatusPoints().intValue());
                assertEquals(0, player.getStatusPoints(), "reproduces the entity/cache gap before syncing level");
                if (combined) player.syncLevelAndExpPoints(6, 45); else player.syncLevel(6);
                assertEquals(6, player.getLevel());
                assertEquals(1, player.getStatusPoints(), "new points must be spendable by the current game session");
            }
        } finally {
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previous);
        }
    }
}
