package com.jftse.web.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.Duration;

/**
 * Sends a line to a Telegram chat. Used for top-up notifications, since there is no admin approval step.
 *
 * Notifying must never block or fail a top-up: if Telegram is down the player still gets their C and the
 * failure only shows up in our log.
 */
@Service
@Log4j2
public class TelegramService {
    private final String token;
    private final String chatId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public TelegramService(@Value("${jftse.telegram.token:}") String token,
                           @Value("${jftse.telegram.chat-id:}") String chatId) {
        this.token = token == null ? "" : token.trim();
        this.chatId = chatId == null ? "" : chatId.trim();
    }

    public boolean isEnabled() {
        return !token.isBlank() && !chatId.isBlank();
    }

    @Async
    public void send(String text) {
        if (!isEnabled()) {
            log.info("[telegram disabled] {}", text);
            return;
        }
        try {
            String url = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s&parse_mode=HTML"
                    .formatted(token, URLEncoder.encode(chatId, StandardCharsets.UTF_8),
                            URLEncoder.encode(text, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                log.warn("telegram send failed: HTTP {} {}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.warn("telegram send failed: {}", e.getMessage());
        }
    }
}
