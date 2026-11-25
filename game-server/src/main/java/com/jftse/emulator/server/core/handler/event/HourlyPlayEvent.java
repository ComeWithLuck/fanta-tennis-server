package com.jftse.emulator.server.core.handler.event;

import com.jftse.emulator.common.service.ConfigService;
import com.jftse.emulator.server.core.handler.lobby.room.RoomBettingPackets;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.event.SGameEvent;
import com.jftse.server.core.protocol.Packet;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// The hourly play events (notes-v179; user-approved 2026-11-11 "Betting + special events"). Client [C]: Gui_Main's frame
// (0x4af2f8..0x4af3cf) counts play time in +0x898; once it reaches 3600 s (latch +0x894, reset by scene changes 0x4ad750) it
// sends C 0x213E {} when Config NewYearEvent [0x7b0840] is set and C 0x213F {} when RiceCakeEvent [0x7b0844] is set (both
// default 0). No dedicated answer is read; the gift notice 0x2135 (Gui_Main 0x4b0243 -> 0x4af400) type 0 shows MSG_GOLD_EVENT
// after skipping 8 bytes.
// Server [I: the native rules are not in the client]: an enabled S_GameEvent row of type NEW_YEAR_HOURLY / RICE_CAKE_HOURLY
// inside its start / end dates switches the event on; each request then gives the gold of Config event.newYear.hourlyGold /
// event.riceCake.hourlyGold (default 1000) at most once per 55 minutes per character, answered with 0x2135 {u8 0, i32 gold,
// i32 0} and the money packet. Nothing is sent while the event is off.
public final class HourlyPlayEvent {
    public static final String NEW_YEAR = "NEW_YEAR_HOURLY", RICE_CAKE = "RICE_CAKE_HOURLY";
    public static final long MIN_INTERVAL_MS = 55 * 60 * 1000L;
    private static final Map<String, Long> LAST = new ConcurrentHashMap<>();

    private HourlyPlayEvent() {}

    public static boolean active(Iterable<SGameEvent> events, String type, Date now) {
        for (SGameEvent e : events) {
            if (!type.equals(e.getType()) || !Boolean.TRUE.equals(e.getEnabled()))
                continue;
            if (e.getStartDate() != null && now.before(e.getStartDate()))
                continue;
            if (e.getEndDate() != null && now.after(e.getEndDate()))
                continue;
            return true;
        }
        return false;
    }

    /** True when this character may be rewarded now (and records it). */
    public static boolean claim(String type, long playerId, long nowMs) {
        String key = type + ":" + playerId;
        Long last = LAST.get(key);
        if (last != null && nowMs - last < MIN_INTERVAL_MS)
            return false;
        LAST.put(key, nowMs);
        return true;
    }

    public static Packet goldNotice(int gold) {
        Packet p = new Packet((char) 0x2135);
        p.write((byte) 0);
        p.write(gold);
        p.write(0);
        return p;
    }

    static void handle(FTConnection connection, String type, String configKey) {
        FTClient client = connection.getClient();
        if (client == null || !client.hasPlayer())
            return;
        Date now = new Date();
        // findAllEnabled, not findAllByType: GameEventServiceImpl.findAllByType calls itself (a StackOverflowError, live
        // 2026-11-11); active() filters the type and the dates.
        if (!active(ServiceManager.getInstance().getGameEventService().findAllEnabled(), type, now))
            return;
        int gold = ConfigService.getInstance().getValue(configKey, 1000);
        if (gold <= 0 || !claim(type, client.getPlayer().getId(), now.getTime()))
            return;
        RoomBettingPackets.gold(client, gold);
        connection.sendTCP(goldNotice(gold));
    }
}
