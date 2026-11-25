package com.jftse.server.core.service;

import com.jftse.entities.database.model.coupon.Coupon;

// Coupon redeem rules (notes-v174). check() returns the S2C 0x2138 result: 0 ok, -1 not available (disabled / not yet valid),
// -2 already used by this account, -3 all uses taken, -4 expired, -5 wrong serial (unknown or malformed).
public interface CouponService {
    Coupon findByCode(String code);

    short check(Coupon coupon, long accountId);

    void redeem(Coupon coupon, long accountId, long playerId);
}
