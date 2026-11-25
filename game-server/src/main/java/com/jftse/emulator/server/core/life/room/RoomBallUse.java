package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomInformationPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Room ball items (notes-v403 / v404). Item_Ball.set: 1 Tennis Ball (UseType N/A, the default), 2..7 Durable. The native client
 * reads the room ball only to draw the match ball (50d354 / 50d65a, Config BallChange) and never changes the count itself;
 * the item text says the owner's ball is used up one per match. So the server checks the ball at room creation and takes one
 * use from the room master's BALL row when a match ends; the client learns the count from the usual pocket packets
 * (S2CInventoryItemCountPacket / S2CInventoryItemRemoveAnswerPacket). The Tennis Ball costs nothing.
 */
public final class RoomBallUse {
    public static final int DEFAULT_BALL = 1;
    private static final Set<GameSession> USED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private RoomBallUse() {
    }

    private static PlayerPocket ballRow(FTClient owner, int ball) {
        if (owner == null || !owner.hasPlayer()) return null;
        PlayerPocket row = ServiceManager.getInstance().getPlayerPocketService()
                .getItemAsPocketByItemIndexAndCategoryAndPocket(ball, EItemCategory.BALL.getName(), owner.getPlayer().getPocketId());
        return row != null && row.getItemCount() != null && row.getItemCount() > 0 ? row : null;
    }

    /** RoomCreate: a ball other than the Tennis Ball must be one of the owner's BALL rows with uses left, else the Tennis Ball. */
    public static int validBall(FTClient owner, int ball) {
        if (ball <= DEFAULT_BALL) return DEFAULT_BALL;
        return ballRow(owner, ball) != null ? ball : DEFAULT_BALL;
    }

    /** Once per match (per game session): one use of the room master's ball; the room falls back to the Tennis Ball when it runs out. */
    public static void afterMatch(GameSession session, Room room) {
        if (session == null || room == null || !USED.add(session)) return;
        int ball = room.getBall();
        if (ball <= DEFAULT_BALL) return;
        RoomPlayer master = room.getRoomPlayerList().stream().filter(RoomPlayer::isMaster).findFirst().orElse(null);
        if (master == null) return;
        FTClient owner = GameManager.getInstance().getClientsInRoom(room.getRoomId()).stream()
                .filter(c -> c.hasPlayer() && c.getPlayer().getId() == master.getPlayerId()).findFirst().orElse(null);
        PlayerPocket row = ballRow(owner, ball);
        boolean left = false;
        if (row != null) {
            int count = row.getItemCount() - 1;
            if (count <= 0) {
                ServiceManager.getInstance().getPlayerPocketService().remove(row.getId());
                ServiceManager.getInstance().getPocketService().decrementPocketBelongings(owner.getPlayer().getPocketId());
                if (owner.getConnection() != null)
                    owner.getConnection().sendTCP(new S2CInventoryItemRemoveAnswerPacket(Math.toIntExact(row.getId())));
            } else {
                row.setItemCount(count);
                ServiceManager.getInstance().getPlayerPocketService().save(row);
                if (owner.getConnection() != null) owner.getConnection().sendTCP(new S2CInventoryItemCountPacket(row));
                left = true;
            }
        }
        if (!left) {
            room.setBall(DEFAULT_BALL);
            S2CRoomInformationPacket info = new S2CRoomInformationPacket(room);
            GameManager.getInstance().getClientsInRoom(room.getRoomId()).forEach(c -> {
                if (c.getConnection() != null) c.getConnection().sendTCP(info);
            });
        }
    }
}
