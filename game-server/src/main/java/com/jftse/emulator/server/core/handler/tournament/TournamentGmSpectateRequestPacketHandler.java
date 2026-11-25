package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.handler.lobby.room.RoomJoinRequestPacketHandler;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.tournament.S2CTournamentResultPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.TournamentBracketService;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomJoin;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentGmSpectate;

import java.util.Date;

// C2S 0x2416 {u32 tournamentId, u8 isFinal, u8 group, u8 round, u8 matchPosition}: GuiPop_GMSpectator btOK (5c39df, notes-v360),
// opened by Gui_TournamentMenu btGMSpectator (5c724b; shown only to a GM, 5c6813). round / matchPosition are the combo indexes:
// the match combo (5c3200) lists the n nodes n - 1 .. 2n - 2 of the chosen round's level (n = teams >> (round + 1)), so the
// bracket node is n - 1 + matchPosition. S2C 0x2417 {i8}: 0 -> the client waits for the pushed 0x138C room join (5c7a50), as for
// btPlay; -1..-6 -> MSG_TOURNEY_FAILED_TO_JOIN_FOR_GM_01..06 (5c7710). The code meanings are the server's [I: the native texts
// are not mapped to causes]: -1 not a GM, -2 no such tournament, -3 bracket not made, -4 no such node, -5 the match has no room
// (not started / already finished), -6 already in a room. A GM joins a private room without its password and takes seat 9
// (RoomManager.joinRoomLocked).
@PacketId(CMSGTournamentGmSpectate.PACKET_ID)
public class TournamentGmSpectateRequestPacketHandler implements PacketHandler<FTConnection, CMSGTournamentGmSpectate> {
    @Override
    public void handle(FTConnection connection, CMSGTournamentGmSpectate packet) {
        FTClient client = connection.getClient();
        if (client == null || !client.hasPlayer())
            return;
        Room[] found = new Room[1];
        byte result = resolve(client, packet, found);
        connection.sendTCP(new S2CTournamentResultPacket(S2CTournamentResultPacket.GM_SPECTATE, result));
        if (result != 0)
            return;
        Room room = found[0];
        // The join handler sends 0x138C + the room information / roster, as TournamentRooms.play does for btPlay.
        new RoomJoinRequestPacketHandler().handle(connection, CMSGRoomJoin.builder().roomId(room.getRoomId()).unk0((byte) 0).password(room.getPassword()).build());
    }

    /** The bracket node of (round, matchPosition), -1 when out of range. */
    static int node(int teams, int round, int matchPosition) {
        int n = teams >> (round + 1);
        if (n < 1 || matchPosition < 0 || matchPosition >= n)
            return -1;
        return n - 1 + matchPosition;
    }

    private static byte resolve(FTClient client, CMSGTournamentGmSpectate packet, Room[] found) {
        if (!client.isGameMaster()) return -1;
        Tournament t = ServiceManager.getInstance().getTournamentService().find(Integer.toUnsignedLong(packet.getTournamentId()));
        if (t == null) return -2;
        TournamentBracketService brackets = ServiceManager.getInstance().getTournamentBracketService();
        boolean isFinal = packet.getIsFinal() != 0;
        int group = packet.getGroup() & 0xff;
        TournamentBracketService.Bracket bracket = brackets.bracket(t, isFinal, group, new Date());
        if (bracket == null) return -3;
        int node = node(bracket.teams(), packet.getRound() & 0xff, packet.getMatchPosition() & 0xff);
        Long matchId = node < 0 ? null : brackets.matchId(t, isFinal, group, node);
        if (matchId == null) return -4;
        Room room = GameManager.getInstance().getRoomManager().getRooms().stream()
                .filter(r -> matchId.equals(r.getTournamentMatchId())).findFirst().orElse(null);
        if (room == null) return -5;
        if (client.getActiveRoom() != null) return -6;
        found[0] = room;
        return 0;
    }
}
