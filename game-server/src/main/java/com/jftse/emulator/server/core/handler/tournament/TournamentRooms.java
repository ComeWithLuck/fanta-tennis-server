package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.packets.tournament.S2CTournamentRoundPacket;
import com.jftse.emulator.server.core.handler.lobby.room.RoomJoinRequestPacketHandler;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayTeamWinsSet;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.server.core.service.TournamentBrackets;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomInformationPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomPlayerListInformationPacket;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentResultPacket;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemsPlacePacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.service.TournamentBracketService;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreate;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomJoin;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomJoin;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

// Tournament match rooms (notes-v186). Client [C]: btPlay (my state 2, canPlay) sends 0x26C6; the menu then takes a pushed
// 0x138C room join answer (5c7a50: result 0 -> the tournament-room flag [0x7b2324] = 1 and the room scene; else
// MSG_TOURNEY_GAME_ROOM_JOIN_FAILED "%s (%d)"), so the server itself puts the player into the match room. In that room Ready
// is 0x26C8 {u8 ready} (4cbaf6) and seat changes are refused client-side (MSG_TOURNEY_IMPOSSIBLE_CHANGE_ROOM_POS).
// Server rules [I]: the first of the two players makes a private Basic 1 vs 1 room named after the tournament (a random
// password keeps the lobby out); the second one is joined into it. The room's first finished Basic game decides the match
// (TournamentBracketService.record); the room then is an ordinary room.
@Log4j2
public final class TournamentRooms {
    // Room stGameMaxPlayTime (0x2413 +0x34, minutes) [I: the native value is not in the client].
    public static final int MAX_PLAY_MINUTES = 10;
    // The 0x2410 wait period from the room's making (notes-v198) [I: the length is the server's].
    public static final int WAIT_MINUTES = 10;
    // A 0x2411 a little before the end still counts (the client's clock runs from the 0x2410 "now") [I].
    static final long WAIT_SLACK_MS = 5000;

    private TournamentRooms() {}

    /** 4cbcbf: Start does nothing in a tournament room, so the server starts the game once every non-master player is ready
     *  (the ready is 0x26C8) [I]. */
    static void startWhenReady(Room room) {
        if (room == null || room.getTournamentMatchId() == null)
            return;
        var players = room.getRoomPlayerList().stream().filter(p -> p.getPosition() >= 0 && p.getPosition() < 4).toList();
        if (players.size() != room.getPlayers() || players.stream().anyMatch(p -> !p.isMaster() && !p.isReady()))
            return;
        GameManager.getInstance().getClientsInRoom(room.getRoomId()).stream()
                .filter(c -> c.getRoomPlayer() != null && c.getRoomPlayer().isMaster() && c.getConnection() != null).findFirst()
                .ifPresent(c -> new com.jftse.emulator.server.core.handler.matchplay.RoomStartGamePacketHandler()
                        .handle(c.getConnection(), com.jftse.server.core.shared.packets.matchplay.CMSGStartGame.builder().build()));
    }

    /** 0x26C6 for an open match: 0x26C7 0 and the room join, or a 0x26C7 failure code. */
    static void play(FTConnection connection, FTClient client, Tournament t, TournamentBracketService.MyMatch match) {
        if (client.getActiveRoom() != null) {
            connection.sendTCP(new S2CTournamentResultPacket(S2CTournamentResultPacket.PLAY, (byte) -3));
            return;
        }
        // The match's room is found by its binding: JFTSE room ids start at 0, so the stored room id cannot mean "none" (live
        // 2026-11-12: the second player got a room of its own).
        Room room = GameManager.getInstance().getRoomManager().getRooms().stream()
                .filter(r -> Long.valueOf(match.matchId()).equals(r.getTournamentMatchId())).findFirst().orElse(null);
        connection.sendTCP(new S2CTournamentResultPacket(S2CTournamentResultPacket.PLAY, (byte) 0));
        if (room != null) {
            // The join handler sends 0x138C + the room information / roster as for a lobby join.
            new RoomJoinRequestPacketHandler().handle(connection, CMSGRoomJoin.builder().roomId(room.getRoomId()).unk0((byte) 0).password(room.getPassword()).build());
            connection.sendTCP(S2CTournamentRoundPacket.info(t.getId().intValue(), false, MAX_PLAY_MINUTES));
            connection.sendTCP(waitTime(room, System.currentTimeMillis()));
            return;
        }
        if (!client.getIsJoiningOrLeavingRoom().compareAndSet(false, true))
            return;
        try {
            String password = "T" + Integer.toString(ThreadLocalRandom.current().nextInt(1 << 30), 36);
            // A club tournament is 2 vs 2 (notes-v401) [I]: the making member's team takes red (the maker at 0, the partner at
            // 2), the opponent team blue (1, 3).
            boolean club = t.getEntryType() != null && t.getEntryType() == 0;
            TournamentBracketService bs = ServiceManager.getInstance().getTournamentBracketService();
            long[] mine = club ? bs.players(match.myEntry()) : null, other = club ? bs.players(match.opponentEntry()) : null;
            String title = t.getTitle() == null ? "Tournament" : t.getTitle();
            CMSGRoomCreate create = CMSGRoomCreate.builder().roomName(title.length() > 30 ? title.substring(0, 30) : title)
                    .roomType((byte) 0).mode((byte) GameMode.BASIC).rule((byte) 0).players((byte) (club ? 4 : 2)).isPrivate(true).mapId((byte) 1)
                    .skillFree(true).quickSlot(true).levelRange((byte) -1).bettingType((char) 0).bettingAmount(0).ball(1).password(password).build();
            room = GameManager.getInstance().getRoomManager().createRoom(create, client);
            room.setTournamentMatchId(match.matchId());
            room.setTournamentId(t.getId().intValue());
            room.setTournamentWaitStart(System.currentTimeMillis());
            if (club && mine != null && other != null) {
                long me = client.getPlayer().getId(), partner = mine[0] == me ? mine[1] : mine[0];
                java.util.Map<Long, Integer> seats = new java.util.HashMap<>();
                seats.put(me, 0);
                if (partner > 0) seats.put(partner, 2);
                seats.put(other[0], 1);
                if (other[1] > 0) seats.put(other[1], 3);
                room.setTournamentSeats(seats);
            }
            client.setActiveRoom(room);
            client.setInLobby(false);
            ServiceManager.getInstance().getTournamentBracketService().setRoom(match.matchId(), room.getRoomId());
            connection.sendTCP(SMSGRoomJoin.builder().result((char) 0).roomType(room.getRoomType()).mode(room.getMode()).mapId(room.getMap()).build());
            connection.sendTCP(new S2CRoomInformationPacket(room));
            connection.sendTCP(new S2CRoomPlayerListInformationPacket(new ArrayList<>(room.getRoomPlayerList())));
            connection.sendTCP(S2CTournamentRoundPacket.info(t.getId().intValue(), false, MAX_PLAY_MINUTES));
            connection.sendTCP(waitTime(room, System.currentTimeMillis()));
            GameManager.getInstance().updateLobbyRoomListForAllClients(connection);
            GameManager.getInstance().refreshLobbyPlayerListForAllClients();
        } finally {
            client.getIsJoiningOrLeavingRoom().set(false);
        }
    }

    /** 0x2410 code 0 for a match room: auto 1 (the client sends 0x2411 at the end), start = the room's making, end = start +
     *  WAIT_MINUTES, now = the server's time [I: the period is the server's]. */
    static S2CTournamentRoundPacket waitTime(Room room, long nowMs) {
        long start = room.getTournamentWaitStart() == null ? nowMs : room.getTournamentWaitStart();
        return S2CTournamentRoundPacket.waitTime(true, new Date(start), new Date(start + WAIT_MINUTES * 60_000L), new Date(nowMs));
    }

    /** C 0x2411 (4c8cb0: the room's wait time ran out). [I] Server rule: a bound match room that is not playing, whose wait
     *  period is over and holds only this player, gives the match to them by default (howWin 2, GUI_TOURNEY_HOW_WIN_2); with
     *  both players there the ready flow stays as it is. */
    static void waitOver(FTClient client) {
        Room room = client == null ? null : client.getActiveRoom();
        RoomPlayer rp = client == null ? null : client.getRoomPlayer();
        if (room == null || rp == null || !client.hasPlayer() || room.getTournamentMatchId() == null || room.getTournamentId() == null)
            return;
        // Walkover when nobody of the other side is seated (1 vs 1: only this player; club 2 vs 2: only this team) (notes-v401).
        boolean red = rp.getPosition() == 0 || rp.getPosition() == 2;
        long opponents = room.getRoomPlayerList().stream().filter(p -> p.getPosition() >= 0 && p.getPosition() < 4 && (p.getPosition() == 0 || p.getPosition() == 2) != red).count();
        if (!walkoverDue(room.getStatus() == RoomStatus.NotRunning, room.getTournamentWaitStart(), System.currentTimeMillis(),
                opponents + 1, rp.getPosition()))
            return;
        Long matchId = room.getTournamentMatchId();
        TournamentBracketService.Recorded r = ServiceManager.getInstance().getTournamentBracketService()
                .record(matchId, client.getPlayer().getId(), (byte) 0, (byte) 0, TournamentBrackets.HOW_DEFAULT);
        log.info("tournament match {} won by default by player {}: {}", matchId, client.getPlayer().getId(), r.recorded() ? "recorded" : "not recorded");
        if (!r.recorded())
            return;
        room.setTournamentMatchId(null);
        if (client.getConnection() == null)
            return;
        if (!r.rewards().isEmpty())
            client.getConnection().sendTCP(new S2CInventoryItemsPlacePacket(r.rewards()));
        int tid = room.getTournamentId();
        client.getConnection().sendTCP(S2CTournamentRoundPacket.score(tid, red ? 1 : 0, red ? 0 : 1));
        client.getConnection().sendTCP(S2CTournamentRoundPacket.result(tid, r.champion(), red ? 0 : 1, 1, red ? 1 : 0, red ? 0 : 1));
        client.getConnection().sendTCP(S2CTournamentRoundPacket.waitCode(S2CTournamentRoundPacket.WAIT_FINISHED));
    }

    /** waitOver's test: the room is not playing, the period is over (with WAIT_SLACK_MS), one player seated and it is this one. */
    static boolean walkoverDue(boolean notRunning, Long waitStart, long nowMs, long seated, short position) {
        return notRunning && waitStart != null && nowMs + WAIT_SLACK_MS >= waitStart + WAIT_MINUTES * 60_000L && seated == 1
                && position >= 0 && position < 4;
    }

    /** A Basic game starts in a bound room: 0x2410 code 1 clears the room's wait texts and 0x203D starts the in-game count of
     *  the room's max play time (stGameTimeCount) [I: when both are sent is the server's]. */
    public static void onGameStart(Room room, Collection<FTClient> clients) {
        if (room == null || room.getTournamentMatchId() == null)
            return;
        for (FTClient c : clients) {
            if (c.getConnection() == null) continue;
            c.getConnection().sendTCP(S2CTournamentRoundPacket.waitCode(S2CTournamentRoundPacket.WAIT_CLEAR));
            c.getConnection().sendTCP(S2CTournamentRoundPacket.gameTime(MAX_PLAY_MINUTES * 60));
        }
    }

    /** C 0x203E (4a04fc: the in-game count reached 0). [I] Server rule: in a bound room's Basic game the side ahead (sets, then
     *  points of the set) wins it at once - its sets become 2 and the game ends as after a deciding point (recordMatch decides
     *  the bracket match); a level game goes on until a point breaks the tie. Each client sends it: the first one ends it. */
    static void gameTimeOver(FTClient client) {
        Room room = client == null ? null : client.getActiveRoom();
        if (room == null || room.getTournamentMatchId() == null || client.getActiveGameSession() == null)
            return;
        if (!(client.getActiveGameSession().getMatchplayGame() instanceof MatchplayBasicGame game))
            return;
        synchronized (game) {
            if (game.getFinished().get())
                return;
            int winner = timeUpWinner(game.getSetsRedTeam().get(), game.getSetsBlueTeam().get(), game.getPointsRedTeam().get(), game.getPointsBlueTeam().get());
            if (winner < 0)
                return;
            (winner == 0 ? game.getSetsRedTeam() : game.getSetsBlueTeam()).set(2);
            game.getFinished().set(true);
        }
        log.info("tournament match {}: play time over, {} ahead", room.getTournamentMatchId(), game.getSetsRedTeam().get() == 2 ? "red" : "blue");
        GameManager.getInstance().sendPacketToAllClientsInSameGameSession(
                new S2CMatchplayTeamWinsSet((byte) game.getSetsRedTeam().get(), (byte) game.getSetsBlueTeam().get()), client.getConnection());
        game.getHandleable().onEnd(client);
    }

    /** 0 red / 1 blue ahead by sets, then by the current set's points; -1 level [I]. */
    static int timeUpWinner(int setsRed, int setsBlue, int pointsRed, int pointsBlue) {
        if (setsRed != setsBlue) return setsRed > setsBlue ? 0 : 1;
        if (pointsRed != pointsBlue) return pointsRed > pointsBlue ? 0 : 1;
        return -1;
    }

    /** End of a Basic game in a tournament room: the winning side's player wins the match. */
    public static void recordMatch(Room room, Collection<FTClient> clients, MatchplayGame game, boolean redWon) {
        Long matchId = room == null ? null : room.getTournamentMatchId();
        if (matchId == null)
            return;
        for (FTClient c : clients) {
            RoomPlayer rp = c.getRoomPlayer();
            if (rp == null || rp.getPosition() >= 4 || !c.hasPlayer() || game.isRedTeam(rp.getPosition()) != redWon)
                continue;
            TournamentBracketService.Recorded r = ServiceManager.getInstance().getTournamentBracketService().record(matchId, c.getPlayer().getId(), (byte) 2, (byte) 0);
            log.info("tournament match {} won by player {}: {}{}", matchId, c.getPlayer().getId(), r.recorded() ? "recorded" : "not recorded",
                    r.champion() ? ", champion, " + r.rewards().size() + " reward rows" : "");
            // The champion's rewards are placed in the pocket as a shop buy places them (0x1B69).
            if (!r.rewards().isEmpty() && c.getConnection() != null)
                c.getConnection().sendTCP(new S2CInventoryItemsPlacePacket(r.rewards()));
            // 0x2415 score + 0x2414 result to the room (event 0x91: cGameResultTourney; one game decides the match [I]: need 1).
            Integer tid = room.getTournamentId();
            if (tid != null) {
                int red = redWon ? 1 : 0, blue = redWon ? 0 : 1;
                for (FTClient other : clients) {
                    if (other.getConnection() == null) continue;
                    other.getConnection().sendTCP(S2CTournamentRoundPacket.score(tid, red, blue));
                    other.getConnection().sendTCP(S2CTournamentRoundPacket.result(tid, r.champion(), redWon ? 0 : 1, 1, red, blue));
                }
            }
            room.setTournamentMatchId(null);
            return;
        }
    }
}
