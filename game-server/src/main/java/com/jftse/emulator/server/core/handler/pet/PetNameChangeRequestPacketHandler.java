package com.jftse.emulator.server.core.handler.pet;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetDataAnswerPacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetNameChangeAnswerPacket;
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
import com.jftse.server.core.shared.packets.pet.CMSGPetNameCheck;

import java.util.List;
import java.util.Set;

// GuiPop_PetRename (opened by Item_Special 10 "Battlemon Tag", 4b9548): C 0x1524 {int32 the tag's pocket id, byte pet type,
// string name}; the client checked 2..12 characters and its word filter (576031 / 57603e) and waits for 0x1525 (20 s).
// Result 0 renames the pet, uses one tag and sends the pet list (0x151B) again; -1 changes nothing.
@PacketId(CMSGPetNameCheck.PACKET_ID)
public class PetNameChangeRequestPacketHandler implements PacketHandler<FTConnection, CMSGPetNameCheck> {
    private static final int BATTLEMON_TAG = 10;
    private final PetService petService;
    private final PlayerPocketService playerPocketService;
    private final PocketService pocketService;

    public PetNameChangeRequestPacketHandler() {
        ServiceManager sm = ServiceManager.getInstance();
        petService = sm.getPetService();
        playerPocketService = sm.getPlayerPocketService();
        pocketService = sm.getPocketService();
    }

    @Override
    public void handle(FTConnection connection, CMSGPetNameCheck packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;

        FTPlayer player = client.getPlayer();
        String name = packet.getNewPetName() == null ? "" : packet.getNewPetName().trim();
        PlayerPocket item = playerPocketService.getItemAsPocket((long) packet.getItemId(), player.getPocketId());
        List<Pet> pets = petService.findAllByPlayerId(player.getId());
        Pet pet = pets.stream().filter(p -> p.getType() != null && p.getType() == packet.getPetType()).findFirst().orElse(null);

        boolean ok = item != null && EItemCategory.SPECIAL.getName().equals(item.getCategory()) && item.getItemIndex() == BATTLEMON_TAG
                && pet != null && name.length() >= 2 && name.length() <= 12;
        if (!ok) {
            connection.sendTCP(new S2CPetNameChangeAnswerPacket((short) -1));
            return;
        }

        pet.setName(name);
        petService.save(pet);
        useOne(connection, player, item);
        connection.sendTCP(new S2CPetNameChangeAnswerPacket((short) 0));
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
