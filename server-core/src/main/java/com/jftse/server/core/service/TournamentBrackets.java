package com.jftse.server.core.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Bracket arithmetic shared by the tournament service and its tests (notes-v184). Client facts [C] (Gui_TournamentBracket
// RE, notes-v184): a bracket of T teams (T = record +0x74 for a qualifying group; the final has 2 * groups teams with
// hasQualifying, else +0x74) has 2T - 1 nodes in a binary heap: node 0 is the final match, the children of p are 2p + 1 and
// 2p + 2, team i of the 0x26C1 list sits on leaf node T - 1 + i; 0x26C3 carries T - 1 records {i8 winner team index or -1,
// u8 howWin} for nodes 0 .. T - 2, and the client rejects a winner that is not one of the node's two children's teams.
// howWin: 0 unsettled, 1 normal, 2 / 3 win by default, 4 unusual (GUI_TOURNEY_HOW_WIN_*).
// Server rules [I: not in the client]: T must be a power of two (2..64); leaves are filled in entry order, empty leaves are
// byes; a match with a team on one side and a bye subtree on the other is won by default (howWin 2) at once.
public final class TournamentBrackets {
    public static final byte HOW_UNSETTLED = 0, HOW_NORMAL = 1, HOW_DEFAULT = 2;

    private TournamentBrackets() {}

    public static boolean validSize(int teams) {
        return teams >= 2 && teams <= 64 && Integer.bitCount(teams) == 1;
    }

    /** Node winners (team index, -1 none) and how-win codes for the internal nodes, from the leaf occupancy and any decided
     *  inner results (decided[p] = team index or -1). Byes are resolved; decided results are kept when consistent. */
    public static int[][] resolve(boolean[] occupied, int[] decided, byte[] decidedHow) {
        int teams = occupied.length, nodes = 2 * teams - 1;
        int[] team = new int[nodes];
        boolean[] alive = new boolean[nodes]; // a team can still come out of this subtree
        byte[] how = new byte[nodes];
        Arrays.fill(team, -1);
        for (int i = 0; i < teams; i++) {
            team[teams - 1 + i] = occupied[i] ? i : -1;
            alive[teams - 1 + i] = occupied[i];
        }
        for (int p = teams - 2; p >= 0; p--) {
            int a = 2 * p + 1, b = 2 * p + 2;
            alive[p] = alive[a] || alive[b];
            int d = decided == null ? -1 : decided[p];
            if (d >= 0 && (d == team[a] || d == team[b]) && d >= 0) {
                team[p] = d;
                how[p] = decidedHow == null || decidedHow[p] == 0 ? HOW_NORMAL : decidedHow[p];
            } else if (team[a] >= 0 && !alive[b]) {
                team[p] = team[a];
                how[p] = HOW_DEFAULT;
            } else if (team[b] >= 0 && !alive[a]) {
                team[p] = team[b];
                how[p] = HOW_DEFAULT;
            }
        }
        int[] winners = Arrays.copyOf(team, teams - 1);
        int[] hows = new int[teams - 1];
        for (int p = 0; p < teams - 1; p++) hows[p] = team[p] < 0 ? 0 : how[p];
        return new int[][]{winners, hows};
    }

    /** The open matches: nodes whose two children both have a team and that have no winner yet. */
    public static List<Integer> openMatches(boolean[] occupied, int[] winners) {
        int teams = occupied.length;
        List<Integer> out = new ArrayList<>();
        for (int p = 0; p < teams - 1; p++) {
            if (winners[p] >= 0) continue;
            int a = 2 * p + 1, b = 2 * p + 2;
            if (teamAt(occupied, winners, a) >= 0 && teamAt(occupied, winners, b) >= 0) out.add(p);
        }
        return out;
    }

    public static int teamAt(boolean[] occupied, int[] winners, int node) {
        int teams = occupied.length;
        if (node >= teams - 1) return occupied[node - (teams - 1)] ? node - (teams - 1) : -1;
        return winners[node];
    }

    /** Qualifying group of the i-th entry (round robin) [I]. */
    public static int groupOf(int entryOrder, int groups) {
        return groups <= 1 ? 0 : entryOrder % groups;
    }

    /** Final leaf of a group's winner (place 0) / runner-up (place 1): winners on the left half, runners-up on the right [I]. */
    public static int finalLeaf(int group, int place, int groups) {
        return place == 0 ? group : groups + group;
    }
}
