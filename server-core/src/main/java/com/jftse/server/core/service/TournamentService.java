package com.jftse.server.core.service;

import com.jftse.entities.database.model.tournament.Tournament;
import com.jftse.entities.database.model.tournament.TournamentEntry;

import java.util.Date;
import java.util.List;

// Tournament browse / individual apply (notes-v176; client Gui_Tournament 0x14 / Gui_TournamentMenu 0x15).
// state(): TOURNEY_STATE_* (client 5c7ec0) 0 prepare, 1 apply, 2 prepare qualifying, 3 qualifying, 4 prepare final, 5 final,
// 6 finished, 7 suspended, 8 canceled. Without a stored state it follows the periods [I: the native server's state machine
// is not in the client]. apply() / cancel() return the S2C 0x26B2 / 0x26B4 result codes (client 5c7330 / 5c7530):
// apply 0 ok, -2 no such tournament, -3 a guild (team) tournament, -4 another character of this account applied, -5 this
// character is already in a running tournament (the answer then carries its title), -6 not the application period, -7 full;
// cancel 0 ok, -1 not applied, -2 not the application period.
public interface TournamentService {
    byte APPLY_OK = 0, APPLY_NO_TOURNAMENT = -2, APPLY_TEAM_ONLY = -3, APPLY_ACCOUNT = -4, APPLY_ALREADY = -5,
        APPLY_NOT_PERIOD = -6, APPLY_FULL = -7;
    byte CANCEL_OK = 0, CANCEL_NOT_APPLIED = -1, CANCEL_NOT_PERIOD = -2;
    // Club team answers (S2C 0x26B6, the client's 5c45b0 table, notes-v373 / v375 (ai-phases-rest)): -1 MSG_NO_CHARACTER_AT_CHARACTER_LIST,
    // -2 MSG_GUILD_MEMBER_FAILED_2 (not the club master), -22..-30 MSG_TOURNEY_FAILED_TO_DOUBLE_APPLY_01..09.
    byte TEAM_OK = 0, TEAM_NO_CHARACTER = -1, TEAM_NOT_MASTER = -2, TEAM_NO_TOURNAMENT = -22, TEAM_NOT_CLUB = -23,
        TEAM_ALREADY = -25, TEAM_NOT_PERIOD = -26, TEAM_FULL = -27, TEAM_CLUB_LIMIT = -30;
    /** At most 11 teams per club (MSG_TOURNEY_FAILED_TO_DOUBLE_APPLY_09 "Maximum 11 teams"). */
    int TEAMS_PER_CLUB = 11;

    List<Tournament> listAfter(long lastTournamentId);

    Tournament find(long tournamentId);

    byte state(Tournament tournament, Date now);

    TournamentEntry entry(Tournament tournament, long playerId);

    /** The running tournament (state below 6) this character applied to, other than the given one, or null. */
    Tournament activeOther(Tournament tournament, long playerId, Date now);

    byte apply(Tournament tournament, long accountId, long playerId, Date now);

    byte cancel(Tournament tournament, long playerId, Date now);

    /** The club's teams in this tournament, oldest first. */
    List<TournamentEntry> teams(Tournament tournament, long guildId);

    /** Registers {player1, player2} of the club; teamId[0] gets the new entry id on TEAM_OK. Membership / mastership are
     *  checked by the caller (the handler has the club records). */
    byte applyTeam(Tournament tournament, long guildId, long player1, long player2, Date now, long[] teamId);

    /** Removes the club's team; CANCEL_* results. */
    byte cancelTeam(Tournament tournament, long guildId, long teamId, Date now);

    /** rewards column -> up to five {productIndex, useOption} pairs (missing pairs are {0, 0}). */
    static int[][] rewards(String text) {
        int[][] out = new int[5][2];
        if (text == null || text.isBlank())
            return out;
        String[] parts = text.split(",");
        for (int i = 0; i < Math.min(5, parts.length); i++) {
            String[] pair = parts[i].trim().split(":");
            try {
                out[i][0] = Integer.parseInt(pair[0].trim());
                out[i][1] = pair.length > 1 ? Integer.parseInt(pair[1].trim()) : 0;
            } catch (NumberFormatException e) {
                out[i][0] = 0;
                out[i][1] = 0;
            }
        }
        return out;
    }
}
