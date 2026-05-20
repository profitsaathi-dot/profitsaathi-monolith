package com.profitsaathi.seller.whatsapp.OpenWA;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitsaathi.seller.whatsapp.Entity.WhatsAppSession;
import com.profitsaathi.seller.whatsapp.Repo.WhatsAppSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class OpenWAClient {
        private final RestTemplate restTemplate = new RestTemplate();
        private final WhatsAppSessionRepository whatsAppSessionRepository;

        @Value("${profitsaathi.whatsapp.openWA.url}")
        private String baseUrl;

        @Value("${profitsaathi.whatsapp.OpenWA.api-key}")
        private String apiKey;

        public OpenWAClient(WhatsAppSessionRepository whatsAppSessionRepository) {
         this.whatsAppSessionRepository = whatsAppSessionRepository;
        }

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


            if (webhookUrl != null && !webhookUrl.isEmpty()) {
                // Put webhook properties DIRECTLY into the body map
                body.put("name", name);
                Map<String, Object> config = new HashMap<>();
                config.put("autoReconnect", true);
                config.put("webhookUrl", webhookUrl);
                body.put("config", config);
            }

            try {
                ResponseEntity<Map> resp = restTemplate.exchange(
                        baseUrl + "/api/sessions",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers()),
                        Map.class);
                return resp.getBody();
            } catch (HttpClientErrorException.Conflict already) {
                log.info("openWA: session '{}' already exists, fetching state", name);
                Optional<WhatsAppSession> wb = whatsAppSessionRepository.findBySessionName(name);

                if (wb.isPresent() && wb.get().getSessionId() != null) {
                    return getSession(wb.get().getSessionId());
                } else {
                    log.warn("OpenWA reported session '{}' exists, but it was not found in the local repository.", name);
                    throw new IllegalStateException("Inconsistent session state for: " + name);
                }

            } catch (HttpStatusCodeException e) {
                log.error("openWA createSession failed: status={}, body={}",
                        e.getStatusCode(), e.getResponseBodyAsString());
                throw new RuntimeException("openWA " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
            }
        }

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
        public Map<String, Object> getSession(String sessionId) {
            try {
                ///api/sessions/{sessionId}/webhooks
                ResponseEntity<Map> resp = restTemplate.exchange(
                        baseUrl + "/api/sessions/" + sessionId,
                        HttpMethod.GET,
                        new HttpEntity<>(headers()),
                        Map.class);
                return resp.getBody();
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND
                        || e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                    log.debug("openWA getSession({}) returned {} — no live session", sessionId, e.getStatusCode());
                    return null;
                }
                throw e;
            }


        }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getQrRaw(String sessionId) {

        HttpHeaders h = new HttpHeaders();
        h.setAccept(List.of(MediaType.APPLICATION_JSON));

        if (apiKey != null && !apiKey.isEmpty()) {
            h.set("X-Api-Key", apiKey);
        }

        try {

            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/api/sessions/" + sessionId + "/qr",
                    HttpMethod.GET,
                    new HttpEntity<>(h),
                    Map.class
            );

            Map<String, Object> response = resp.getBody();

            if (response == null || resp.getStatusCode() != HttpStatus.OK) {
                return null;
            }

            String status = String.valueOf(response.get("status"));

            // Only return QR when ready
            if (!"qr_ready".equalsIgnoreCase(status)) {
                return null;
            }

            Object qrCodeObj = response.get("qrCode");

            if (qrCodeObj == null) {
                return null;
            }

            String qrCode = qrCodeObj.toString();

            Map<String, Object> result = new HashMap<>();


            if (qrCode.startsWith("data:")) {
                String[] parts = qrCode.split(",", 2);

                String meta = parts[0];
                String base64 = parts[1];

                String mimeType = meta
                        .replace("data:", "")
                        .replace(";base64", "");

                result.put("value", base64);
                result.put("mimetype", mimeType);
            }

            //result.put("mimetype", "image/png");

            return result;

        } catch (HttpStatusCodeException e) {

            log.debug("OpenWA getQr({}) returned {}: {}",
                    sessionId,
                    e.getStatusCode(),
                    e.getResponseBodyAsString());

            return null;
        }
    }
        ///api/sessions/{sessionId}/messages/send-text
        public void sendText(String session, String chatId, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("chatId", chatId);
        body.put("text", text);
        //body.put("linkPreview", true);
        try {
            restTemplate.exchange(
                    baseUrl + "/api/sessions/"+session+"messages/send-text",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    Map.class);
        } catch (HttpStatusCodeException e) {
            log.error("WAHA sendText failed: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("WAHA " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        }
    }


        @SuppressWarnings("unchecked")
        public Map<String, Object> startSession(String sessionId) {
            try {
                ResponseEntity<Map> resp = restTemplate.exchange(
                        baseUrl + "/api/sessions/" + sessionId + "/start",
                        HttpMethod.POST,
                        new HttpEntity<>(headers()),
                        Map.class
                );

                return resp.getBody();

            } catch (HttpStatusCodeException e) {
                log.error("OpenWA startSession failed: status={}, body={}",
                        e.getStatusCode(),
                        e.getResponseBodyAsString());

                throw new RuntimeException(
                        "OpenWA " + e.getStatusCode() + ": " + e.getResponseBodyAsString(),
                        e
                );
            }
        }

        public void stopSession(String SessionId) {
        callSafe(HttpMethod.POST, "/api/sessions/" + SessionId + "/stop");
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

        public void deleteSession(String sessionId) {
        callSafe(HttpMethod.DELETE, "/api/sessions/" + sessionId);
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

