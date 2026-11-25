package com.jftse.emulator.server.core.handler.item;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.item.S2CPersonalBoardMessageListPacket;
import com.jftse.emulator.server.core.packets.item.S2CPersonalBoardPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PocketService;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;
import com.jftse.server.core.shared.packets.item.CMSGPersonalBoard;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

// GuiPop_FixedChatBoard (Gui_RoomLobby btFixedChat, class 557480): C 0x22CD {int32 pocket id of Item_Special 19, string
// message (2..80 characters, the client's word filter)}. One item is used and the message becomes the player's fixed chat
// line: S 0x22CE {string player name, string message} to everyone in the room. The client (4d6990) finds the sender's slot
// by name and draws the line above that player's head (4d8300; the normal chat bubble wins for 10 s). A player joining a
// room gets S 0x22CF {short count, (short room position, string message)*} for the members who have a line (4d6b30).
// The lines live in memory for the server's lifetime, keyed by player id. Nothing is sent back on a refusal.
@PacketId(CMSGPersonalBoard.PACKET_ID)
public class FixedChatBoardRequestPacketHandler implements PacketHandler<FTConnection, CMSGPersonalBoard> {
    public static final int FIXED_CHAT_ITEM = 19;
    private static final Map<Long, String> LINES = new ConcurrentHashMap<>();

    private final PlayerPocketService playerPocketService;
    private final PocketService pocketService;

    public FixedChatBoardRequestPacketHandler() {
        playerPocketService = ServiceManager.getInstance().getPlayerPocketService();
        pocketService = ServiceManager.getInstance().getPocketService();
    }

    public static void sendBoard(FTConnection connection, List<FTClient> clientsInRoom) {
        Map<Integer, String> board = new TreeMap<>();
        for (FTClient c : clientsInRoom) {
            RoomPlayer roomPlayer = c.getRoomPlayer();
            if (roomPlayer == null || c.getPlayer() == null)
                continue;
            String line = LINES.get(c.getPlayer().getId());
            if (line != null)
                board.put((int) roomPlayer.getPosition(), line);
        }
        if (!board.isEmpty())
            connection.sendTCP(new S2CPersonalBoardMessageListPacket(board));
    }

    @Override
    public void handle(FTConnection connection, CMSGPersonalBoard packet) {
        FTClient client = connection.getClient();
        if (client == null || !client.hasPlayer() || client.getActiveRoom() == null)
            return;

        FTPlayer player = client.getPlayer();
        String message = packet.getMessage() == null ? "" : packet.getMessage();
        if (message.length() < 2 || message.length() > 80)
            return;

        PlayerPocket item = playerPocketService.getItemAsPocket((long) packet.getPlayerPocketId(), player.getPocketId());
        if (item == null || !EItemCategory.SPECIAL.getName().equals(item.getCategory()) || item.getItemIndex() != FIXED_CHAT_ITEM)
            return;

        int left = item.getItemCount() - 1;
        if (left <= 0) {
            playerPocketService.remove(item.getId());
            pocketService.decrementPocketBelongings(player.getPocketId());
            connection.sendTCP(new S2CInventoryItemRemoveAnswerPacket(Math.toIntExact(item.getId())));
        } else {
            item.setItemCount(left);
            playerPocketService.save(item);
            connection.sendTCP(new S2CInventoryItemCountPacket(item));
        }

        LINES.put(player.getId(), message);
        GameManager.getInstance().sendPacketToAllClientsInSameRoom(new S2CPersonalBoardPacket(player.getName(), message), connection);
    }
}
