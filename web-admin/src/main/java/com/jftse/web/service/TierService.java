package com.jftse.web.service;

import com.jftse.entities.database.model.account.Account;
import com.jftse.entities.database.repository.account.AccountRepository;
import com.jftse.web.model.AccountTier;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads and writes the five account tiers.
 *
 * NORMAL / SOFTBAN / BAN live in the emulator's own account columns so the game enforces them
 * (auth-server: status -6 blocks login, and a bannedUntil in the past clears itself on the next try).
 * STAFF is ours alone (table web_account_role); GM additionally sets account.gameMaster, which the
 * in-game GM commands read.
 */
@Service
@RequiredArgsConstructor
public class TierService {
    /** AuthenticationServiceImpl.ACCOUNT_BLOCKED_USER_ID */
    public static final int STATUS_BLOCKED = -6;
    public static final int STATUS_NORMAL = 0;

    private final AccountRepository accountRepository;
    private final JdbcTemplate jdbc;

    public AccountTier tierOf(Account account) {
        if (account == null)
            return AccountTier.NORMAL;
        if (isBlocked(account))
            return account.getBannedUntil() == null ? AccountTier.BAN : AccountTier.SOFTBAN;
        if (Boolean.TRUE.equals(account.getGameMaster()))
            return AccountTier.GM;
        return storedRole(account.getId()) == AccountTier.STAFF ? AccountTier.STAFF : AccountTier.NORMAL;
    }

    public Map<Long, AccountTier> tiersOf(List<Account> accounts) {
        return accounts.stream()
                .filter(a -> a != null && a.getId() != null)
                .collect(Collectors.toMap(Account::getId, this::tierOf, (a, b) -> a));
    }

    private boolean isBlocked(Account account) {
        return account.getStatus() != null && account.getStatus() == STATUS_BLOCKED;
    }

    private AccountTier storedRole(Long accountId) {
        List<String> roles = jdbc.queryForList(
                "SELECT role FROM web_account_role WHERE account_id = ?", String.class, accountId);
        if (roles.isEmpty())
            return AccountTier.NORMAL;
        try {
            return AccountTier.valueOf(roles.get(0));
        } catch (IllegalArgumentException e) {
            return AccountTier.NORMAL;
        }
    }

    /** Ban with an end date. The auth-server lifts it by itself once the date has passed. */
    @Transactional
    public void softBan(Account account, String reason, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_YEAR, Math.max(1, days));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        block(account, reason, cal.getTime());
    }

    /** Ban with no end date: nothing in the emulator clears it, only an unban here. */
    @Transactional
    public void permanentBan(Account account, String reason) {
        block(account, reason, null);
    }

    private void block(Account account, String reason, Date until) {
        account.setStatus(STATUS_BLOCKED);
        account.setBanned(true);
        account.setBanReason(reason);
        account.setBannedUntil(until);
        accountRepository.save(account);
    }

    @Transactional
    public void unban(Account account) {
        account.setStatus(STATUS_NORMAL);
        account.setBanned(false);
        account.setBanReason(null);
        account.setBannedUntil(null);
        accountRepository.save(account);
    }

    /** Only NORMAL / STAFF / GM can be set here; bans go through softBan/permanentBan. */
    @Transactional
    public void setRole(Account account, AccountTier tier, String grantedBy) {
        if (tier.isBanned())
            throw new IllegalArgumentException("ใช้ปุ่มแบนสำหรับ SOFTBAN/BAN");

        account.setGameMaster(tier == AccountTier.GM);
        accountRepository.save(account);

        if (tier == AccountTier.STAFF) {
            jdbc.update("""
                    INSERT INTO web_account_role (account_id, role, granted_by) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE role = VALUES(role), granted_by = VALUES(granted_by)
                    """, account.getId(), tier.name(), grantedBy);
        } else {
            jdbc.update("DELETE FROM web_account_role WHERE account_id = ?", account.getId());
        }
    }
}
