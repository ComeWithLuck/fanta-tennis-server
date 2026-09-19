package com.jftse.web.service;

import com.jftse.entities.database.model.account.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Top-up with a TrueMoney gift voucher. There is no admin approval step: the voucher itself is the
 * proof, so a successful redeem credits C immediately and Telegram is only told about it.
 *
 * Order of operations matters: verify (read-only) -> all checks -> redeem (money moves) -> credit.
 * Anything rejected before redeem leaves the voucher with the player, who can take it back in the app.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class TopUpService {
    /** how many vouchers one account may try per day, successful or not */
    public static final int MAX_ATTEMPTS_PER_DAY = 20;

    private final TrueMoneyGiftClient truemoney;
    private final CashService cash;
    private final TelegramService telegram;
    private final JdbcTemplate jdbc;

    public record Outcome(boolean ok, String message, int cashAdded) {
    }

    public boolean isEnabled() {
        return truemoney.isEnabled();
    }

    public boolean isMock() {
        return truemoney.isMock();
    }

    public Outcome redeem(Account account, String link) {
        String hash = TrueMoneyGiftClient.extractHash(link);
        if (hash == null)
            return new Outcome(false, "ลิงก์ไม่ถูกต้อง — วางลิงก์ซองของขวัญจากแอป TrueMoney (ขึ้นต้นด้วย gift.truemoney.com)", 0);

        if (attemptsToday(account.getId()) >= MAX_ATTEMPTS_PER_DAY)
            return new Outcome(false, "วันนี้ลองเติมหลายครั้งเกินไป กรุณาลองใหม่พรุ่งนี้", 0);

        String ref = "TMGIFT:" + hash;
        if (cash.refUsed(ref)) {
            record(account, hash, "verify", "ALREADY_USED", null, null);
            return new Outcome(false, "ซองนี้ถูกใช้เติมไปแล้ว", 0);
        }

        // 1) verify — read only
        TrueMoneyGiftClient.Result verify = truemoney.verify(hash);
        record(account, hash, "verify", verify.ok() ? "SUCCESS" : verify.code(), verify.amountBaht(), verify.raw());
        if (!verify.ok()) {
            String message = explain(verify.code(), verify.message());
            telegram.send("⚠️ เติมเงินไม่สำเร็จ (verify)\nบัญชี: %s\nเหตุผล: %s (%s)"
                    .formatted(account.getUsername(), message, verify.code()));
            return new Outcome(false, message, 0);
        }

        // 2) gates that must happen before the money moves
        if (verify.members() != null && verify.members() != 1) {
            telegram.send("⚠️ เติมเงินไม่สำเร็จ: ซองหลายคนรับ\nบัญชี: %s".formatted(account.getUsername()));
            return new Outcome(false, "ซองนี้ตั้งให้หลายคนรับ กรุณาสร้างซองแบบ \"ส่งให้ 1 คน\"", 0);
        }
        Integer baht = verify.amountBaht();
        if (baht == null || baht <= 0) {
            // fail closed: taking money without knowing the amount is worse than asking them to retry
            record(account, hash, "verify", "NO_AMOUNT", null, verify.raw());
            telegram.send("🚨 อ่านยอดซองไม่ได้ ไม่ได้กดรับเงิน\nบัญชี: %s\nraw: %s"
                    .formatted(account.getUsername(), shorten(verify.raw())));
            return new Outcome(false, "อ่านยอดในซองไม่ได้ ยังไม่ได้กดรับเงิน กรุณาแจ้งทีมงาน", 0);
        }

        // 3) redeem — from here the money is in the shop wallet
        TrueMoneyGiftClient.Result redeem = truemoney.redeem(hash);
        record(account, hash, "redeem", redeem.ok() ? "SUCCESS" : redeem.code(), redeem.amountBaht(), redeem.raw());
        if (!redeem.ok()) {
            String message = explain(redeem.code(), redeem.message());
            telegram.send("⚠️ เติมเงินไม่สำเร็จ (redeem)\nบัญชี: %s\nยอด: %d บาท\nเหตุผล: %s (%s)"
                    .formatted(account.getUsername(), baht, message, redeem.code()));
            return new Outcome(false, message, 0);
        }

        int amountBaht = redeem.amountBaht() != null ? redeem.amountBaht() : baht;
        int amountC = CashService.cashForBaht(amountBaht);
        boolean credited = cash.move(account.getId(), amountC, CashService.KIND_TOPUP, ref,
                "TrueMoney %d บาท".formatted(amountBaht));
        if (!credited) {
            // the voucher is already redeemed: this must never be silently lost
            telegram.send("🚨 รับเงินแล้วแต่เครดิตไม่สำเร็จ ต้องเติมมือ\nบัญชี: %s (id %d)\nยอด: %d บาท = %d C\nซอง: %s"
                    .formatted(account.getUsername(), account.getId(), amountBaht, amountC, hash));
            return new Outcome(false, "รับเงินแล้วแต่เครดิตไม่สำเร็จ ทีมงานได้รับแจ้งแล้ว จะเติมให้โดยเร็ว", 0);
        }

        telegram.send("💰 เติมเงินสำเร็จ\nบัญชี: %s (id %d)\nยอด: %d บาท → %d C\nคงเหลือ: %d C%s"
                .formatted(account.getUsername(), account.getId(), amountBaht, amountC,
                        cash.balance(account.getId()), truemoney.isMock() ? "\n(โหมดทดสอบ)" : ""));
        return new Outcome(true, "เติมสำเร็จ %d บาท ได้รับ %d C".formatted(amountBaht, amountC), amountC);
    }

    private int attemptsToday(Long accountId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM web_tmgift_check WHERE account_id = ? AND created >= CURDATE()",
                Integer.class, accountId);
        return count == null ? 0 : count;
    }

    private void record(Account account, String hash, String step, String outcome, Integer baht, String raw) {
        try {
            jdbc.update("INSERT INTO web_tmgift_check (account_id, voucher_hash, step, outcome, amount_baht, raw) VALUES (?, ?, ?, ?, ?, ?)",
                    account.getId(), hash, step, outcome, baht, shorten(raw));
        } catch (Exception e) {
            log.warn("could not record tmgift check: {}", e.getMessage());
        }
    }

    private static String shorten(String raw) {
        if (raw == null)
            return null;
        String oneLine = raw.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 2000 ? oneLine.substring(0, 2000) : oneLine;
    }

    /** Codes seen in the wild; anything else falls back to whatever TrueMoney said. */
    private static final Map<String, String> MESSAGES = Map.of(
            "VOUCHER_NOT_FOUND", "ไม่พบซองนี้ (ลิงก์ผิดหรือซองถูกยกเลิก)",
            "VOUCHER_OUT_OF_STOCK", "ซองนี้ถูกกดรับไปแล้ว",
            "VOUCHER_EXPIRED", "ซองหมดอายุแล้ว",
            "CANNOT_GET_OWN_VOUCHER", "ซองนี้เป็นของวอเลตร้านเอง กรุณาสร้างซองจากบัญชีของคุณ",
            "CONDITION_NOT_MET", "ซองนี้มีเงื่อนไขที่ระบบรับไม่ได้",
            "TRANSPORT", "ติดต่อ TrueMoney ไม่สำเร็จ กรุณาลองใหม่",
            "PARSE", "TrueMoney ตอบกลับมาในรูปแบบที่อ่านไม่ได้ กรุณาแจ้งทีมงาน");

    private static String explain(String code, String fallback) {
        String message = MESSAGES.get(code);
        if (message != null)
            return message;
        return (fallback == null || fallback.isBlank()) ? "เติมไม่สำเร็จ (%s)".formatted(code) : fallback;
    }
}
