package com.jftse.web.model;

/**
 * The five account tiers. Three of them (NORMAL / SOFTBAN / BAN) are just how the emulator's own
 * account fields are set, so the game enforces them without any extra code:
 *
 *   NORMAL   status = 0
 *   SOFTBAN  status = -6, bannedUntil = a date  -> auth-server clears it by itself once that date passes
 *   BAN      status = -6, bannedUntil = null    -> never expires
 *
 * STAFF and GM are about what the web panel allows. GM also flips account.gameMaster, which is what the
 * in-game GM commands check; STAFF does not get that flag.
 */
public enum AccountTier {
    NORMAL("ผู้เล่นปกติ"),
    SOFTBAN("แบนชั่วคราว"),
    BAN("แบนถาวร"),
    STAFF("ทีมงาน"),
    GM("GM / ผู้ดูแล");

    private final String label;

    AccountTier(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isBanned() {
        return this == SOFTBAN || this == BAN;
    }

    /** Staff can do the day-to-day moderation; GM can do everything. */
    public boolean canModerate() {
        return this == STAFF || this == GM;
    }

    public boolean canGrant() {
        return this == GM;
    }

    public boolean canBanPermanently() {
        return this == GM;
    }

    public boolean canChangeTiers() {
        return this == GM;
    }
}
