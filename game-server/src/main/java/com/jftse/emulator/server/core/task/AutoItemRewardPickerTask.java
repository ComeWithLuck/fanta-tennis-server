package com.jftse.emulator.server.core.task;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.MatchplayReward;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemsPlacePacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.entities.database.model.item.Product;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.server.core.item.EItemUseType;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PocketService;
import com.jftse.server.core.service.ProductService;
import com.jftse.server.core.thread.AbstractTask;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AutoItemRewardPickerTask extends AbstractTask {
    private final ConcurrentLinkedDeque<FTClient> clients;
    private final short roomId;
    private final MatchplayReward expectedReward;

    private final PlayerPocketService playerPocketService;
    private final PocketService pocketService;
    private final ProductService productService;

    public AutoItemRewardPickerTask(final ConcurrentLinkedDeque<FTClient> clients, short roomId) {
        this.clients = clients;
        this.roomId = roomId;
        this.expectedReward = GameSessionManager.getInstance().getMatchplayReward(roomId);

        this.playerPocketService = ServiceManager.getInstance().getPlayerPocketService();
        this.pocketService = ServiceManager.getInstance().getPocketService();
        this.productService = ServiceManager.getInstance().getProductService();
    }

    @Override
    public void run() {
        GameSessionManager manager = GameSessionManager.getInstance();
        if (expectedReward == null || manager.getMatchplayReward(roomId) != expectedReward) return;
        try {
            for (FTClient client : clients) {
                MatchplayReward.ItemClaim claim = expectedReward.claimAnyItem(client);
                if (claim == null || !claim.newlyClaimed()) continue;
                MatchplayReward.ItemReward itemReward = claim.reward();
                FTPlayer player = client.getPlayer();
                expectedReward.notifyClaim(claim);
                // add reward to player pocket
                int productIndex = itemReward.getProductIndex();
                int productAmount = itemReward.getProductAmount();
                if (itemReward.getProductIndex() > 0) {
                    Product product = productService.findProductByProductItemIndex(productIndex);
                    if (product == null)
                        continue;

                    Pocket pocket = pocketService.findById(player.getPocketId());
                    PlayerPocket playerPocket = playerPocketService.getItemAsPocketByItemIndexAndCategoryAndPocket(product.getItem0(), product.getCategory(), pocket);
                    boolean existingItem = false;

                    if (playerPocket != null && !playerPocket.getUseType().equals("N/A")) {
                        existingItem = true;
                    } else {
                        playerPocket = new PlayerPocket();
                    }

                    playerPocket.setCategory(product.getCategory());
                    playerPocket.setItemIndex(product.getItem0());
                    playerPocket.setUseType(product.getUseType());

                    // no idea how itemCount can be null here, but ok
                    playerPocket.setItemCount((playerPocket.getItemCount() == null ? 0 : playerPocket.getItemCount()) + productAmount);

                    if (playerPocket.getUseType().equalsIgnoreCase(EItemUseType.TIME.getName())) {
                        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                        cal.add(Calendar.DAY_OF_MONTH, playerPocket.getItemCount());

                        playerPocket.setCreated(cal.getTime());
                        playerPocket.setItemCount(1);
                    }
                    playerPocket.setPocket(pocket);

                    playerPocketService.save(playerPocket);
                    if (!existingItem)
                        pocketService.incrementPocketBelongings(pocket);

                    if (!existingItem) {
                        S2CInventoryItemsPlacePacket inventoryDataPacket = new S2CInventoryItemsPlacePacket(List.of(playerPocket));
                        client.getConnection().sendTCP(inventoryDataPacket);
                    } else {
                        S2CInventoryItemCountPacket inventoryDataPacket = new S2CInventoryItemCountPacket(playerPocket);
                        client.getConnection().sendTCP(inventoryDataPacket);
                    }
                }
            }
        } finally {
            // An expired result must never remove a later match's reward.
            manager.removeMatchplayReward(roomId, expectedReward);
        }
    }
}
