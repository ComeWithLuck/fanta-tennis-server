package com.jftse.emulator.server.core.handler.inventory;

import com.jftse.emulator.server.core.client.EquippedPetSlots;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryWearBattlemonAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.player.BattlemonSlotEquipment;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.BattlemonSlotEquipmentService;
import com.jftse.server.core.shared.packets.inventory.CMSGInventoryWearBattlemon;

// The Battlemon's two pet item slots (Gui_Pocket 4b5d0f / Gui_Shop 56237f send 0x1BDB with both pocket ids). Saved like the
// quick slots (InventoryWearQuickPacketHandler): the slot row, then the cached EquippedPetSlots, then the 0x1BDC echo.
@PacketId(CMSGInventoryWearBattlemon.PACKET_ID)
public class InventoryWearBattlemonPacketHandler implements PacketHandler<FTConnection, CMSGInventoryWearBattlemon> {
    private final BattlemonSlotEquipmentService battlemonSlotEquipmentService;

    public InventoryWearBattlemonPacketHandler() {
        battlemonSlotEquipmentService = ServiceManager.getInstance().getBattlemonSlotEquipmentService();
    }

    @Override
    public void handle(FTConnection connection, CMSGInventoryWearBattlemon packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;

        FTPlayer player = client.getPlayer();
        // The player's row (battlemonslotequipment.player_id), made on the first equip; the cached slots then carry its id.
        BattlemonSlotEquipment equipment = battlemonSlotEquipmentService.findOrCreateByPlayerId(player.getId());
        battlemonSlotEquipmentService.updateBattlemonSlots(equipment, packet.getBattlemonSlotList());
        player.setPetSlots(new EquippedPetSlots(equipment.getId(), packet.getBattlemonSlotList().get(0), packet.getBattlemonSlotList().get(1)));

        S2CInventoryWearBattlemonAnswerPacket inventoryWearBattlemonAnswerPacket = new S2CInventoryWearBattlemonAnswerPacket(packet.getBattlemonSlotList());
        connection.sendTCP(inventoryWearBattlemonAnswerPacket);
    }
}
