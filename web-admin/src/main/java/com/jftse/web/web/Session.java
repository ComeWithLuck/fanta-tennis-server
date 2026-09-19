package com.jftse.web.web;

import com.jftse.entities.database.model.account.Account;
import com.jftse.web.model.AccountTier;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;

/** Session helpers: who is logged in, which tier they are, and a per-session CSRF token for POST forms. */
public final class Session {
    public static final String ACCOUNT_ID = "accountId";
    public static final String USERNAME = "username";
    public static final String TIER = "tier";
    public static final String CSRF = "csrf";

    private static final SecureRandom RANDOM = new SecureRandom();

    private Session() {
    }

    public static void login(HttpServletRequest request, Account account, AccountTier tier) {
        HttpSession old = request.getSession(false);
        if (old != null)
            old.invalidate();                       // new session id on login
        HttpSession session = request.getSession(true);
        session.setAttribute(ACCOUNT_ID, account.getId());
        session.setAttribute(USERNAME, account.getUsername());
        session.setAttribute(TIER, tier);
        session.setAttribute(CSRF, newToken());
        session.setMaxInactiveInterval(60 * 60 * 8);
    }

    public static void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null)
            session.invalidate();
    }

    public static Long accountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Long) session.getAttribute(ACCOUNT_ID);
    }

    public static String username(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(USERNAME);
    }

    /** Never null: a visitor with no session counts as NORMAL, which may do nothing in /admin. */
    public static AccountTier tier(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object tier = session == null ? null : session.getAttribute(TIER);
        return tier instanceof AccountTier t ? t : AccountTier.NORMAL;
    }

    public static void refreshTier(HttpServletRequest request, AccountTier tier) {
        HttpSession session = request.getSession(false);
        if (session != null)
            session.setAttribute(TIER, tier);
    }

    public static String csrf(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String token = (String) session.getAttribute(CSRF);
        if (token == null) {
            token = newToken();
            session.setAttribute(CSRF, token);
        }
        return token;
    }

    /** Every POST handler calls this first; a mismatch means the request did not come from our form. */
    public static void requireCsrf(HttpServletRequest request, String submitted) {
        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(CSRF);
        if (expected == null || submitted == null || !expected.equals(submitted))
            throw new IllegalStateException("invalid csrf token");
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
