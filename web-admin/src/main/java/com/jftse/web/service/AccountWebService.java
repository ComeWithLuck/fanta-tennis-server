package com.jftse.web.service;

import com.jftse.entities.database.model.account.Account;
import com.jftse.entities.database.repository.account.AccountRepository;
import com.jftse.entities.database.model.config.Config;
import com.jftse.entities.database.repository.config.ConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Registration / login / password changes.
 *
 * Password format follows the emulator's own switch: config row 'password.encryption.enabled'.
 * false (the default the SQL seed ships) means the auth-server compares plain text, so the site has
 * to store plain text too, otherwise accounts made here cannot log into the game.
 */
@Service
@RequiredArgsConstructor
public class AccountWebService {
    /** the login box in the client is narrow; keep names simple and game-safe */
    public static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{4,16}$");
    public static final int MIN_PASSWORD = 4;
    public static final int MAX_PASSWORD = 20;   // the client's login field limit

    private final AccountRepository accountRepository;
    private final ConfigRepository configRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean passwordEncryptionEnabled() {
        return configRepository.findConfigByName("password.encryption.enabled")
                .map(Config::getValue)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    private String encode(String raw) {
        return passwordEncryptionEnabled() ? passwordEncoder.encode(raw) : raw;
    }

    public boolean passwordMatches(Account account, String raw) {
        if (account.getPassword() == null)
            return false;
        return passwordEncryptionEnabled()
                ? passwordEncoder.matches(raw, account.getPassword())
                : account.getPassword().equals(raw);
    }

    public Optional<Account> findByUsername(String username) {
        return accountRepository.findAccountByUsername(username);
    }

    public Optional<Account> findById(Long id) {
        return accountRepository.findAccountById(id);
    }

    @Transactional
    public Account register(String username, String password, String email) {
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(encode(password));
        account.setEmail(email == null || email.isBlank() ? null : email.trim());
        account.setActive(true);
        account.setStatus(1);
        account.setAp(0);
        account.setGameMaster(false);
        account.setBanned(false);
        account.setEmailVerified(true);      // no mail flow yet; the auth-server only checks this
        account.setVerificationLevel(0);
        return accountRepository.save(account);
    }

    @Transactional
    public void changePassword(Account account, String newPassword) {
        account.setPassword(encode(newPassword));
        accountRepository.save(account);
    }

    /** Mirrors the in-game -ban command: blocked status + reason + an expiry date. */
    @Transactional
    public void ban(Account account, String reason, int days) {
        account.setStatus(3);                 // AuthenticationServiceImpl.ACCOUNT_BLOCKED_USER_ID
        account.setBanned(true);
        account.setBanReason(reason);
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_YEAR, days);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        account.setBannedUntil(cal.getTime());
        accountRepository.save(account);
    }

    @Transactional
    public void unban(Account account) {
        account.setStatus(1);
        account.setBanned(false);
        account.setBanReason(null);
        account.setBannedUntil(null);
        accountRepository.save(account);
    }

    @Transactional
    public void setGameMaster(Account account, boolean gm) {
        account.setGameMaster(gm);
        accountRepository.save(account);
    }

    @Transactional
    public void addAp(Account account, int amount) {
        account.setAp(Math.max(0, (account.getAp() == null ? 0 : account.getAp()) + amount));
        accountRepository.save(account);
    }
}
