package com.jftse.web.web;

import com.jftse.entities.database.model.account.Account;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;

/** Session helpers: who is logged in, and a per-session CSRF token for every POST form. */
public final class Session {
    public static final String ACCOUNT_ID = "accountId";
    public static final String USERNAME = "username";
    public static final String IS_GM = "isGm";
    public static final String CSRF = "csrf";

    private static final SecureRandom RANDOM = new SecureRandom();

    private Session() {
    }

    public static void login(HttpServletRequest request, Account account) {
        HttpSession old = request.getSession(false);
        if (old != null)
            old.invalidate();                       // new session id on login
        HttpSession session = request.getSession(true);
        session.setAttribute(ACCOUNT_ID, account.getId());
        session.setAttribute(USERNAME, account.getUsername());
        session.setAttribute(IS_GM, Boolean.TRUE.equals(account.getGameMaster()));
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

    public static boolean isGm(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(IS_GM));
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
