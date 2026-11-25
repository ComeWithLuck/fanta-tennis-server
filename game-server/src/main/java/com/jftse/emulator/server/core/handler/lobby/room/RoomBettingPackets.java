package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomBetting;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.server.core.shared.packets.shop.SMSGSetMoney;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.protocol.Packet;

// Betting packets and money moves (RoomBetting, notes-v179): S2C 0x1DB1 / 0x1DB3 {u8 result}, 0x1DB4 {i16 position, u8 side},
// 0x1DB5 {i16 position}; gold moves as the enchant / guild handlers do (syncGold + setMoney + SMSGSetMoney 0x1B6B).
public final class RoomBettingPackets {
    private RoomBettingPackets() {}

    public static Packet result(char id, byte result) {
        Packet p = new Packet(id);
        p.write(result);
        return p;
    }

    public static Packet placed(short position, byte side) {
        Packet p = new Packet((char) 0x1DB4);
        p.write(position);
        p.write(side);
        return p;
    }

    public static Packet cleared(short position) {
        Packet p = new Packet((char) 0x1DB5);
        p.write(position);
        return p;
    }

    public static void broadcast(Room room, Packet packet) {
        GameManager.getInstance().getClientsInRoom(room.getRoomId()).forEach(c -> {
            if (c.getConnection() != null)
                c.getConnection().sendTCP(packet);
        });
    }

    /** Adds delta gold to the client's player (negative takes) and sends its money. */
    public static void gold(FTClient client, int delta) {
        if (client == null || !client.hasPlayer() || delta == 0)
            return;
        FTPlayer player = client.getPlayer();
        int gold = Math.max(0, player.getGold() + delta);
        player.syncGold(gold);
        ServiceManager.getInstance().getPlayerService().setMoney(player.getPlayer(), gold);
        if (client.getConnection() != null)
            // SMSGSetMoney with the client's AP (as ShopMoneyRequestPacketHandler): S2CShopMoneyAnswerPacket reads the lazy
            // Account outside a session (LazyInitializationException, live 2026-11-11).
            client.getConnection().sendTCP(SMSGSetMoney.builder().ap(client.getAp().get()).gold(player.getGold()).build());
    }

    /** End of a Basic match: pay RoomBetting.settle's shares to the bettors still in the room, clear every side. */
    public static void settleMatch(Room room, java.util.Collection<FTClient> clients, boolean redWon) {
        if (room == null || RoomBetting.mode(room) != RoomBetting.MODE_GOLD)
            return;
        for (RoomBetting.Payout payout : RoomBetting.settle(room.getRoomPlayerList(), room.getBettingAmount(), redWon)) {
            clients.stream().filter(c -> c.getRoomPlayer() == payout.bettor()).findFirst().ifPresent(c -> gold(c, payout.gold()));
        }
        room.getRoomPlayerList().forEach(rp -> rp.setBettingSide(RoomBetting.NONE));
    }

    /** A bettor leaving before the match gets the stake back (no broadcast: the roster update removes the player). */
    public static void refundOnLeave(Room room, FTClient client, RoomPlayer rp) {
        if (room == null || rp == null || rp.getBettingSide() == RoomBetting.NONE)
            return;
        rp.setBettingSide(RoomBetting.NONE);
        if (RoomBetting.idle(room))
            gold(client, room.getBettingAmount());
    }
}
