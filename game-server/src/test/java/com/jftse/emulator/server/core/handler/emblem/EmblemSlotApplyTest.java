package com.jftse.emulator.server.core.handler.emblem;

import com.jftse.entities.database.model.emblem.EmblemQuestLog;
import com.jftse.entities.database.model.emblem.EmblemSlot;
import com.jftse.entities.database.repository.emblem.EmblemQuestLogRepository;
import com.jftse.entities.database.repository.emblem.EmblemSlotRepository;
import com.jftse.server.core.service.impl.EmblemQuestServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** notes-v185 E3: 0x2295 keeps owned emblems (1000..1999, completed) once each; the stored slots are cached for the room records. */
class EmblemSlotApplyTest {
    static EmblemQuestLog done(int index, int complete) { EmblemQuestLog e = new EmblemQuestLog(); e.setQuestIndex(index); e.setCompleteCount(complete); return e; }

    @Test void keepsOwnedEmblemsOnce() {
        EmblemQuestLogRepository log = mock(EmblemQuestLogRepository.class);
        EmblemSlotRepository slots = mock(EmblemSlotRepository.class);
        when(log.findAllByPlayerIdOrderByIdAsc(7L)).thenReturn(List.of(done(1000, 1), done(1020, 1), done(1003, 0), done(5, 2)));
        when(slots.findByPlayerId(7L)).thenReturn(Optional.empty());
        when(slots.save(any(EmblemSlot.class))).thenAnswer(a -> a.getArgument(0));
        EmblemQuestServiceImpl service = new EmblemQuestServiceImpl(log, null, null, null, slots);
        // 1003 not completed and 1000 a second time -> 0 in their slots; the others keep their position.
        assertArrayEquals(new short[]{1000, 0, 0, 1020}, service.applySlots(7L, new short[]{1000, 1003, 1000, 1020}));
        assertArrayEquals(new short[]{0, 0, 0, 0}, service.applySlots(7L, new short[]{5, (short) 2000, 0, 0}));
        assertArrayEquals(new short[]{0, 0, 0, 0}, service.slots(7L));
        verify(slots, times(2)).save(any(EmblemSlot.class));
    }
}
