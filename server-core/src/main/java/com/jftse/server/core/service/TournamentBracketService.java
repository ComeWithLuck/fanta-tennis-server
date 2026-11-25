package com.jftse.server.core.service;

import com.jftse.entities.database.model.tournament.Tournament;

import java.util.Date;
import java.util.List;

// Tournament brackets (notes-v184; the rules here are [I]: the native server's are not in the client). A bracket is made the
// first time it is asked for once the application period is over (state >= 2 and not 7 / 8): entries in id order go round
// robin into the qualifying groups (or all into the one bracket without qualifying) and fill the leaves in order; every
// placed entry becomes "entered" (my state 2) with its group. The final of a qualifying tournament is made when every group's
// final is decided: group g's winner on leaf g, its runner-up on leaf groups + g.
public interface TournamentBracketService {
    /** One bracket as the client reads it: the leaf entries (null = bye) and per internal node the winner team index (-1) and
     *  how-win code. */
    record Bracket(int teams, List<Long> leaves, int[] winners, int[] hows) {}

    /** Teams of a bracket as the client expects them (record +0x74 / the final's 2 * groups), or 0 when it cannot be made. */
    static int teams(Tournament t, boolean isFinal) {
        int groups = groups(t);
        int teams = isFinal && Boolean.TRUE.equals(t.getHasQualifying()) ? 2 * groups : t.getMaxTeams() == null ? 0 : t.getMaxTeams();
        return TournamentBrackets.validSize(teams) ? teams : 0;
    }

    static int groups(Tournament t) {
        int g = t.getQualifyingGroups() == null ? 0 : t.getQualifyingGroups();
        return Boolean.TRUE.equals(t.getHasQualifying()) ? Math.max(1, g) : 1;
    }

    /** The bracket, made on first use; null = MSG_TOURNEY_MATCH_MADE_NOT_YET. */
    Bracket bracket(Tournament tournament, boolean isFinal, int group, Date now);

    /** The character's open match: both sides known, no winner yet, in the bracket being played now (state 3 = the qualifying
     *  group of the entry, state 5 = the final) [I]. matchId is the TournamentMatch row of the node. */
    record MyMatch(long matchId, boolean isFinal, int group, int node, long myEntry, long opponentEntry, short roomId) {}

    MyMatch myMatch(Tournament tournament, long playerId, Date now);

    void setRoom(long matchId, short roomId);

    /** The bracket node's TournamentMatch row id (null when the bracket is not made or the node has no row); the bracket is
     *  picked as bracket() picks it (the final when isFinal or the tournament has no qualifying). Used by GM spectate 0x2416. */
    Long matchId(Tournament tournament, boolean isFinal, int group, int node);

    /** Records the winner of an open match (by player id) [I: one game decides it, howWin 1 normal]; the loser's entry becomes
     *  dropped (3), the final's winner the champion (4). Returns false when the match is not open or the player is not in it. */
    record Recorded(boolean recorded, boolean champion, List<com.jftse.entities.database.model.pocket.PlayerPocket> rewards) {}

    /** ... and when it decides the final, the tournament's reward pairs go to the champion's pocket [I: delivery not in the client;
     *  the five {productIndex, useOption} of the record +0x7c, quantities as a shop buy]. */
    Recorded record(long matchId, long winnerPlayerId, byte winnerScore, byte loserScore);

    /** The same with the match's howWin (TournamentBrackets.HOW_*), e.g. HOW_DEFAULT for a walkover (notes-v198). */
    Recorded record(long matchId, long winnerPlayerId, byte winnerScore, byte loserScore, byte howWin);

    /** Entry id -> player name (empty when unknown). */
    String name(Long entryId);

    /** Player names of the leaves (empty for a bye), in leaf order. */
    List<String> names(Bracket bracket);

    /** Club tournament leaves (0x26C1 entry type 0, 5cafd0 reads three names per team): {club name, member, partner} per
     *  leaf, empty strings for a bye (notes-v401). */
    List<String[]> teamNames(Bracket bracket);

    /** The entry's players: {playerId, partnerId (0 for an individual entry)}, or null. */
    long[] players(long entryId);
}
