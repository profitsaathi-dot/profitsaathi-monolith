package com.profitsaathi.seller.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin RestTemplate wrapper around WAHA's HTTP API (session lifecycle + send).
 * Used by the seller's WhatsApp connect flow. The notification module also
 * talks to WAHA but uses WebClient — they're intentionally independent so
 * notification sends never block on a seller-side restart.
 */
@Slf4j
@Component
public class WahaClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${profitsaathi.whatsapp.url:http://localhost:3004}")
    private String baseUrl;

    @Value("${profitsaathi.whatsapp.api-key:}")
    private String apiKey;

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (apiKey != null && !apiKey.isEmpty()) h.set("X-Api-Key", apiKey);
        return h;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createSession(String name, String webhookUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("start", true);

        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            Map<String, Object> webhook = new HashMap<>();
            webhook.put("url", webhookUrl);
            webhook.put("events", List.of("session.status", "message"));
            Map<String, Object> config = new HashMap<>();
            config.put("webhooks", List.of(webhook));
            body.put("config", config);
        }

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/api/sessions",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    Map.class);
            return resp.getBody();
        } catch (HttpClientErrorException.UnprocessableEntity already) {
            log.info("WAHA: session '{}' already exists, fetching state", name);
            return getSession(name);
        } catch (HttpStatusCodeException e) {
            log.error("WAHA createSession failed: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("WAHA " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Lists every session the WAHA server knows about. Doubles as a health
     * probe — a successful return means the server is reachable. Returns an
     * empty list (not null) when the server reports no sessions.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listSessions() {
        try {
            ResponseEntity<List> resp = restTemplate.exchange(
                    baseUrl + "/api/sessions",
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    List.class);
            List<Map<String, Object>> body = resp.getBody();
            return body == null ? List.of() : body;
        } catch (HttpStatusCodeException e) {
            log.warn("WAHA listSessions failed: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("WAHA " + e.getStatusCode(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSession(String name) {
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/api/sessions/" + name,
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    Map.class);
            return resp.getBody();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND
                    || e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                log.debug("WAHA getSession({}) returned {} — no live session", name, e.getStatusCode());
                return null;
            }
            throw e;
        }
    }

    public Map<String, Object> getQrRaw(String name) {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(List.of(MediaType.IMAGE_PNG, MediaType.ALL));
        if (apiKey != null && !apiKey.isEmpty()) h.set("X-Api-Key", apiKey);
        try {
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    baseUrl + "/api/" + name + "/auth/qr",
                    HttpMethod.GET,
                    new HttpEntity<>(h),
                    byte[].class);
            byte[] bytes = resp.getBody();
            if (bytes == null || bytes.length == 0) return null;
            Map<String, Object> result = new HashMap<>();
            result.put("value", Base64.getEncoder().encodeToString(bytes));
            result.put("mimetype", "image/png");
            return result;
        } catch (HttpStatusCodeException e) {
            log.debug("WAHA getQr({}) returned {}: {}",
                    name, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        }
    }

    public void setPresence(String session, String chatId, String presence) {
        if (session == null || presence == null) return;
        Map<String, Object> body = new HashMap<>();
        if (chatId != null) body.put("chatId", chatId);
        body.put("presence", presence);
        try {
            restTemplate.exchange(
                    baseUrl + "/api/" + session + "/presence",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    Void.class);
        } catch (Exception e) {
            log.debug("WAHA setPresence({}, {}) failed: {}", presence, chatId, e.getMessage());
        }
    }

    public void sendText(String session, String chatId, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("session", session);
        body.put("chatId", chatId);
        body.put("text", text);
        body.put("linkPreview", true);
        try {
            restTemplate.exchange(
                    baseUrl + "/api/sendText",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    Map.class);
        } catch (HttpStatusCodeException e) {
            log.error("WAHA sendText failed: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("WAHA " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        }
    }

    public void startSession(String name) {
        try {
            restTemplate.exchange(
                    baseUrl + "/api/sessions/" + name + "/start",
                    HttpMethod.POST,
                    new HttpEntity<>(headers()),
                    Void.class);
        } catch (HttpStatusCodeException e) {
            log.error("WAHA startSession failed: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("WAHA " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        }
    }

    public void stopSession(String name) {
        callSafe(HttpMethod.POST, "/api/sessions/" + name + "/stop");
    }

    public void restartSession(String name) {
        try {
            restTemplate.exchange(
                    baseUrl + "/api/sessions/" + name + "/restart",
                    HttpMethod.POST,
                    new HttpEntity<>(headers()),
                    Void.class);
        } catch (HttpStatusCodeException e) {
            log.error("WAHA restartSession failed: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("WAHA " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        }
    }

    public void logoutSession(String name) {
        callSafe(HttpMethod.POST, "/api/sessions/" + name + "/logout");
    }

    public void deleteSession(String name) {
        callSafe(HttpMethod.DELETE, "/api/sessions/" + name);
    }

    private void callSafe(HttpMethod method, String path) {
        try {
            restTemplate.exchange(
                    baseUrl + path,
                    method,
                    new HttpEntity<>(headers()),
                    Void.class);
        } catch (HttpStatusCodeException e) {
            // intentionally swallow — disconnect should always succeed
        }
    }
}
