package com.jftse.server.core.emblem;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.*;

// Ini3_EmblemQuest.set as the client loads it (loader 0x461110, notes-v185 / emblem RE): Quest rows with their QuestDetail copied in
// (0x45f910), and the RewardItem_<CHAR> rows (0x4616e6, charType order NIKI LUNLUN DHANPIR LUCY SHUA POCHI AL, 0x75d104).
public final class EmblemQuestData {
    // ConditionType names, value = row + 1 (static table 0x75c428). WinPercentage / WorldQuest get no value (slot type 0).
    public static final List<String> CONDITION_TYPES = List.of("WinCount", "TotalWinCount", "LoseCount", "TotalLoseCount", "GuardBreak",
            "TotalGuardBreak", "PerfectGame", "TotalPerfectGame", "Smash", "TotalSmash", "Slice", "TotalSlice", "ChargeShot", "TotalChargeShot",
            "Lob", "TotalLob", "SkillShot", "TotalSkillShot", "ServiceAce", "TotalServiceAce", "ReturnAce", "TotalReturnAce", "Fishes",
            "TotalFishes", "Fruits", "TotalFruits", "Transmutes", "TotalTransmutes", "Furniture", "Tutorial", "BattleMonLevel",
            "CharacterLevel", "EnchantStatCount", "TotalEnchantStatCount", "EnchantElementCount", "TotalEnchantElementCount", "IsCouple",
            "CouplePoint");
    // GameMode (table 0x75c404).
    public static final List<String> GAME_MODES = List.of("BASIC", "BATTLE", "BATTLEMON", "GUARDIAN", "SINGLE", "QUEST", "EMBLEM", "BETTING");
    public static final List<String> REWARD_CHARS = List.of("NIKI", "LUNLUN", "DHANPIR", "LUCY", "SHUA", "POCHI", "AL");

    public record Condition(int type, String name, int count) {
        public boolean total() { return name.startsWith("Total"); }
    }

    public record Require(int product, int qty) {}

    public record RewardRow(int index, int shopIndex, int min, int max) {}

    public record Quest(int index, boolean enable, boolean event, int grade, List<Integer> prereq, boolean repeat, boolean itemRepeat,
                        int exp, int gold, int[] rewards, int gameMode, int level, float winPercentage, List<Integer> worldQuest,
                        // 4 slots in ConditionType1..4 order (the 0x226B C baseline is per slot); type 0 = empty / untyped
                        Condition[] conditions, List<Require> require) {}

    private final Map<Integer, Quest> quests;
    private final Map<Integer, Map<Integer, RewardRow>> rewardRows;

    private EmblemQuestData(Map<Integer, Quest> quests, Map<Integer, Map<Integer, RewardRow>> rewardRows) {
        this.quests = quests;
        this.rewardRows = rewardRows;
    }

    public Quest quest(int index) { return quests.get(index); }

    public Collection<Quest> quests() { return quests.values(); }

    // 0x45e940(charType, row)
    public RewardRow rewardRow(int charType, int row) {
        Map<Integer, RewardRow> rows = rewardRows.get(charType);
        return row <= 0 || rows == null ? null : rows.get(row);
    }

    public static boolean isEmblem(int index) { return index >= 1000 && index < 2000 || index >= 3000; }

    private static int num(Element e, String name) {
        String v = e.getAttribute(name).trim();
        if (v.isEmpty())
            return 0;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static List<Integer> list(String v) {
        List<Integer> out = new ArrayList<>();
        for (String s : v.split(",")) {
            s = s.trim();
            if (s.isEmpty())
                continue;
            try {
                out.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static EmblemQuestData load(InputStream in) throws Exception {
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        Map<String, Element> details = new HashMap<>();
        NodeList dn = doc.getElementsByTagName("QuestDetail");
        for (int i = 0; i < dn.getLength(); i++) {
            Element e = (Element) dn.item(i);
            details.put(e.getAttribute("Index"), e);
        }
        Map<Integer, Quest> quests = new LinkedHashMap<>();
        NodeList qn = doc.getElementsByTagName("Quest");
        for (int i = 0; i < qn.getLength(); i++) {
            Element a = (Element) qn.item(i);
            Element d = details.get(a.getAttribute("QuestDetail"));
            Condition[] conditions = new Condition[4];
            List<Require> require = new ArrayList<>();
            int gameMode = -1, level = 0;
            float win = 0;
            List<Integer> worldQuest = List.of();
            for (int n = 0; n < 4; n++)
                conditions[n] = new Condition(0, "", 0);
            if (d != null) {
                gameMode = GAME_MODES.indexOf(d.getAttribute("GameMode"));
                level = num(d, "LevelRestriction");
                String w = d.getAttribute("WinPercentage").trim();
                win = w.isEmpty() ? 0 : Float.parseFloat(w);
                worldQuest = list(d.getAttribute("WorldQuest"));
                for (int n = 0; n < 4; n++) {
                    String type = d.getAttribute("ConditionType" + (n + 1));
                    int value = CONDITION_TYPES.indexOf(type) + 1;
                    conditions[n] = new Condition(value, value > 0 ? type : "", num(d, "ConditionCount" + (n + 1)));
                }
                for (int n = 1; n <= 4; n++)
                    if (num(d, "RequireItem" + n) > 0)
                        require.add(new Require(num(d, "RequireItem" + n), num(d, "Quantity" + n)));
            }
            int index = num(a, "Index");
            // QuestPreReq: comma list, atoi, values <= 0 dropped (0x45f450).
            List<Integer> prereq = list(a.getAttribute("QuestPreReq")).stream().filter(v -> v > 0).toList();
            quests.put(index, new Quest(index, "1".equals(a.getAttribute("Enable")), !Set.of("", "0").contains(a.getAttribute("Event")),
                    num(a, "EmblemGrade"), prereq, "Yes".equals(a.getAttribute("QuestRepeat")), "Yes".equals(a.getAttribute("ItemRewardRepeat")),
                    num(a, "RewardEXP"), num(a, "RewardGOLD"),
                    new int[]{num(a, "RewardItem1"), num(a, "RewardItem2"), num(a, "RewardItem3"), num(a, "RewardItem4")},
                    gameMode, level, win, worldQuest, conditions, require));
        }
        Map<Integer, Map<Integer, RewardRow>> rows = new HashMap<>();
        for (int c = 0; c < REWARD_CHARS.size(); c++) {
            Map<Integer, RewardRow> byIndex = new HashMap<>();
            NodeList rn = doc.getElementsByTagName("RewardItem_" + REWARD_CHARS.get(c));
            for (int i = 0; i < rn.getLength(); i++) {
                Element e = (Element) rn.item(i);
                RewardRow row = new RewardRow(num(e, "Index"), num(e, "ShopIndex"), num(e, "QuantityMin"), num(e, "QuantityMax"));
                byIndex.put(row.index(), row);
            }
            rows.put(c, byIndex);
        }
        return new EmblemQuestData(quests, rows);
    }
}
