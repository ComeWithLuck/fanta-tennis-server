package com.jftse.entities.database.model.coupon;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import java.util.Date;

// Coupon redeem (C2S 0x2137 / S2C 0x2138, notes-v174; user-approved minimal schema 2026-11-10). One row per serial: the shop
// product it grants (productIndex + useOption, as a shop buy), an optional validity window, an optional total use limit and
// an enabled flag. Each account may redeem a serial once (coupon_redemption) [I: native per-account rule].
@Getter
@Setter
@Entity
public class Coupon extends AbstractBaseModel {
    @Column(length = 16, unique = true, nullable = false)
    private String code;
    private Integer productIndex;
    private Byte useOption = 0;
    private Date validFrom;
    private Date validTo;
    private Integer maxUses;
    @Column(columnDefinition = "bit(1) DEFAULT 1")
    private Boolean enabled = true;
}
