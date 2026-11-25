package com.jftse.emulator.server.core.handler.emblem;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.emblem.S2CEmblemCompleteAnswerPacket;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.player.S2CPlayerStatusPointChangePacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.emblem.EmblemQuestLog;
import com.jftse.entities.database.model.item.Product;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.server.core.emblem.EmblemCounters;
import com.jftse.server.core.emblem.EmblemQuestData;
import com.jftse.server.core.emblem.EmblemQuestRules;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.*;
import com.jftse.emulator.common.service.ConfigService;
import com.jftse.server.core.shared.packets.emblem.CMSGEmblemComplete;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

// C2S 0x227C {u16 questIndex} (btAccept as Complete, 0x49ac25) -> S2C 0x227D then 0x226B (the client waits 20 s for 0x226B; the
// result is handled by the scene dispatcher while it waits). notes-v185, user-approved 2026-11-11.
// Checks in the order of the client's failure texts: 2 invalid index, 3 level, 4 no entry, 5 emblem already owned, 6 not in
// progress, 7 a required product unknown, 9 progress not complete, 10 pocket full. Then: required items are taken [I: the native
// server's turn-in], exp / gold added (level by LevelService like the tutorial), item rewards from RewardItem_<CHAR> rows (ShopIndex
// product, quantity uniform in [QuantityMin, QuantityMax] [I]) unless already completed and ItemRewardRepeat is No, the log updated.
@Log4j2
@PacketId(CMSGEmblemComplete.PACKET_ID)
public class EmblemCompleteRequestPacketHandler implements PacketHandler<FTConnection, CMSGEmblemComplete> {
    private final EmblemQuestService emblemQuestService;
    private final ProductService productService;
    private final PocketService pocketService;
    private final PlayerPocketService playerPocketService;
    private final InventoryService inventoryService;
    private final LevelService levelService;

    public EmblemCompleteRequestPacketHandler() {
        ServiceManager sm = ServiceManager.getInstance();
        emblemQuestService = sm.getEmblemQuestService();
        productService = sm.getProductService();
        pocketService = sm.getPocketService();
        playerPocketService = sm.getPlayerPocketService();
        inventoryService = sm.getInventoryService();
        levelService = sm.getLevelService();
    }

    // The pocket item a required product counts: its category + Item0, use type Count or Durable (client: held only for useType 2/3).
    private PlayerPocket held(Product product, Pocket pocket) {
        PlayerPocket item = playerPocketService.getItemAsPocketByItemIndexAndCategoryAndPocket(product.getItem0(), product.getCategory(), pocket);
        if (item == null || item.getUseType() == null)
            return null;
        String use = item.getUseType().toUpperCase();
        return use.equals("COUNT") || use.equals("DURABLE") ? item : null;
    }

    @Override
    public void handle(FTConnection connection, CMSGEmblemComplete packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;
        FTPlayer player = client.getPlayer();
        long playerId = player.getId();
        int index = packet.getQuestIndex() & 0xFFFF;
        byte result = check(player, index);
        if (result != 0)
            connection.sendTCP(new S2CEmblemCompleteAnswerPacket(result));
        else
            give(connection, player, index);
        EmblemListRequestPacketHandler.sendLog(connection, emblemQuestService, playerId);
    }

    private byte check(FTPlayer player, int index) {
        EmblemQuestData data = emblemQuestService.data();
        EmblemQuestData.Quest q = data.quest(index);
        EmblemQuestLog entry = emblemQuestService.entry(player.getId(), index);
        byte result = EmblemQuestRules.completeCheck(q, entry, player.getLevel());
        if (result != 0)
            return result;
        Pocket pocket = pocketService.findById(player.getPocketId());
        Map<Integer, Integer> held = new HashMap<>();
        for (EmblemQuestData.Require r : q.require()) {
            Product product = productService.findProductByProductItemIndex(r.product());
            if (product == null)
                return EmblemQuestRules.BAD_PRODUCT;
            PlayerPocket item = pocket == null ? null : held(product, pocket);
            held.put(r.product(), item == null || item.getItemCount() == null ? 0 : item.getItemCount());
        }
        EmblemCounters counters = emblemQuestService.counters(player.getId());
        int[] progress = EmblemQuestRules.progress(q, entry, counters, p -> held.getOrDefault(p, 0));
        if (progress[0] != progress[1])
            return EmblemQuestRules.NOT_DONE;
        int rewards = EmblemQuestRules.itemRewards(q, entry) ? (int) Arrays.stream(q.rewards()).filter(r -> r > 0).count() : 0;
        if (pocket != null && rewards > 0 && pocket.getBelongings() + rewards > pocket.getMaxBelongings())
            return EmblemQuestRules.INVENTORY_FULL;
        return 0;
    }

    private void give(FTConnection connection, FTPlayer player, int index) {
        EmblemQuestData data = emblemQuestService.data();
        EmblemQuestData.Quest q = data.quest(index);
        EmblemQuestLog entry = emblemQuestService.entry(player.getId(), index);
        Pocket pocket = pocketService.findById(player.getPocketId());

        // Required items are handed in.
        for (EmblemQuestData.Require r : q.require()) {
            Product product = productService.findProductByProductItemIndex(r.product());
            PlayerPocket item = held(product, pocket);
            if (item == null)
                continue;
            int left = item.getItemCount() - r.qty();
            if (left <= 0) {
                playerPocketService.remove(item.getId());
                pocketService.decrementPocketBelongings(pocket);
                connection.sendTCP(new S2CInventoryItemRemoveAnswerPacket(Math.toIntExact(item.getId())));
            } else {
                item.setItemCount(left);
                playerPocketService.save(item);
                connection.sendTCP(new S2CInventoryItemCountPacket(item));
            }
        }

        List<PlayerPocket> items = new ArrayList<>();
        if (EmblemQuestRules.itemRewards(q, entry)) {
            int charType = player.getPlayerType();
            for (int row : q.rewards()) {
                EmblemQuestData.RewardRow reward = data.rewardRow(charType, row);
                if (reward == null)
                    continue;
                int min = Math.max(1, reward.min()), max = Math.max(min, reward.max());
                int quantity = ThreadLocalRandom.current().nextInt(min, max + 1);
                List<PlayerPocket> added = inventoryService.addItem(player.getId(), reward.shopIndex(), quantity, List.of());
                if (added.isEmpty())
                    log.warn("emblem quest {}: reward product {} not added", index, reward.shopIndex());
                items.addAll(added);
            }
        }

        int oldLevel = player.getLevel();
        int level = levelService.getLevel(q.exp(), player.getExpPoints(), (byte) oldLevel);
        if (level < ConfigService.getInstance().getValue("player.level.max", 60) || oldLevel < level)
            player.syncExpPoints(player.getExpPoints() + q.exp());
        player.syncGold(player.getGold() + q.gold());
        if (level != oldLevel) {
            levelService.setNewLevelStatusPoints((byte) level, player.getPlayer());
            player.syncLevel(level);
        }
        emblemQuestService.markCompleted(player.getId(), index);

        connection.sendTCP(new S2CEmblemCompleteAnswerPacket((byte) player.getLevel(), player.getExpPoints(), player.getGold(), items));
        if (level != oldLevel)
            connection.sendTCP(new S2CPlayerStatusPointChangePacket(player));
    }
}
