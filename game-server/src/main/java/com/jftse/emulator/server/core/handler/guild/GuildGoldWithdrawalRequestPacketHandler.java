package com.jftse.emulator.server.core.handler.guild;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.GuildService;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.shared.packets.guild.CMSGGuildGoldWithdrawal;
import com.jftse.server.core.shared.packets.guild.SMSGGuildGoldWithdrawal;
import com.jftse.server.core.shared.packets.shop.SMSGSetMoney;

// GuiPop_GuildGoldWithdrawl OK: C 0x2029 {amount} -> S 0x202A (559400, notes-v401). JFTSE rule [I]: only the club master
// withdraws (-1 otherwise); the amount must be 5000..500000 (the client's own check, -3); not more than the club gold (-2).
// The gold goes to the master's Gold (SMSGSetMoney) and a GuildGoldUsage row is logged (the 0x202D list).
@PacketId(CMSGGuildGoldWithdrawal.PACKET_ID)
public class GuildGoldWithdrawalRequestPacketHandler implements PacketHandler<FTConnection, CMSGGuildGoldWithdrawal> {
    private final GuildService guildService;
    private final PlayerService playerService;

    public GuildGoldWithdrawalRequestPacketHandler() {
        guildService = ServiceManager.getInstance().getGuildService();
        playerService = ServiceManager.getInstance().getPlayerService();
    }

    @Override
    public void handle(FTConnection connection, CMSGGuildGoldWithdrawal packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;

        FTPlayer activePlayer = client.getPlayer();
        int[] result;
        try {
            result = guildService.withdrawGold(activePlayer.getId(), packet.getAmount());
        } catch (Exception e) {
            connection.sendTCP(SMSGGuildGoldWithdrawal.builder().result((short) -10).guildGold(0).build());
            return;
        }
        if (result[0] == 0) {
            int newGold = activePlayer.getGold() + packet.getAmount();
            activePlayer.syncGold(newGold);
            playerService.setMoney(activePlayer.getPlayer(), newGold);
            // SMSGSetMoney with the client's AP (RoomBettingPackets.gold: the money answer reads a lazy Account).
            connection.sendTCP(SMSGSetMoney.builder().ap(client.getAp().get()).gold(activePlayer.getGold()).build());
        }
        connection.sendTCP(SMSGGuildGoldWithdrawal.builder().result((short) result[0]).guildGold(result[1]).build());
    }
}
