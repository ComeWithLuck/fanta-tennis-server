package com.jftse.entities.database.repository.coupon;

import com.jftse.entities.database.model.coupon.Coupon;
import com.jftse.entities.database.model.coupon.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
    boolean existsByCouponAndAccountId(Coupon coupon, Long accountId);

    long countByCoupon(Coupon coupon);
}
