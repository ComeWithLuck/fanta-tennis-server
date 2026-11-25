package com.jftse.emulator.server.core.handler.inventory;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CCardOpenAnswerPacket;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.CardPoolService;
import com.jftse.server.core.service.InventoryService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PocketService;
import com.jftse.server.core.shared.packets.inventory.CMSGCardOpen;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;
import lombok.extern.log4j.Log4j2;

import java.util.List;

// C2S 0x1DE3 {int32 pocketId} from GuiPop_AbilityCard btCardOpen (54cb9b; the pocket / room inventory open it for Item_Special 47
// "Unopened Cards(Normal)", 4ba223 / 565f6a) -> S2C 0x1DE4 (54ce00). notes-v175, user-approved 2026-11-11.
// The pack must be the player's SPECIAL 47 with a count; one card is rolled from the cardpool rows of 47 [I: the native pool and
// odds], added to the pocket, the pack is spent (0x1B73 count / 0x1B74 removal: the client does not count it down itself) and the
// card goes back in 0x1DE4. Any failure answers status 1, which the client takes silently (it only refreshes).
@Log4j2
@PacketId(CMSGCardOpen.PACKET_ID)
public class CardOpenRequestPacketHandler implements PacketHandler<FTConnection, CMSGCardOpen> {
    public static final int NORMAL_PACK = 47;

    private final PocketService pocketService;
    private final PlayerPocketService playerPocketService;
    private final InventoryService inventoryService;
    private final CardPoolService cardPoolService;

    public CardOpenRequestPacketHandler() {
        pocketService = ServiceManager.getInstance().getPocketService();
        playerPocketService = ServiceManager.getInstance().getPlayerPocketService();
        inventoryService = ServiceManager.getInstance().getInventoryService();
        cardPoolService = ServiceManager.getInstance().getCardPoolService();
    }

    @Override
    public void handle(FTConnection connection, CMSGCardOpen packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;

        FTPlayer player = client.getPlayer();
        Pocket pocket = pocketService.findById(player.getPocketId());
        PlayerPocket pack = pocket == null ? null : playerPocketService.getItemAsPocket((long) packet.getPocketId(), pocket);
        if (pack == null || !EItemCategory.SPECIAL.getName().equals(pack.getCategory()) || pack.getItemIndex() != NORMAL_PACK
                || pack.getItemCount() == null || pack.getItemCount() <= 0) {
            connection.sendTCP(new S2CCardOpenAnswerPacket(S2CCardOpenAnswerPacket.FAILED, null));
            return;
        }

        int cardIndex = cardPoolService.roll(pack.getItemIndex());
        if (cardIndex <= 0) {
            log.warn("cardpool has no cards for pack {}", pack.getItemIndex());
            connection.sendTCP(new S2CCardOpenAnswerPacket(S2CCardOpenAnswerPacket.FAILED, null));
            return;
        }

        List<PlayerPocket> added = inventoryService.addItem(player.getId(), cardIndex, EItemCategory.CARD.getName(), 1, List.of());
        PlayerPocket card = added.stream().filter(p -> p.getItemIndex() == cardIndex).findFirst().orElse(added.isEmpty() ? null : added.get(0));
        if (card == null) {
            connection.sendTCP(new S2CCardOpenAnswerPacket(S2CCardOpenAnswerPacket.FAILED, null));
            return;
        }

        int left = pack.getItemCount() - 1;
        if (left <= 0) {
            playerPocketService.remove(pack.getId());
            pocketService.decrementPocketBelongings(pocket);
            connection.sendTCP(new S2CInventoryItemRemoveAnswerPacket(Math.toIntExact(pack.getId())));
        } else {
            pack.setItemCount(left);
            playerPocketService.save(pack);
            connection.sendTCP(new S2CInventoryItemCountPacket(pack));
        }
        connection.sendTCP(new S2CCardOpenAnswerPacket(S2CCardOpenAnswerPacket.SUCCESS, card));
    }
}
