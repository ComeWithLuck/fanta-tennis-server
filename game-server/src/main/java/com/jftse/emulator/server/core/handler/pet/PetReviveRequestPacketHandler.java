package com.jftse.emulator.server.core.handler.pet;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetDataAnswerPacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetReviveAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.PetService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PocketService;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;
import com.jftse.server.core.shared.packets.pet.CMSGRevivePet;

import java.util.List;
import java.util.Set;

// GuiPop_PetRebirth (opened by Item_Special 9 / 12 / 38 "Root of life tree", 4b94fb / 4b94c0, only with a fainted Battlemon):
// C 0x1526 {int32 the item's pocket id, byte pet type} and a wait for 0x1527 (20 s). Result 0 revives that pet with full
// energy / hunger / life (PetService.revive), uses one item and sends the pet list (0x151B) again; -1 changes nothing.
@PacketId(CMSGRevivePet.PACKET_ID)
public class PetReviveRequestPacketHandler implements PacketHandler<FTConnection, CMSGRevivePet> {
    private static final Set<Integer> ROOTS = Set.of(9, 12, 38);
    private final PetService petService;
    private final PlayerPocketService playerPocketService;
    private final PocketService pocketService;

    public PetReviveRequestPacketHandler() {
        ServiceManager sm = ServiceManager.getInstance();
        petService = sm.getPetService();
        playerPocketService = sm.getPlayerPocketService();
        pocketService = sm.getPocketService();
    }

    @Override
    public void handle(FTConnection connection, CMSGRevivePet packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;

        FTPlayer player = client.getPlayer();
        PlayerPocket item = playerPocketService.getItemAsPocket((long) packet.getItemId(), player.getPocketId());
        Pet pet = petService.findAllByPlayerId(player.getId()).stream()
                .filter(p -> p.getType() != null && p.getType() == packet.getPetType()).findFirst().orElse(null);

        boolean ok = item != null && EItemCategory.SPECIAL.getName().equals(item.getCategory()) && ROOTS.contains(item.getItemIndex())
                && pet != null && !Boolean.TRUE.equals(pet.getAlive());
        if (!ok) {
            connection.sendTCP(new S2CPetReviveAnswerPacket((short) -1));
            return;
        }

        petService.revive(pet);
        useOne(connection, player, item);
        connection.sendTCP(new S2CPetReviveAnswerPacket((short) 0));
        connection.sendTCP(new S2CPetDataAnswerPacket(petService.findAllByPlayerId(player.getId())));
    }

    // One use of the pocket item: the count goes down, the last one leaves the pocket (as EmblemCompleteRequestPacketHandler).
    private void useOne(FTConnection connection, FTPlayer player, PlayerPocket item) {
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
    }
}
