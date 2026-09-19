package com.jftse.web.service;

import com.jftse.entities.database.model.account.Account;
import com.jftse.entities.database.model.ServerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * C (cash) balance and its ledger.
 *
 * C is ours alone: the game never sees it. Players top C up (TrueMoney voucher) and then convert C into
 * AP, which is the currency the game's shop actually spends. Keeping them apart means AP can also be
 * handed out by events without it looking like purchased currency.
 *
 * Rates: 1 THB = {@link #C_PER_BAHT} C · 1 C = {@link #AP_PER_C} AP.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class CashService {
    public static final int C_PER_BAHT = 10;
    public static final int AP_PER_C = 1;

    public static final String KIND_TOPUP = "TOPUP";
    public static final String KIND_EXCHANGE = "EXCHANGE";
    public static final String KIND_ADMIN = "ADMIN";

    private final JdbcTemplate jdbc;
    private final AccountWebService accounts;

    public int balance(Long accountId) {
        List<Integer> rows = jdbc.queryForList(
                "SELECT balance_c FROM web_cash_balance WHERE account_id = ?", Integer.class, accountId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    public List<Map<String, Object>> history(Long accountId, int limit) {
        return jdbc.queryForList(
                "SELECT delta_c, kind, detail, created FROM web_cash_ledger WHERE account_id = ? ORDER BY id DESC LIMIT " + limit,
                accountId);
    }

    public boolean refUsed(String ref) {
        return !jdbc.queryForList("SELECT id FROM web_cash_ledger WHERE ref = ?", Long.class, ref).isEmpty();
    }

    /**
     * Writes the ledger row first: `ref` is UNIQUE, so a replayed voucher fails here before any balance
     * moves. Returns false when this ref was already credited.
     */
    @Transactional
    public boolean move(Long accountId, int deltaC, String kind, String ref, String detail) {
        try {
            jdbc.update("INSERT INTO web_cash_ledger (account_id, delta_c, kind, ref, detail) VALUES (?, ?, ?, ?, ?)",
                    accountId, deltaC, kind, ref, detail);
        } catch (DuplicateKeyException e) {
            log.warn("cash move rejected, ref already used: {}", ref);
            return false;
        }
        jdbc.update("""
                INSERT INTO web_cash_balance (account_id, balance_c) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE balance_c = balance_c + VALUES(balance_c)
                """, accountId, deltaC);
        return true;
    }

    public static int cashForBaht(int baht) {
        return baht * C_PER_BAHT;
    }

    /** The game keeps AP in memory while a player is online and writes it back on a purchase. */
    public boolean accountIsOnline(Account account) {
        ServerType server = account.getLoggedInServer();
        return server != null && server != ServerType.NONE;
    }

    /**
     * C -> AP. Refuses while the account is logged in: the game servers hold AP in memory for an online
     * player and overwrite the row on the next purchase, which would silently eat the topped-up AP.
     */
    @Transactional
    public String exchangeToAp(Account account, int amountC) {
        if (amountC <= 0)
            return "จำนวนต้องมากกว่า 0";
        if (accountIsOnline(account))
            return "กรุณาออกจากเกมก่อนแลก (ระหว่างออนไลน์ เซิร์ฟเกมถือค่า AP ไว้ในหน่วยความจำ)";
        if (balance(account.getId()) < amountC)
            return "C ไม่พอ";

        String ref = "EXCHANGE:%d:%d".formatted(account.getId(), System.currentTimeMillis());
        if (!move(account.getId(), -amountC, KIND_EXCHANGE, ref, "แลกเป็น AP %d".formatted(amountC * AP_PER_C)))
            return "ทำรายการซ้ำ กรุณาลองใหม่";
        accounts.addAp(account, amountC * AP_PER_C);
        return null;
    }
}
