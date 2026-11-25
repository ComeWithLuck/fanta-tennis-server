package com.jftse.emulator.server.core.life.item.special;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.item.BaseItem;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CExpandCardSlotPacket;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PocketService;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;

// Item_Special 48 "Slot Expansion Item", used through 0x1BDA like the Trunks (notes-v174, user-approved 2026-11-10): one more
// card slot, answered with S2C 0x1DE5. The count is the player-info byte the client reads as extra slots (492b80: count + 1
// usable); MSG_CARD_LAST_SLOT says 4 slots at most, so the count stops at 3 [I: the native server's own limit].
public class CardSlotExpansion extends BaseItem {
    public static final int MAX_EXTRA_SLOTS = 3;

    private final PocketService pocketService;
    private final PlayerPocketService playerPocketService;

    public CardSlotExpansion(int itemIndex, String name, String category) {
        super(itemIndex, name, category);

        pocketService = ServiceManager.getInstance().getPocketService();
        playerPocketService = ServiceManager.getInstance().getPlayerPocketService();
    }

    @Override
    public boolean processPlayer(FTPlayer player) {
        this.localPlayerId = player.getId();

        return true;
    }

    @Override
    public boolean processPocket(Long pocketId) {
        Pocket pocket = pocketService.findById(pocketId);
        if (pocket == null)
            return false;

        PlayerPocket item = playerPocketService.getItemAsPocketByItemIndexAndCategoryAndPocket(this.getItemIndex(), this.getCategory(), pocket);
        if (item == null)
            return false;

        int count = pocket.getCardSlotCount() == null ? 0 : pocket.getCardSlotCount();
        if (count >= MAX_EXTRA_SLOTS) {
            this.packetsToSend.add(this.localPlayerId, new S2CExpandCardSlotPacket(S2CExpandCardSlotPacket.LAST_SLOT, (byte) count));
            return false;
        }

        pocket.setCardSlotCount((byte) (count + 1));
        pocket = pocketService.save(pocket);
        this.packetsToSend.add(this.localPlayerId, new S2CExpandCardSlotPacket(S2CExpandCardSlotPacket.SUCCESS, pocket.getCardSlotCount()));

        int itemCount = item.getItemCount() - 1;
        if (itemCount <= 0) {
            playerPocketService.remove(item.getId());
            pocketService.decrementPocketBelongings(pocket);
            this.packetsToSend.add(this.localPlayerId, new S2CInventoryItemRemoveAnswerPacket(Math.toIntExact(item.getId())));
        } else {
            item.setItemCount(itemCount);
            playerPocketService.save(item);
            this.packetsToSend.add(this.localPlayerId, new S2CInventoryItemCountPacket(item));
        }

        return true;
    }
}
