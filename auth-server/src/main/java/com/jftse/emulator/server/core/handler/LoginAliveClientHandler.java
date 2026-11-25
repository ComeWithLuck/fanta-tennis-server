package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.ServerType;
import com.jftse.entities.database.model.gameserver.GameServer;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.AuthenticationService;
import com.jftse.server.core.shared.packets.auth.CMSGRequestChannelList;
import com.jftse.server.core.shared.packets.auth.SMSGChannelList;

import java.util.List;

@PacketId(CMSGRequestChannelList.PACKET_ID)
public class LoginAliveClientHandler implements PacketHandler<FTConnection, CMSGRequestChannelList> {
    private final AuthenticationService authenticationService;

    public LoginAliveClientHandler() {
        authenticationService = ServiceManager.getInstance().getAuthenticationService();
    }

    @Override
    public void handle(FTConnection connection, CMSGRequestChannelList packet) {
        FTClient client = connection.getClient();
        if (client == null)
            return;

        if (client.isClientAlive().compareAndSet(false, true)) {
            List<GameServer> gameServerList = authenticationService.getGameServerList();
            // 0x1010 is the client's channel list. Native buckets the rows by `type` as the channel index (466b00):
            // btChannel0n (4aa7b1) connects to a server of bucket n as its main connection (466f50 drop, 46b940 connect,
            // scene 7); CHANNEL_NAME_00 is Chat (squares / houses), 01 Free (game); 46d7b0 auto-picks Rookie 2 / Pro 3 /
            // Master 4 by level, then Free 1. JFTSE's type is the server kind (relay 2, chat 4), so a native client could take
            // the relay or the chat server as a game channel. List the game server as channel 1 (Free) and the chat server
            // as channel 0 (Chat); the relay is announced at match start (S2CGameNetworkSettings), not here. One game
            // channel (user's choice 2026-11-23 / 2026-11-24, notes-v400).
            SMSGChannelList channelList = SMSGChannelList.builder()
                    .channels(gameServerList.stream()
                            .filter(gs -> nativeChannel(gs) != null)
                            .map(gs -> com.jftse.server.core.shared.packets.auth.Channel.builder()
                            .id((byte) Math.toIntExact(gs.getId()))
                            .id2((short) Math.toIntExact(gs.getId()))
                            .type(nativeChannel(gs))
                            .hostname(gs.getHost())
                            .port(gs.getPort().shortValue())
                            .population((short) 0)
                            .customChannel(gs.getIsCustomChannel())
                            .name(gs.getName())
                            .build()).toList()
                    ).build();
            connection.sendTCP(channelList);
        }
    }

    /** The native channel index for a gameserver row: game -> 1 (Free), chat -> 0 (Chat), others not listed (notes-v400). */
    private static Byte nativeChannel(GameServer gs) {
        Byte kind = gs.getGameServerType() == null ? null : gs.getGameServerType().getType();
        if (kind == null) return null;
        if (kind == ServerType.GAME_SERVER.getValue()) return (byte) 1;
        if (kind == ServerType.CHAT_SERVER.getValue()) return (byte) 0;
        return null;
    }
}
