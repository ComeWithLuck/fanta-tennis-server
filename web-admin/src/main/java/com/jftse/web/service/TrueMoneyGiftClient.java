package com.jftse.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to the TrueMoney gift-voucher endpoints (unofficial, the same ones every private server uses):
 *   GET  https://gift.truemoney.com/campaign/vouchers/{hash}/verify?mobile=...
 *   POST https://gift.truemoney.com/campaign/vouchers/{hash}/redeem   {"mobile": "..."}
 *
 * Transport is curl.exe on purpose. gift.truemoney.com sits behind Cloudflare bot protection which
 * answers a normal Java/.NET HTTP client with an HTML 403 page, while curl with the same URL and headers
 * gets real JSON (a lesson already paid for on another project). Never "simplify" this to HttpClient.
 *
 * Every response is returned raw so the caller can store it: the API can change shape at any time and
 * we would rather reject a top-up than guess an amount.
 */
@Service
@Log4j2
public class TrueMoneyGiftClient {
    /** gift.truemoney.com/campaign/?v=<hash> — the hash is the voucher */
    private static final Pattern HASH = Pattern.compile("[?&]v=([A-Za-z0-9]+)");
    private static final String BASE = "https://gift.truemoney.com/campaign/vouchers/";
    /** field names seen in the wild for the voucher value; first hit wins */
    private static final List<String> AMOUNT_FIELDS = List.of("amount_baht", "amount", "value");
    private static final List<String> MEMBER_FIELDS = List.of("member", "member_count", "members");

    private final ObjectMapper json = new ObjectMapper();

    @Getter
    private final boolean mock;
    private final String mobile;

    public TrueMoneyGiftClient(@Value("${jftse.tmgift.mobile:}") String mobile,
                               @Value("${jftse.tmgift.provider:mock}") String provider) {
        this.mobile = mobile == null ? "" : mobile.trim();
        this.mock = !"real".equalsIgnoreCase(provider);
        if (mock)
            log.warn("TrueMoney gift provider = MOCK (set jftse.tmgift.provider=real + jftse.tmgift.mobile for production)");
    }

    /** Off when no wallet number is configured: the top-up page hides itself instead of failing later. */
    public boolean isEnabled() {
        return mock || !mobile.isBlank();
    }

    public static String extractHash(String link) {
        if (link == null)
            return null;
        String trimmed = link.trim();
        Matcher m = HASH.matcher(trimmed);
        if (m.find())
            return m.group(1);
        // someone may paste just the hash
        return trimmed.matches("[A-Za-z0-9]{20,}") ? trimmed : null;
    }

    public record Result(boolean ok, String code, String message, Integer amountBaht, Integer members, String raw) {
        public static Result error(String code, String message, String raw) {
            return new Result(false, code, message, null, null, raw);
        }
    }

    /** Read-only check. Nothing moves until redeem, so every rejection here leaves the voucher untouched. */
    public Result verify(String hash) {
        if (mock)
            return mockResult(hash, "verify");
        return parse(curl(List.of("-sS", "-X", "GET", BASE + hash + "/verify?mobile=" + mobile,
                "-H", "User-Agent: Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
                "-H", "Accept: application/json",
                "-H", "Referer: https://gift.truemoney.com/campaign/?v=" + hash), null));
    }

    /** Actually takes the money into the shop wallet. Only call after a successful verify. */
    public Result redeem(String hash) {
        if (mock)
            return mockResult(hash, "redeem");
        String body = "{\"mobile\":\"%s\",\"voucher_hash\":\"%s\"}".formatted(mobile, hash);
        return parse(curl(List.of("-sS", "-X", "POST", BASE + hash + "/redeem",
                "-H", "User-Agent: Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
                "-H", "Content-Type: application/json",
                "-H", "Accept: application/json",
                "-H", "Referer: https://gift.truemoney.com/campaign/?v=" + hash,
                "-d", "@-"), body));
    }

    private String curl(List<String> args, String stdin) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            List<String> command = new java.util.ArrayList<>();
            command.add("curl");
            command.addAll(args);
            pb.command(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            if (stdin != null) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                process.getOutputStream().close();
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            return out;
        } catch (Exception e) {
            log.error("curl to TrueMoney failed: {}", e.getMessage());
            return "";
        }
    }

    private Result parse(String raw) {
        if (raw == null || raw.isBlank())
            return Result.error("TRANSPORT", "ติดต่อ TrueMoney ไม่สำเร็จ", raw);
        try {
            JsonNode root = json.readTree(raw);
            JsonNode status = root.path("status");
            String code = status.path("code").asText("");
            String message = status.path("message").asText("");
            boolean ok = "SUCCESS".equalsIgnoreCase(code);
            JsonNode voucher = root.path("data").path("voucher");
            Integer amount = firstInt(voucher, AMOUNT_FIELDS);
            Integer members = firstInt(voucher, MEMBER_FIELDS);
            return new Result(ok, code.isBlank() ? "UNKNOWN" : code, message, amount, members, raw);
        } catch (Exception e) {
            // HTML from Cloudflare, or a shape we do not know: never guess, let the caller reject
            return Result.error("PARSE", "อ่านคำตอบจาก TrueMoney ไม่ได้", raw);
        }
    }

    private Integer firstInt(JsonNode node, List<String> fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber())
                return (int) Math.round(value.asDouble());
            if (value.isTextual() && value.asText().matches("\\d+(\\.\\d+)?"))
                return (int) Math.round(Double.parseDouble(value.asText()));
        }
        return null;
    }

    /**
     * Dev provider. A link whose hash starts with MOCK carries the amount, e.g. ?v=MOCK50abcdef -> 50 THB.
     * MOCKM2... pretends the voucher is shared by 2 people, to exercise that rejection.
     */
    private Result mockResult(String hash, String step) {
        String upper = hash.toUpperCase();
        if (!upper.startsWith("MOCK"))
            return new Result(false, "VOUCHER_NOT_FOUND", "ซองนี้ไม่มีอยู่จริง", null, null,
                    "{\"mock\":true,\"step\":\"%s\"}".formatted(step));
        int members = 1;
        String rest = upper.substring(4);
        if (rest.startsWith("M2")) {
            members = 2;
            rest = rest.substring(2);
        }
        Matcher digits = Pattern.compile("^(\\d+)").matcher(rest);
        int amount = digits.find() ? Integer.parseInt(digits.group(1)) : 0;
        String raw = "{\"mock\":true,\"step\":\"%s\",\"status\":{\"code\":\"SUCCESS\"},\"data\":{\"voucher\":{\"amount_baht\":%d,\"member\":%d}}}"
                .formatted(step, amount, members);
        return new Result(true, "SUCCESS", "mock", amount, members, raw);
    }
}
