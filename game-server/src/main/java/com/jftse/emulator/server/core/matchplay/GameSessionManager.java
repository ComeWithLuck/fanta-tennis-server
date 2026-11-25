package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.constants.RoomStatus;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;
import java.util.Comparator;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.server.core.shared.packets.matchplay.SMSGSetHost;
import com.jftse.server.core.shared.packets.matchplay.SMSGSetHostUnknown;

@Service
@Getter
@Log4j2
public class GameSessionManager {
    private static GameSessionManager instance;

    private ConcurrentHashMap<Integer, GameSession> gameSessionList;
    private ConcurrentHashMap<Integer, MatchplayReward> matchplayRewardList;

    @PostConstruct
    public void init() {
        instance = this;
        gameSessionList = new ConcurrentHashMap<>();
        matchplayRewardList = new ConcurrentHashMap<>();

        log.info(this.getClass().getSimpleName() + " initialized");
    }

    public static GameSessionManager getInstance() {
        return instance;
    }

    public Integer addGameSession(GameSession gameSession) {
        Integer id = Integer.parseInt(RandomStringUtils.randomNumeric(5));
        while (gameSessionList.putIfAbsent(id, gameSession) != null) {
            id = Integer.parseInt(RandomStringUtils.randomNumeric(5));
        }
        return id;
    }
    public boolean removeGameSession(Integer gameSessionId, GameSession gameSession) {
        return gameSessionList.remove(gameSessionId, gameSession);
    }

    public GameSession getGameSessionBySessionId(int sessionId) {
        return gameSessionList.get(sessionId);
    }

    public boolean cancelAbandonedBasicSession(Integer id, GameSession session, Room room, short departingPosition) {
        if (id == null || session == null || !session.isBasicMode() || !session.isValid()
                || departingPosition < 0 || departingPosition >= 4) return false;
        synchronized (session) {
            var game = session.getMatchplayGame();
            if (gameSessionList.get(id) != session || !game.getFinished().compareAndSet(false, true)) return false;
            game.getScheduledFutures().forEach(future -> future.cancel(false));
            game.getScheduledFutures().clear();
            session.clearCountDownRunnable();
            session.getFireables().forEach(event -> event.setCancelled(true));
            session.getFireables().clear();
            room.setStatus(RoomStatus.NotRunning);
            for (var client : session.getClients()) {
                if (id.equals(client.getGameSessionId())) client.setActiveGameSession(null);
            }
            session.getClients().clear();
            gameSessionList.remove(id, session);
            // An interrupted match is cancelled; normal onEnd awards/results
            // must never be invoked just because a participant left.
            return true;
        }
    }

    /**
     * A Battle / Guardian session whose last client left (user-approved 2026-11-23, notes-v385): cancelled like
     * cancelAbandonedBasicSession - finished, its scheduled futures, countdown and fireables cancelled, the advanced boss
     * phases ended, the room NotRunning and the session removed from gameSessionList. No onEnd results or rewards.
     */
    public boolean cancelEmptySession(Integer id, GameSession session, Room room) {
        if (id == null || session == null || session.isBasicMode() || !session.isValid()) return false;
        synchronized (session) {
            if (!session.getClients().isEmpty()) return false;
            var game = session.getMatchplayGame();
            if (gameSessionList.get(id) != session || !game.getFinished().compareAndSet(false, true)) return false;
            game.getScheduledFutures().forEach(future -> future.cancel(false));
            game.getScheduledFutures().clear();
            session.clearCountDownRunnable();
            session.getFireables().forEach(event -> event.setCancelled(true));
            session.getFireables().clear();
            if (game instanceof MatchplayGuardianGame guardianGame && guardianGame.isAdvancedBossGuardianMode()
                    && guardianGame.getPhaseManager() != null) {
                guardianGame.getPhaseManager().end();
            }
            session.setHostClient(null);
            if (room != null) room.setStatus(RoomStatus.NotRunning);
            gameSessionList.remove(id, session);
            return true;
        }
    }

    /**
     * The host of a running Battle / Guardian session left (user-approved 2026-11-23, notes-v385): the remaining player with
     * the lowest seat (0..3) becomes host - SetHost(1) + 0x17DA as at start, and the advanced boss phases follow it. Native
     * only flips the host flag on SetHost (4c6770); the web rebinds the guardians at once.
     */
    public FTClient handOverHost(GameSession session, FTClient departing, Room room) {
        if (session == null || !session.isValid() || session.isBasicMode() || room == null) return null;
        synchronized (session) {
            if (session.getMatchplayGame().getFinished().get()) return null;
            FTClient host = session.getHostClient();
            if (host != null && host != departing && session.getClients().contains(host)) return null;
            FTClient next = session.getClients().stream()
                    .filter(c -> c != departing && c.hasPlayer() && c.getConnection() != null)
                    .filter(c -> seat(room, c) >= 0 && seat(room, c) < 4)
                    .min(Comparator.comparingInt(c -> seat(room, c)))
                    .orElse(null);
            session.setHostClient(next);
            if (next == null) return null;
            next.getConnection().sendTCP(SMSGSetHost.builder().result((byte) 1).build());
            next.getConnection().sendTCP(SMSGSetHostUnknown.builder().build());
            if (session.getMatchplayGame() instanceof MatchplayGuardianGame guardianGame && guardianGame.getPhaseManager() != null) {
                guardianGame.getPhaseManager().setHostConnection(next.getConnection());
            }
            log.info("Game session host handed over to player {} (seat {})", next.getPlayer().getId(), seat(room, next));
            return next;
        }
    }

    /**
     * The connection a session task acts through. Tasks are bound to the client that started them (the starter / host);
     * when that client has left the session (a handover, notes-v389), the current host, else any remaining client of the
     * session, takes its place, so the guardian attacks, crystals and the match timer keep running. Null when nobody is left.
     */
    public static com.jftse.emulator.server.net.FTConnection actingConnection(GameSession session, com.jftse.emulator.server.net.FTConnection bound) {
        if (session == null) return null;
        if (bound != null && bound.getClient() != null && bound.getClient().getActiveGameSession() == session) return bound;
        FTClient host = session.getHostClient();
        if (host != null && host.getConnection() != null && host.getActiveGameSession() == session) return host.getConnection();
        return session.getClients().stream()
                .filter(c -> c.getConnection() != null && c.getActiveGameSession() == session)
                .map(FTClient::getConnection)
                .findFirst().orElse(null);
    }

    private static int seat(Room room, FTClient client) {
        return room.getRoomPlayerList().stream()
                .filter(rp -> rp.getPlayerId() == client.getPlayer().getId())
                .mapToInt(RoomPlayer::getPosition)
                .findFirst().orElse(-1);
    }

    public void addMatchplayReward(Room room, Collection<FTClient> clients, Integer sessionId, MatchplayReward matchplayReward) {
        matchplayReward.bindRecipients(room, clients, sessionId);
        matchplayRewardList.put((int) room.getRoomId(), matchplayReward);
    }

    public MatchplayReward getMatchplayReward(int roomId) {
        return matchplayRewardList.get(roomId);
    }

    public void removeMatchplayReward(int roomId) {
        matchplayRewardList.remove(roomId);
    }
    public boolean removeMatchplayReward(int roomId, MatchplayReward expected) {
        return matchplayRewardList.remove(roomId, expected);
    }

    public boolean hasMatchplayReward(int roomId) {
        return matchplayRewardList.containsKey(roomId);
    }
}
