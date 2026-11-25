package com.jftse.emulator.server.core.handler.player;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.item.BaseItem;
import com.jftse.emulator.server.core.life.item.ItemFactory;
import com.jftse.emulator.server.core.life.item.quick.QuickItem;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.shared.packets.player.CMSGUseQuickSlot;
import com.jftse.server.core.shared.rabbit.messages.PacketMessage;
import org.springframework.util.MultiValueMap;

@PacketId(CMSGUseQuickSlot.PACKET_ID)
public class QuickSlotUseRequestHandler implements PacketHandler<FTConnection, CMSGUseQuickSlot> {
    private final RProducerService rProducerService;

    public QuickSlotUseRequestHandler() {
        rProducerService = RProducerService.getInstance();
    }

    @Override
    public void handle(FTConnection connection, CMSGUseQuickSlot quickSlotUseRequestPacket) {
        FTClient ftClient = connection.getClient();
        if (!ftClient.hasPlayer())
            return;

        FTPlayer player = ftClient.getPlayer();

        BaseItem baseItem = ItemFactory.getItem(quickSlotUseRequestPacket.getQuickSlotId(), player.getPocketId());
        if (baseItem == null)
            return;

        // The native client sends CMSG_UseQuickSlot when the quick cast starts and then CMSG_PlayerUseSkill
        // (isQuickSlot) on release; PlayerUseSkillHandler validates and consumes the item there. Consuming a quick (cast)
        // item here as well took two items per use, so QUICK items skip the pocket here. Every other kind used through
        // 0x1BDA (special items: Trunk 15/16/17, the card slot item 48, rings, ...) is applied and consumed only here, as in
        // chat-server (notes-v174, user-approved 2026-11-10).
        boolean applied = baseItem.processPlayer(player);
        if (applied && !(baseItem instanceof QuickItem)) {
            baseItem.processPocket(player.getPocketId());
        }
        sendPackets(baseItem.getPacketsToSend());
    }

    private void sendPackets(MultiValueMap<Long, IPacket> packetsToSend) {
        packetsToSend.forEach((playerId, packets) -> {
            for (IPacket p : packets) {
                PacketMessage packetMessage = PacketMessage.builder()
                        .packet(p)
                        .receivingPlayerId(playerId)
                        .build();
                rProducerService.send(packetMessage, "game.player.quickSlot chat.player.quickSlot", "GameServer");
            }
        });
    }
}
