package com.jftse.emulator.server.core.life.item.special;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CExpandCardSlotPacket;
import com.jftse.entities.database.model.coupon.Coupon;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.coupon.CouponRedemptionRepository;
import com.jftse.entities.database.repository.coupon.CouponRepository;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PocketService;
import com.jftse.server.core.service.impl.CouponServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** notes-v174: the coupon result rules (0x2138) and the card slot item 48 (0x1DE5). Mocked repositories / services only;
 * no Spring context, persistence or sockets. */
class CouponAndCardSlotTest {
    private ServiceManager previous;

    @AfterEach void restore() { ReflectionTestUtils.setField(ServiceManager.class, "instance", previous); }

    @Test void couponResultCodes() {
        previous = ServiceManager.getInstance();
        CouponRepository coupons = mock(CouponRepository.class);
        CouponRedemptionRepository redemptions = mock(CouponRedemptionRepository.class);
        CouponServiceImpl service = new CouponServiceImpl(coupons, redemptions);
        Coupon c = new Coupon();
        c.setCode("TESTCOUPON2026");
        c.setProductIndex(1);
        when(coupons.findByCode("TESTCOUPON2026")).thenReturn(Optional.of(c));

        assertSame(c, service.findByCode(" testcoupon2026 "), "trimmed and upper-cased");
        assertEquals(-5, service.check(null, 1L), "unknown serial: WRONG");
        assertEquals(0, service.check(c, 1L));
        c.setEnabled(false);
        assertEquals(-1, service.check(c, 1L), "disabled: NA");
        c.setEnabled(true);
        c.setValidFrom(new Date(System.currentTimeMillis() + 86_400_000L));
        assertEquals(-1, service.check(c, 1L), "not yet valid: NA");
        c.setValidFrom(null);
        c.setValidTo(new Date(System.currentTimeMillis() - 1000L));
        assertEquals(-4, service.check(c, 1L), "expired");
        c.setValidTo(null);
        when(redemptions.existsByCouponAndAccountId(c, 1L)).thenReturn(true);
        assertEquals(-2, service.check(c, 1L), "used by this account");
        c.setMaxUses(3);
        when(redemptions.countByCoupon(c)).thenReturn(3L);
        assertEquals(-3, service.check(c, 2L), "all uses taken");
        assertEquals(-2, service.check(c, 1L), "own use is reported first");
    }

    @Test void cardSlotExpansion() {
        previous = ServiceManager.getInstance();
        ServiceManager sm = mock(ServiceManager.class);
        PocketService pockets = mock(PocketService.class);
        PlayerPocketService items = mock(PlayerPocketService.class);
        when(sm.getPocketService()).thenReturn(pockets);
        when(sm.getPlayerPocketService()).thenReturn(items);
        ReflectionTestUtils.setField(ServiceManager.class, "instance", sm);

        Pocket pocket = new Pocket();
        pocket.setId(9L);
        PlayerPocket item = new PlayerPocket();
        item.setId(77L);
        item.setItemCount(2);
        when(pockets.findById(9L)).thenReturn(pocket);
        when(pockets.save(any(Pocket.class))).thenAnswer(a -> a.getArgument(0));
        when(items.getItemAsPocketByItemIndexAndCategoryAndPocket(eq(48), eq("SPECIAL"), any(Pocket.class))).thenReturn(item);

        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(5L);
        for (int n = 1; n <= 3; n++) {
            CardSlotExpansion use = new CardSlotExpansion(48, "Slot Expansion Item", "SPECIAL");
            assertTrue(use.processPlayer(player));
            item.setItemCount(2);
            assertTrue(use.processPocket(9L), "use " + n);
            assertEquals(n, pocket.getCardSlotCount().intValue());
            List<IPacket> sent = use.getPacketsToSend().get(5L);
            assertInstanceOf(S2CExpandCardSlotPacket.class, sent.get(0));
            assertEquals(2, sent.size(), "answer + item count");
        }
        CardSlotExpansion over = new CardSlotExpansion(48, "Slot Expansion Item", "SPECIAL");
        over.processPlayer(player);
        assertFalse(over.processPocket(9L), "4 slots at most (MSG_CARD_LAST_SLOT)");
        assertEquals(3, pocket.getCardSlotCount().intValue());
        assertEquals(1, over.getPacketsToSend().get(5L).size(), "only the 0xFF answer, nothing consumed");
    }
}
