package com.profitsaathi.seller.whatsapp;

import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import com.profitsaathi.seller.whatsapp.Entity.SellerWhatsAppMessage;
import com.profitsaathi.seller.whatsapp.Entity.WhatsAppSession;
import com.profitsaathi.seller.whatsapp.Repo.SellerWhatsAppMessageRepository;
import com.profitsaathi.seller.whatsapp.Repo.WhatsAppSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seller-facing WAHA session lifecycle (connect / status / disconnect /
 * send). One session per seller, named by {@link #desiredSessionName}.
 *
 * The session row in Postgres mirrors the WAHA session — it's the source of
 * truth for the UI's "connected" badge so we don't have to round-trip WAHA
 * on every page render.
 */
@Slf4j
@Service
public class WhatsAppSessionService {

    private final WhatsAppSessionRepository sessionRepository;
    private final SellerWhatsAppMessageRepository messageRepository;
    private final SellerRepository sellerRepository;
    private final WahaClient waha;

    private final String webhookUrl;
    private final String sessionNameMode;

    public WhatsAppSessionService(
            WhatsAppSessionRepository sessionRepository,
            SellerWhatsAppMessageRepository messageRepository,
            SellerRepository sellerRepository,
            WahaClient waha,
            @Value("${waha.webhook-public-url:http://host.docker.internal:9090/api/v1/whatsapp/webhook}")
            String webhookUrl,
            @Value("${waha.session-name-mode:default}")
            String sessionNameMode) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.sellerRepository = sellerRepository;
        this.waha = waha;
        this.webhookUrl = webhookUrl;
        this.sessionNameMode = sessionNameMode;
    }

    @Transactional
    public Map<String, Object> connect(Long sellerId) {

        WhatsAppSession session = ensureLocalSession(sellerId);

        Map<String, Object> wahaSession =
                waha.getSession(session.getSessionName());

        /*
         * Create only if missing
         */
        if (wahaSession == null) {

            wahaSession = waha.createSession(
                    session.getSessionName(),
                    webhookUrl
            );
        }

        String status =
                wahaSession != null
                        ? asString(wahaSession.get("status"))
                        : null;

        /*
         * Restart dead sessions
         */
        if (shouldStart(status)) {

            waha.startSession(session.getSessionName());

            wahaSession =
                    waha.getSession(session.getSessionName());
        }

        if (wahaSession != null) {
            applyWahaState(session, wahaSession);
        }

        return composeStatusPayload(session);
    }

    private boolean shouldStart(String status) {

        if (status == null) {
            return true;
        }

        return switch (status.toUpperCase()) {

            case "STOPPED",
                 "FAILED",
                 "DISCONNECTED",
                 "CREATED" -> true;

            default -> false;
        };
    }

    @Transactional
    public Map<String, Object> status(Long sellerId) {
        WhatsAppSession session = sessionRepository.findBySeller_Id(sellerId).orElse(null);
        if (session == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("status", "DISCONNECTED");
            empty.put("connected", false);
            return empty;
        }

        session = renameIfStale(session);
        Map<String, Object> wahaSession = waha.getSession(session.getSessionName());
        if (wahaSession != null) applyWahaState(session, wahaSession);

        return composeStatusPayload(session);
    }

    @Transactional
    public Map<String, Object> restart(Long sellerId) {
        WhatsAppSession session = sessionRepository.findBySeller_Id(sellerId)
                .orElseThrow(() -> new IllegalStateException("No WhatsApp session — click Connect first"));

        waha.restartSession(session.getSessionName());

        session.setStatus("STARTING");
        session.setConnected(false);
        sessionRepository.save(session);

        Map<String, Object> wahaSession = waha.getSession(session.getSessionName());
        if (wahaSession != null) applyWahaState(session, wahaSession);

        return composeStatusPayload(session);
    }

    public void disconnect(Long sellerId) {
        WhatsAppSession session = sessionRepository.findBySeller_Id(sellerId).orElse(null);
        if (session == null) return;

        waha.logoutSession(session.getSessionName());
        waha.stopSession(session.getSessionName());
        waha.deleteSession(session.getSessionName());

        session.setStatus("DISCONNECTED");
        session.setConnected(false);
        session.setPhoneNumber(null);
        session.setPushName(null);
        sessionRepository.save(session);
    }

    @Transactional
    public void sendText(Long sellerId, String rawTo, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Message text is required");
        }
        WhatsAppSession session = sessionRepository.findBySeller_Id(sellerId)
                .orElseThrow(() -> new IllegalStateException("WhatsApp not connected"));
        if (!Boolean.TRUE.equals(session.getConnected())) {
            throw new IllegalStateException("WhatsApp is not connected — scan the QR first");
        }

        String chatId = toChatId(rawTo);
        humanLikeSend(session.getSessionName(), chatId, text);

        SellerWhatsAppMessage row = new SellerWhatsAppMessage();
        row.setSessionName(session.getSessionName());
        row.setChatId(chatId);
        row.setMessage(text);
        row.setDirection("OUTBOUND");
        messageRepository.save(row);
    }

    @Transactional
    public void handleWebhook(Map<String, Object> payload) {
        if (payload == null) return;

        Object event = payload.get("event");
        Object sessionName = payload.get("session");
        if (sessionName == null) return;

        WhatsAppSession session = sessionRepository.findBySessionNameAndStatusNotIn(sessionName.toString(), List.of("DISCONNECTED","STOPPED")).orElse(null);
        if (session == null) return;

        if ("session.status".equals(event)) {
            Object data = payload.get("payload");
            if (data instanceof Map<?, ?> map) {
                Object status = map.get("status");
                if (status != null) {
                    session.setStatus(status.toString());
                    session.setConnected(isConnected(status.toString()));
                    sessionRepository.save(session);
                }
            }
            return;
        }

        if ("message".equals(event)) {
            Object data = payload.get("payload");
            if (data instanceof Map<?, ?> map) {
                SellerWhatsAppMessage row = new SellerWhatsAppMessage();
                row.setSessionName(session.getSessionName());
                row.setChatId(asString(map.get("from")));
                row.setMessage(asString(map.get("body")));
                row.setDirection("INBOUND");
                messageRepository.save(row);
            }
        }
    }

    private WhatsAppSession ensureLocalSession(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not registered"));

        return sessionRepository.findBySeller_Id(sellerId)
                .map(existing -> renameIfStale(existing))
                .orElseGet(() -> {
                    WhatsAppSession s = new WhatsAppSession();
                    s.setSeller(seller);
                    s.setSessionName(desiredSessionName(seller));
                    s.setStatus("STARTING");
                    s.setConnected(false);
                    return sessionRepository.save(s);
                });
    }

    private WhatsAppSession renameIfStale(WhatsAppSession existing) {
        Seller seller = existing.getSeller();
        String desiredName = desiredSessionName(seller);
        if (desiredName.equals(existing.getSessionName())) return existing;
        existing.setSessionName(desiredName);
        existing.setStatus("STARTING");
        existing.setConnected(false);
        existing.setPhoneNumber(null);
        existing.setPushName(null);
        return sessionRepository.save(existing);
    }

    private boolean isConnected(String status) {

        if (status == null) {
            return false;
        }

        return switch (status.toUpperCase()) {

            case "WORKING",
                 "CONNECTED",
                 "READY" -> true;

            default -> false;
        };
    }

    private String desiredSessionName(Seller seller) {
        if ("per-user".equalsIgnoreCase(sessionNameMode) && seller != null) {
            return "seller_" + seller.getId();
        }
        return "default";
    }

    private void applyWahaState(
            WhatsAppSession session,
            Map<String, Object> wahaSession
    ) {

        Object statusObj = wahaSession.get("status");

        if (statusObj != null) {

            String status = statusObj.toString();

            session.setStatus(status);
            session.setConnected(isConnected(status));
        }

        Object me = wahaSession.get("me");

        if (me instanceof Map<?, ?> meMap) {

            String id = asString(meMap.get("id"));

            if (id != null && id.contains("@")) {
                id = id.substring(0, id.indexOf('@'));
            }

            session.setPhoneNumber(id);

            session.setPushName(
                    asString(meMap.get("pushName"))
            );
        }

        sessionRepository.save(session);
    }
    private Map<String, Object> composeStatusPayload(WhatsAppSession session) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionName", session.getSessionName());
        out.put("status", session.getStatus());
        out.put("connected", session.getConnected());
        out.put("phoneNumber", session.getPhoneNumber());
        out.put("pushName", session.getPushName());

        if ("SCAN_QR_CODE".equalsIgnoreCase(session.getStatus())) {
            Map<String, Object> qr = waha.getQrRaw(session.getSessionName());
            if (qr != null) {
                Object value = qr.get("value");
                Object mimetype = qr.get("mimetype");
                if (value != null) {
                    out.put("qrBase64", value);
                    out.put("qrMimetype", mimetype != null ? mimetype : "image/png");
                }
            }
        }
        return out;
    }

    private void humanLikeSend(String sessionName, String chatId, String text) {
        waha.setPresence(sessionName, chatId, "typing");
        try {
            Thread.sleep(typingDelayMillis(text));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        try {
            waha.sendText(sessionName, chatId, text);
        } finally {
            waha.setPresence(sessionName, chatId, "paused");
        }
    }

    private static long typingDelayMillis(String text) {
        long ms = 1200L + (text == null ? 0 : text.length()) * 30L;
        return Math.max(1500L, Math.min(4000L, ms));
    }

    private static String toChatId(String raw) {
        if (raw == null) throw new IllegalArgumentException("Recipient is required");
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 10) {
            throw new IllegalArgumentException("Phone must be at least 10 digits");
        }
        return digits + "@c.us";
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
