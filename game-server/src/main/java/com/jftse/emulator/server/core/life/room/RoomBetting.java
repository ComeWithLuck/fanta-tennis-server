package com.jftse.emulator.server.core.life.room;

import com.jftse.server.core.constants.GameMode;
import com.jftse.emulator.server.core.constants.RoomStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

// Spectator gold betting (user-approved 2026-11-11 "Betting + special events"; notes-v179). Client facts [C]:
// - Room create 0x1389 carries the bet mode (char bettingType: low byte 0 none / 1 item / 2 gold / 3 both, high byte the
//   coin count) and the gold amount (GuiPop_MakeRoom 56a890; amounts of Item_Betting_Ini3: 100 / 500 / 1000 / 5000 / 10000);
//   betting only in Basic (battle MSG_CAN_NOT_BETTING_PET_MODE, guardian ..._GUARDIAN_MODE).
// - Spectators (positions 5..8) bet: C 0x1DB0 {u8 betMode, u8 side 0 red / 1 blue} -> S 0x1DB1 u8 (0 ok, -1 / -2
//   MSG_BETTING_FAILED_01 / 02, -3 MSG_NEED_MORE_COIN, -4 MSG_NEED_MORE_GOLD, -5 MSG_CAN_NOT_BETTING_IN_RULE) and the room
//   gets S 0x1DB4 {i16 position, u8 side}; C 0x1DB2 cancels -> S 0x1DB3 u8 (0xff / 0xfe CANCEL_FAILED_01 / 02) and S 0x1DB5
//   {i16 position}. The player record's side byte (before the emblem shorts, 0x494313) is 0xff when not betting.
// - The client refuses to start with no bettor on a side (MSG_CAN_NOT_START_BETTING_GAME); the server's start answer -11 is
//   MSG_NO_BETTING_USER (0x4cc8e8).
// Server rules [I: the native server's betting rules are not in the client]: gold only; the stake is the room's amount, taken
// when the bet is placed and given back on cancel or when the bettor leaves before the match; one bet per spectator; at the
// end of a Basic match the stakes of both sides are shared equally by the winning side's bettors (the remainder of the
// division is not paid); with no winning bettor every stake is returned.
public final class RoomBetting {
    public static final byte NONE = (byte) 0xff, RED = 0, BLUE = 1;
    public static final byte MODE_GOLD = 2;
    public static final Set<Integer> GOLD_AMOUNTS = Set.of(100, 500, 1000, 5000, 10000);
    // -1 MSG_BETTING_FAILED_01 "You already betted.", -2 _02 "The Player can't bet.", -4 MSG_NEED_MORE_GOLD (texts of the client).
    public static final byte OK = 0, ALREADY = -1, CANNOT = -2, NEED_GOLD = -4;
    // 0xff MSG_BETTING_CANEL_FAILED_01 "There's no betted team.", 0xfe _02 "The Player can't bet." (the match is starting).
    public static final byte CANCEL_OK = 0, CANCEL_FAILED_01 = (byte) 0xff, CANCEL_FAILED_02 = (byte) 0xfe;
    public static final short START_NO_BETTOR = -11;

    private RoomBetting() {}

    /** The bettingType char a created room keeps: gold betting only for a Basic room with a listed amount, else none. */
    public static char sanitize(char bettingType, int amount, byte mode) {
        return (bettingType & 0xff) == MODE_GOLD && mode == GameMode.BASIC && GOLD_AMOUNTS.contains(amount) ? (char) MODE_GOLD : (char) 0;
    }

    /** The room's bet mode byte as the client reads it (+0x333): 2 for a gold-betting room, else 0. */
    public static byte mode(Room room) {
        return room != null && (room.getBettingType() & 0xff) == MODE_GOLD && room.getMode() == GameMode.BASIC && room.getBettingAmount() > 0 ? MODE_GOLD : 0;
    }

    public static boolean isSpectator(RoomPlayer rp) {
        return rp != null && rp.getPosition() >= 5 && rp.getPosition() <= 8;
    }

    public static boolean idle(Room room) {
        int status = room.getStatus();
        return status == RoomStatus.NotRunning || status == RoomStatus.Idle || status == RoomStatus.StartCancelled || status == RoomStatus.RelayConnectionFailed;
    }

    /** 0x1DB1 result for a bet; the caller takes the stake when OK. */
    public static byte check(Room room, RoomPlayer rp, byte betMode, byte side, int gold) {
        if (rp != null && rp.getBettingSide() != NONE)
            return ALREADY;
        if (room == null || rp == null || !isSpectator(rp) || !idle(room) || (side != RED && side != BLUE) ||
                mode(room) != MODE_GOLD || betMode != MODE_GOLD)
            return CANNOT;
        if (gold < room.getBettingAmount())
            return NEED_GOLD;
        return OK;
    }

    /** 0x1DB3 result for a cancel; the caller returns the stake when CANCEL_OK. */
    public static byte checkCancel(Room room, RoomPlayer rp) {
        if (room == null || rp == null || rp.getBettingSide() == NONE)
            return CANCEL_FAILED_01;
        if (!idle(room))
            return CANCEL_FAILED_02;
        return CANCEL_OK;
    }

    public static int count(Collection<RoomPlayer> players, byte side) {
        return (int) players.stream().filter(p -> isSpectator(p) && p.getBettingSide() == side).count();
    }

    /** The start answer: -11 when a betting room lacks a bettor on either side, else 0. */
    public static short startCheck(Room room) {
        if (mode(room) != MODE_GOLD)
            return 0;
        return count(room.getRoomPlayerList(), RED) == 0 || count(room.getRoomPlayerList(), BLUE) == 0 ? START_NO_BETTOR : 0;
    }

    /** The payout per bettor of the given list; a stake back for everyone when no bettor won. */
    public record Payout(RoomPlayer bettor, int gold) {}

    public static List<Payout> settle(Collection<RoomPlayer> players, int amount, boolean redWon) {
        List<RoomPlayer> bettors = players.stream().filter(p -> isSpectator(p) && p.getBettingSide() != NONE).toList();
        byte winning = redWon ? RED : BLUE;
        List<RoomPlayer> winners = bettors.stream().filter(p -> p.getBettingSide() == winning).toList();
        List<Payout> out = new ArrayList<>();
        if (winners.isEmpty()) {
            bettors.forEach(p -> out.add(new Payout(p, amount)));
            return out;
        }
        int share = (int) Math.min(Integer.MAX_VALUE, (long) amount * bettors.size() / winners.size());
        for (RoomPlayer p : bettors)
            out.add(new Payout(p, p.getBettingSide() == winning ? share : 0));
        return out;
    }
}
