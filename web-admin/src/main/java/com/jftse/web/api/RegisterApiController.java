package com.jftse.web.api;

import com.jftse.web.service.AccountWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sign-up for the game page: POST /api/register, JSON in and out. The game page reaches it same-origin through
 * web/server.mjs (/api/register -> 127.0.0.1:8080/api/register); no CORS headers are sent, so other sites cannot read it,
 * and only application/json is accepted, so a cross-site HTML form cannot post it either (that needs a preflight).
 *
 * Request: {"username", "password", "email"? , "website"?}. "website" is a honeypot the sign-up form keeps empty and
 * hidden; anything in it is refused.
 * Responses (always {"ok": bool, ...}):
 *   201 {ok:true, username}                       the account exists and can log in at once (email-verified, status 0)
 *   400 {ok:false, error:"username_invalid"|"password_invalid"|"email_invalid"|"bad_request"|"rejected", message}
 *   409 {ok:false, error:"username_taken", message}
 *   415 {ok:false, error:"unsupported_media_type", message}   not application/json
 *   429 {ok:false, error:"rate_limited", retryAfter, message} + Retry-After (RegisterRateLimiter, per client IP)
 *   500 {ok:false, error:"server_error", message}
 * The client IP is the socket address, or X-Forwarded-For's first value when the socket is loopback (the web/server.mjs proxy).
 */
@RestController
public class RegisterApiController {
    public static final String USERNAME_TAKEN = "username_taken";
    public static final String RATE_LIMITED = "rate_limited";
    public static final String REJECTED = "rejected";
    public static final String BAD_REQUEST = "bad_request";

    static final Map<String, String> MESSAGES = Map.of(
            RegisterRules.USERNAME_INVALID, "ชื่อผู้ใช้ต้องเป็น a-z A-Z 0-9 _ ยาว 4-16 ตัว",
            RegisterRules.PASSWORD_INVALID, "รหัสผ่านต้องยาว %d-%d ตัว ใช้ตัวอักษรอังกฤษ ตัวเลข หรือสัญลักษณ์ (ไม่มีช่องว่าง)"
                    .formatted(AccountWebService.MIN_PASSWORD, AccountWebService.MAX_PASSWORD),
            RegisterRules.EMAIL_INVALID, "อีเมลไม่ถูกต้อง (เว้นว่างได้)",
            USERNAME_TAKEN, "ชื่อผู้ใช้นี้ถูกใช้แล้ว",
            RATE_LIMITED, "สมัครบ่อยเกินไป กรุณารอสักครู่แล้วลองใหม่",
            REJECTED, "คำขอไม่ถูกต้อง",
            BAD_REQUEST, "คำขอไม่ถูกต้อง",
            "unsupported_media_type", "คำขอไม่ถูกต้อง",
            "server_error", "ระบบสมัครสมาชิกขัดข้อง กรุณาลองใหม่ภายหลัง");

    public record RegisterRequest(String username, String password, String email, String website) {
    }

    private final AccountWebService accounts;
    private final RegisterRateLimiter limiter;
    private final Object createLock = new Object();

    @Autowired
    public RegisterApiController(AccountWebService accounts) {
        this(accounts, new RegisterRateLimiter());
    }

    RegisterApiController(AccountWebService accounts, RegisterRateLimiter limiter) {
        this.accounts = accounts;
        this.limiter = limiter;
    }

    @PostMapping(path = "/api/register", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> register(HttpServletRequest request, @RequestBody RegisterRequest body) {
        String ip = clientIp(request);
        long wait = limiter.tryAttempt(ip);
        if (wait > 0) {
            Map<String, Object> out = error(RATE_LIMITED);
            out.put("retryAfter", wait);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, String.valueOf(wait)).body(out);
        }
        if (body == null)
            return ResponseEntity.badRequest().body(error(BAD_REQUEST));
        if (body.website() != null && !body.website().isEmpty())
            return ResponseEntity.badRequest().body(error(REJECTED));
        String invalid = RegisterRules.validate(body.username(), body.password(), body.email());
        if (invalid != null)
            return ResponseEntity.badRequest().body(error(invalid));
        try {
            synchronized (createLock) {                       // one web-admin process: no two creates of one name race
                if (accounts.findByUsername(body.username()).isPresent())
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(error(USERNAME_TAKEN));
                accounts.register(body.username(), body.password(), body.email());
            }
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error(USERNAME_TAKEN));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("server_error"));
        }
        limiter.accountCreated(ip);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("username", body.username());
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable() {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(error(BAD_REQUEST));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> mediaType() {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).contentType(MediaType.APPLICATION_JSON).body(error("unsupported_media_type"));
    }

    static Map<String, Object> error(String code) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", false);
        out.put("error", code);
        out.put("message", MESSAGES.getOrDefault(code, MESSAGES.get(BAD_REQUEST)));
        return out;
    }

    static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && isLoopback(remote)) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty())
                return first;
        }
        return remote;
    }

    private static boolean isLoopback(String address) {
        try {
            return address != null && InetAddress.getByName(address).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
