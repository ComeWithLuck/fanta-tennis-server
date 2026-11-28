package com.jftse.web.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * In-memory sign-up limits per client IP (one web-admin process; restarting it resets them):
 * at most {@link #MAX_ATTEMPTS} requests per {@link #ATTEMPT_WINDOW_MS} and {@link #MAX_ACCOUNTS} new accounts per
 * {@link #ACCOUNT_WINDOW_MS}. No CAPTCHA service; the endpoint's honeypot field catches naive form bots.
 */
public class RegisterRateLimiter {
    public static final int MAX_ATTEMPTS = 10;
    public static final long ATTEMPT_WINDOW_MS = 10 * 60_000L;
    public static final int MAX_ACCOUNTS = 3;
    public static final long ACCOUNT_WINDOW_MS = 60 * 60_000L;
    /** The oldest-touched IP is dropped past this many tracked IPs. */
    static final int MAX_TRACKED = 10_000;

    private final LongSupplier clock;
    private final Map<String, Deque<Long>> attempts = new LinkedHashMap<>();
    private final Map<String, Deque<Long>> accounts = new LinkedHashMap<>();

    public RegisterRateLimiter() {
        this(System::currentTimeMillis);
    }

    public RegisterRateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Counts one attempt from ip. Returns 0 when it may go on, else the whole seconds until it may try again
     * (the refused attempt is not counted).
     */
    public synchronized long tryAttempt(String ip) {
        long now = clock.getAsLong();
        long wait = Math.max(waitFor(attempts, ip, now, MAX_ATTEMPTS, ATTEMPT_WINDOW_MS),
                waitFor(accounts, ip, now, MAX_ACCOUNTS, ACCOUNT_WINDOW_MS));
        if (wait > 0)
            return (wait + 999) / 1000;
        push(attempts, ip, now);
        return 0;
    }

    /** A new account was made from ip. */
    public synchronized void accountCreated(String ip) {
        push(accounts, ip, clock.getAsLong());
    }

    synchronized int tracked() {
        return attempts.size();
    }

    private static long waitFor(Map<String, Deque<Long>> map, String ip, long now, int max, long window) {
        Deque<Long> q = map.get(ip);
        if (q == null)
            return 0;
        while (!q.isEmpty() && q.peekFirst() <= now - window)
            q.pollFirst();
        if (q.isEmpty()) {
            map.remove(ip);
            return 0;
        }
        return q.size() >= max ? q.peekFirst() + window - now : 0;
    }

    private static void push(Map<String, Deque<Long>> map, String ip, long now) {
        Deque<Long> q = map.remove(ip);               // re-insert: LinkedHashMap keeps the most recent IP last
        if (q == null) {
            q = new ArrayDeque<>();
            if (map.size() >= MAX_TRACKED)
                map.remove(map.keySet().iterator().next());
        }
        q.addLast(now);
        map.put(ip, q);
    }
}
