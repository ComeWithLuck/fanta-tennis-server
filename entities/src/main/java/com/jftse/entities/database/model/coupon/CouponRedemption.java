package com.jftse.entities.database.model.coupon;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

// One redeemed coupon per account (notes-v174).
@Getter
@Setter
@Entity
public class CouponRedemption extends AbstractBaseModel {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;
    private Long accountId;
    private Long playerId;
}
