package com.jftse.web.api;

import com.jftse.web.service.AccountWebService;

import java.util.regex.Pattern;

/**
 * Sign-up rules for the game page's JSON endpoint (POST /api/register). Same as the /register form
 * (AccountWebService.USERNAME, MIN/MAX_PASSWORD), plus what the game's login can carry:
 * - username: [A-Za-z0-9_]{4,16}. CMSG_Login 0x0FA1 sends it in the client's default string encoding, so
 *   ASCII only, and the native Gui_Login ID box is short.
 * - password: 4..20 printable ASCII without spaces (0x21..0x7E). The native login box is ANSI-only and
 *   20 characters wide. The web client sends UTF-8, but a password must work in both clients.
 *   BCrypt (password.encryption.enabled) reads at most 72 bytes, far above 20.
 * - email: optional; when given, at most 100 characters and roughly name@host.tld. There is no mail flow:
 *   accounts are created email-verified (AccountWebService.register), so the auth-server's
 *   verification.enabled check (LoginPacketHandler) never blocks them.
 */
public final class RegisterRules {
    public static final Pattern PASSWORD = Pattern.compile("^[\\x21-\\x7E]{" + AccountWebService.MIN_PASSWORD + "," + AccountWebService.MAX_PASSWORD + "}$");
    public static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}$");
    public static final int MAX_EMAIL = 100;

    public static final String USERNAME_INVALID = "username_invalid";
    public static final String PASSWORD_INVALID = "password_invalid";
    public static final String EMAIL_INVALID = "email_invalid";

    private RegisterRules() {
    }

    /** null when the fields are acceptable, else one of the error codes above (checked in field order). */
    public static String validate(String username, String password, String email) {
        if (username == null || !AccountWebService.USERNAME.matcher(username).matches())
            return USERNAME_INVALID;
        if (password == null || !PASSWORD.matcher(password).matches())
            return PASSWORD_INVALID;
        if (email != null && !email.isBlank()) {
            String e = email.trim();
            if (e.length() > MAX_EMAIL || !EMAIL.matcher(e).matches())
                return EMAIL_INVALID;
        }
        return null;
    }
}
