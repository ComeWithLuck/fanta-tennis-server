package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.coupon.Coupon;
import com.jftse.entities.database.model.coupon.CouponRedemption;
import com.jftse.entities.database.repository.coupon.CouponRedemptionRepository;
import com.jftse.entities.database.repository.coupon.CouponRepository;
import com.jftse.server.core.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {
    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;

    @Override
    public Coupon findByCode(String code) {
        if (code == null)
            return null;
        return couponRepository.findByCode(code.trim().toUpperCase()).orElse(null);
    }

    @Override
    public short check(Coupon coupon, long accountId) {
        if (coupon == null)
            return -5;
        Date now = new Date();
        if (!Boolean.TRUE.equals(coupon.getEnabled()) || (coupon.getValidFrom() != null && now.before(coupon.getValidFrom())))
            return -1;
        if (coupon.getValidTo() != null && now.after(coupon.getValidTo()))
            return -4;
        if (couponRedemptionRepository.existsByCouponAndAccountId(coupon, accountId))
            return -2;
        if (coupon.getMaxUses() != null && couponRedemptionRepository.countByCoupon(coupon) >= coupon.getMaxUses())
            return -3;
        return 0;
    }

    @Override
    @Transactional
    public void redeem(Coupon coupon, long accountId, long playerId) {
        CouponRedemption redemption = new CouponRedemption();
        redemption.setCoupon(coupon);
        redemption.setAccountId(accountId);
        redemption.setPlayerId(playerId);
        couponRedemptionRepository.save(redemption);
    }
}
