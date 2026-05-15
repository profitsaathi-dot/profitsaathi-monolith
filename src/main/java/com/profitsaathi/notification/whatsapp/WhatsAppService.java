package com.profitsaathi.notification.whatsapp;

import com.profitsaathi.notification.log.WhatsAppLog;
import com.profitsaathi.notification.log.WhatsAppLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.Map;

/**
 * In-process replacement for the Kafka {@code whatsapp-topic} consumer.
 *
 * <p>Single home for outbound WhatsApp via WAHA. Every notification (OTP,
 * order updates, …) flows through {@link #send(WhatsAppMessage)} so the
 * human-like sending behaviour (typing indicator + small pause + link
 * preview) and the audit log persistence both live in exactly one place.
 *
 * <p>Status semantics in {@code whatsapp_log}:
 * <ul>
 *   <li>SUCCESS — WAHA responded 2xx</li>
 *   <li>FAILED — one delivery attempt failed (retry pending)</li>
 *   <li>FAILED_PERMANENT — written by {@link #recover} after all retries exhausted</li>
 * </ul>
 */
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    private static final String DEFAULT_SESSION = "default";

    private final WebClient webClient;
    private final WhatsAppLogRepository whatsAppLogRepository;

    @Value("${profitsaathi.whatsapp.url}")
    private String whatsappUrl;

    @Value("${profitsaathi.whatsapp.api-key:}")
    private String wahaApiKey;

    public WhatsAppService(WebClient webClient, WhatsAppLogRepository repo) {
        this.webClient = webClient;
        this.whatsAppLogRepository = repo;
    }

    /** Async entry point — call from anywhere; the request thread returns immediately. */
    @Async("notificationExecutor")
    @Retryable(
            retryFor = { RuntimeException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 5000, multiplier = 2.0)
    )
    public void send(WhatsAppMessage event) {
        if (event.chatId() == null || event.chatId().isBlank()) {
            log.warn("Dropping WhatsApp event — blank chatId");
            return;
        }
        String body = resolveMessageBody(event);
        if (body == null || body.isBlank()) {
            log.warn("Dropping WhatsApp event for {} — no otp and no text", event.chatId());
            return;
        }

        String session = (event.session() == null || event.session().isBlank())
                ? DEFAULT_SESSION : event.session();

        setPresence(session, event.chatId(), "typing");
        try {
            sleepProportional(body);
            try {
                sendText(session, event.chatId(), body);
                saveLog(event.chatId(), body, "SUCCESS", null);
                log.info("WAHA send OK to {}", event.chatId());
            } catch (Exception e) {
                String reason = describeError(e);
                saveLog(event.chatId(), body, "FAILED", reason);
                log.warn("WAHA send failed to {} — {} (will retry)", event.chatId(), reason);
                throw e;
            }
        } finally {
            setPresence(session, event.chatId(), "paused");
        }
    }

    /** Synchronous overload for callers that need to await delivery (rare). */
    public void sendSync(String session, String chatId, String text) {
        send(WhatsAppMessage.builder().session(session).chatId(chatId).text(text).build());
    }

    @Recover
    public void recover(RuntimeException e, WhatsAppMessage event) {
        String body = resolveMessageBody(event);
        saveLog(event.chatId() == null ? "?" : event.chatId(),
                body == null ? "" : body,
                "FAILED_PERMANENT",
                e.getMessage());
        log.error("WhatsApp send permanently failed: chatId={}, err={}", event.chatId(), e.getMessage());
    }

    private String resolveMessageBody(WhatsAppMessage event) {
        if (event.otp() != null && !event.otp().isBlank()) {
            return String.format(
                    "Your ProfitSaathi verification code is *%s*. It is valid for %d minutes. Do not share this with anyone.",
                    event.otp(), event.expiry());
        }
        return event.text();
    }

    private void sendText(String session, String chatId, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("session", session);
        body.put("chatId", chatId);
        body.put("text", text);
        body.put("linkPreview", true);

        try {
            webClient.post()
                    .uri(whatsappUrl + "/api/sendText")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("X-Api-Key", wahaApiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException e) {
            // Wrap so @Retryable's retryFor matches and we get a clean message.
            throw new RuntimeException("WAHA HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        }
    }

    /** Best-effort. Presence (typing/paused) failures must never block real sends. */
    private void setPresence(String session, String chatId, String presence) {
        if (session == null || chatId == null || presence == null) return;
        Map<String, Object> body = new HashMap<>();
        body.put("chatId", chatId);
        body.put("presence", presence);
        try {
            webClient.post()
                    .uri(whatsappUrl + "/api/" + session + "/presence")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("X-Api-Key", wahaApiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.debug("WAHA setPresence({}, {}) failed: {}", presence, chatId, e.getMessage());
        }
    }

    /** ~30 ms/char, clamped to [1.5s, 4s] — matches a human typing pace. */
    private static void sleepProportional(String text) {
        long ms = 1200L + (text == null ? 0 : text.length()) * 30L;
        ms = Math.max(1500L, Math.min(4000L, ms));
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void saveLog(String chatId, String text, String status, String errorMessage) {
        try {
            WhatsAppLog row = new WhatsAppLog();
            row.setChatId(chatId);
            row.setText(text);
            row.setStatus(status);
            row.setErrorMessage(truncate(errorMessage, 4000));
            whatsAppLogRepository.save(row);
        } catch (Exception e) {
            log.warn("Failed to persist whatsapp_log row (status={}): {}", status, e.getMessage());
        }
    }

    private static String describeError(Throwable t) {
        if (t == null) return "unknown";
        return t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
