package com.jftse.server.core.service.impl;

import com.jftse.emulator.common.utilities.ResourceUtil;
import com.jftse.entities.database.model.emblem.EmblemQuestLog;
import com.jftse.entities.database.model.emblem.EmblemSlot;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.repository.emblem.EmblemQuestLogRepository;
import com.jftse.entities.database.repository.emblem.EmblemSlotRepository;
import com.jftse.entities.database.repository.pet.PetRepository;
import com.jftse.entities.database.repository.tutorial.TutorialProgressRepository;
import com.jftse.server.core.emblem.EmblemCounters;
import com.jftse.server.core.emblem.EmblemQuestData;
import com.jftse.server.core.emblem.EmblemQuestRules;
import com.jftse.server.core.service.EmblemQuestService;
import com.jftse.server.core.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@Service
@RequiredArgsConstructor
public class EmblemQuestServiceImpl implements EmblemQuestService {
    private final EmblemQuestLogRepository repository;
    private final PlayerService playerService;
    private final PetRepository petRepository;
    private final TutorialProgressRepository tutorialProgressRepository;
    private final EmblemSlotRepository emblemSlotRepository;

    private final Map<Long, short[]> slotCache = new ConcurrentHashMap<>();

    private volatile EmblemQuestData data;

    @Override
    public List<EmblemQuestLog> log(long playerId) { return repository.findAllByPlayerIdOrderByIdAsc(playerId); }

    @Override
    public EmblemQuestLog entry(long playerId, int questIndex) { return repository.findByPlayerIdAndQuestIndex(playerId, questIndex).orElse(null); }

    @Override
    public EmblemQuestLog save(EmblemQuestLog entry) { return repository.save(entry); }

    @Override
    public EmblemQuestData data() {
        if (data == null) {
            synchronized (this) {
                if (data == null) {
                    try (InputStream in = ResourceUtil.getResource("res/emblem/Ini3_EmblemQuest.xml")) {
                        data = EmblemQuestData.load(in);
                    } catch (Exception e) {
                        throw new IllegalStateException("res/emblem/Ini3_EmblemQuest.xml", e);
                    }
                }
            }
        }
        return data;
    }

    private static int n(Integer v) { return v == null ? 0 : v; }

    @Override
    public EmblemCounters counters(long playerId) {
        Player p = playerService.findWithStatisticById(playerId);
        PlayerStatistic s = p == null ? null : p.getPlayerStatistic();
        if (s == null)
            s = new PlayerStatistic();
        int tutorials = (int) tutorialProgressRepository.findAllByPlayerId(playerId).stream().filter(t -> n(t.getSuccess()) > 0).count();
        int petLevel = petRepository.findAllByPlayerId(playerId).stream().map(Pet::getLevel).filter(l -> l != null).mapToInt(Byte::intValue).max().orElse(0);
        return new EmblemCounters(n(s.getBasicRecordWin()), n(s.getBasicRecordLoss()), n(s.getBattleRecordWin()), n(s.getBattleRecordLoss()),
                n(s.getGuardBreakShot()), n(s.getSmash()), n(s.getSlice()), n(s.getChargeShot()), n(s.getLob()), n(s.getSkillShot()),
                n(s.getServiceAce()), n(s.getReturnAce()), tutorials, petLevel, p == null ? 0 : p.getLevel(), false,
                p == null ? 0 : n(p.getCouplePoints()));
    }

    private static void setBases(EmblemQuestLog e, int[] b) {
        e.setBase1(b[0]);
        e.setBase2(b[1]);
        e.setBase3(b[2]);
        e.setBase4(b[3]);
    }

    @Override
    public boolean accept(long playerId, int questIndex) {
        EmblemQuestData.Quest q = data().quest(questIndex);
        Player p = playerService.findById(playerId);
        List<EmblemQuestLog> log = log(playerId);
        if (p == null || !EmblemQuestRules.canAccept(q, log, p.getLevel()))
            return false;
        EmblemCounters c = counters(playerId);
        int[] base = new int[4];
        for (int i = 0; i < 4; i++) {
            EmblemQuestData.Condition cond = q.conditions()[i];
            // C = the plain counter at accept (progress = counter - C, 0x45e1f2..); Total / pass-fail slots keep 0 [I: native rule].
            if (cond.type() != 0 && !cond.total() && !EmblemQuestRules.passFail(cond.type()))
                base[i] = c.value(cond.name(), q.gameMode());
        }
        EmblemQuestLog e = EmblemQuestRules.entry(log, questIndex);
        if (e == null) {
            e = new EmblemQuestLog();
            e.setPlayerId(playerId);
            e.setQuestIndex(questIndex);
        }
        e.setInProgress(true);
        setBases(e, base);
        repository.save(e);
        return true;
    }

    @Override
    public boolean abandon(long playerId, int questIndex) {
        EmblemQuestLog e = entry(playerId, questIndex);
        if (e == null || !Boolean.TRUE.equals(e.getInProgress()))
            return false;
        e.setInProgress(false);
        setBases(e, new int[4]);
        // An entry never completed goes away (the quest shows as available again) [I].
        if (n(e.getCompleteCount()) == 0)
            repository.delete(e);
        else
            repository.save(e);
        return true;
    }

    @Override
    public EmblemQuestLog markCompleted(long playerId, int questIndex) {
        EmblemQuestLog e = entry(playerId, questIndex);
        if (e == null) {
            e = new EmblemQuestLog();
            e.setPlayerId(playerId);
            e.setQuestIndex(questIndex);
        }
        e.setCompleteCount(Math.min(Short.MAX_VALUE, n(e.getCompleteCount()) + 1));
        e.setInProgress(false);
        setBases(e, new int[4]);
        return repository.save(e);
    }

    @Override
    public short[] slots(long playerId) {
        return slotCache.computeIfAbsent(playerId, id -> emblemSlotRepository.findByPlayerId(id)
                .map(s -> new short[]{(short) n(s.getSlot1()), (short) n(s.getSlot2()), (short) n(s.getSlot3()), (short) n(s.getSlot4())})
                .orElseGet(() -> new short[4])).clone();
    }

    @Override
    public short[] applySlots(long playerId, short[] wanted) {
        List<EmblemQuestLog> log = log(playerId);
        short[] out = new short[4];
        for (int i = 0; i < 4 && i < wanted.length; i++) {
            int index = wanted[i] & 0xFFFF;
            boolean owned = index >= 1000 && index < 2000 && log.stream().anyMatch(e -> e.getQuestIndex() == index && n(e.getCompleteCount()) > 0);
            boolean twice = false;
            for (int k = 0; k < i; k++)
                twice |= out[k] == (short) index;
            out[i] = owned && !twice ? (short) index : 0;
        }
        EmblemSlot slot = emblemSlotRepository.findByPlayerId(playerId).orElseGet(() -> {
            EmblemSlot s = new EmblemSlot();
            s.setPlayerId(playerId);
            return s;
        });
        slot.setSlot1((int) out[0]);
        slot.setSlot2((int) out[1]);
        slot.setSlot3((int) out[2]);
        slot.setSlot4((int) out[3]);
        emblemSlotRepository.save(slot);
        slotCache.put(playerId, out.clone());
        return out;
    }
}
