package com.jftse.web.api;

import com.jftse.entities.database.model.account.Account;
import com.jftse.web.service.AccountWebService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegisterApiTest {
    private AccountWebService accounts;
    private AtomicLong now;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountWebService.class);
        when(accounts.findByUsername(anyString())).thenReturn(Optional.empty());
        when(accounts.register(anyString(), anyString(), any())).thenAnswer(i -> new Account());
        now = new AtomicLong(1_000_000L);
        mvc = MockMvcBuilders.standaloneSetup(new RegisterApiController(accounts, new RegisterRateLimiter(now::get))).build();
    }

    private static String json(String username, String password, String email, String website) {
        StringBuilder b = new StringBuilder("{");
        b.append("\"username\":").append(username == null ? "null" : "\"" + username + "\"");
        b.append(",\"password\":").append(password == null ? "null" : "\"" + password + "\"");
        if (email != null) b.append(",\"email\":\"").append(email).append('"');
        if (website != null) b.append(",\"website\":\"").append(website).append('"');
        return b.append('}').toString();
    }

    @Test
    void rules_followTheFormAndTheLoginPacket() {
        assertNull(RegisterRules.validate("qa_user1", "pass!word", null));
        assertNull(RegisterRules.validate("abcd", "1234", ""));
        assertNull(RegisterRules.validate("A_b9A_b9A_b9A_b9", "x".repeat(20), "a.b@ex.co"));
        assertEquals(RegisterRules.USERNAME_INVALID, RegisterRules.validate("abc", "1234", null), "too short");
        assertEquals(RegisterRules.USERNAME_INVALID, RegisterRules.validate("a".repeat(17), "1234", null), "too long");
        assertEquals(RegisterRules.USERNAME_INVALID, RegisterRules.validate("ผู้เล่น1", "1234", null), "ASCII only");
        assertEquals(RegisterRules.USERNAME_INVALID, RegisterRules.validate("bad name", "1234", null));
        assertEquals(RegisterRules.USERNAME_INVALID, RegisterRules.validate(null, "1234", null));
        assertEquals(RegisterRules.PASSWORD_INVALID, RegisterRules.validate("good_name", "123", null));
        assertEquals(RegisterRules.PASSWORD_INVALID, RegisterRules.validate("good_name", "x".repeat(21), null), "native login box: 20");
        assertEquals(RegisterRules.PASSWORD_INVALID, RegisterRules.validate("good_name", "with space", null));
        assertEquals(RegisterRules.PASSWORD_INVALID, RegisterRules.validate("good_name", "รหัสผ่าน", null), "printable ASCII only");
        assertEquals(RegisterRules.EMAIL_INVALID, RegisterRules.validate("good_name", "1234", "nope"));
        assertEquals(RegisterRules.EMAIL_INVALID, RegisterRules.validate("good_name", "1234", "a@b"));
        assertEquals(RegisterRules.EMAIL_INVALID, RegisterRules.validate("good_name", "1234", "a".repeat(95) + "@ex.co"));
    }

    @Test
    void created_thenTaken() throws Exception {
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(json("newbie_1", "secret12", "n@ex.co", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.username").value("newbie_1"));
        verify(accounts).register("newbie_1", "secret12", "n@ex.co");

        when(accounts.findByUsername("newbie_1")).thenReturn(Optional.of(new Account()));
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(json("newbie_1", "secret12", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value("username_taken"))
                .andExpect(jsonPath("$.message").value("ชื่อผู้ใช้นี้ถูกใช้แล้ว"));
        verify(accounts, times(1)).register(anyString(), anyString(), any());
    }

    @Test
    void aDuplicateFromTheDatabaseIsTakenToo() throws Exception {
        when(accounts.register(anyString(), anyString(), any())).thenThrow(new DataIntegrityViolationException("dup"));
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(json("racer_01", "secret12", null, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("username_taken"));
    }

    @Test
    void invalidFields_honeypot_badJson_formPosts() throws Exception {
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(json("ab", "secret12", null, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("username_invalid"));
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(json("good_name", "a b c d", null, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("password_invalid"));
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(json("good_name", "secret12", "x@y", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("email_invalid"));
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(json("good_name", "secret12", null, "http://spam")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("rejected"));
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("bad_request"));
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_FORM_URLENCODED).content("username=good_name&password=secret12"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.error").value("unsupported_media_type"));
        verify(accounts, never()).register(anyString(), anyString(), any());
    }

    @Test
    void rateLimited_perIp_thenFreeAgain() throws Exception {
        for (int i = 0; i < RegisterRateLimiter.MAX_ATTEMPTS; i++)
            mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(json("x", "secret12", null, null)))
                    .andExpect(status().isBadRequest());
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(json("good_name", "secret12", null, null)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("rate_limited"))
                .andExpect(jsonPath("$.retryAfter").value(600))
                .andExpect(header().string("Retry-After", "600"));
        // Another client (forwarded by the loopback proxy) is not limited by the first one.
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "203.0.113.7")
                        .content(json("good_name", "secret12", null, null)))
                .andExpect(status().isCreated());
        now.addAndGet(RegisterRateLimiter.ATTEMPT_WINDOW_MS);
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(json("other_name", "secret12", null, null)))
                .andExpect(status().isCreated());
    }

    @Test
    void limiter_accountsPerHour() {
        AtomicLong t = new AtomicLong(0);
        RegisterRateLimiter limiter = new RegisterRateLimiter(t::get);
        for (int i = 0; i < RegisterRateLimiter.MAX_ACCOUNTS; i++) {
            assertEquals(0, limiter.tryAttempt("1.2.3.4"));
            limiter.accountCreated("1.2.3.4");
        }
        assertEquals(3600, limiter.tryAttempt("1.2.3.4"), "three accounts: wait for the hour");
        assertEquals(0, limiter.tryAttempt("5.6.7.8"));
        t.set(RegisterRateLimiter.ACCOUNT_WINDOW_MS);
        assertEquals(0, limiter.tryAttempt("1.2.3.4"));
    }

    @Test
    void clientIp_trustsForwardedOnlyFromLoopback() {
        MockHttpServletRequest local = new MockHttpServletRequest();
        local.setRemoteAddr("127.0.0.1");
        local.addHeader("X-Forwarded-For", "198.51.100.4, 10.0.0.1");
        assertEquals("198.51.100.4", RegisterApiController.clientIp(local));
        MockHttpServletRequest remote = new MockHttpServletRequest();
        remote.setRemoteAddr("192.0.2.9");
        remote.addHeader("X-Forwarded-For", "198.51.100.4");
        assertEquals("192.0.2.9", RegisterApiController.clientIp(remote), "a direct client cannot pick its own IP");
    }
}
