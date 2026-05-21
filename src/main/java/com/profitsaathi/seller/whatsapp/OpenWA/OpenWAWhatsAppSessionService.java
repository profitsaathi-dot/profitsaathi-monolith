package com.profitsaathi.seller.whatsapp.OpenWA;

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
import java.util.Optional;

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
public class OpenWAWhatsAppSessionService {

    private final WhatsAppSessionRepository sessionRepository;
    private final SellerWhatsAppMessageRepository messageRepository;
    private final SellerRepository sellerRepository;
    private final OpenWAClient openWAClient;

    private final String webhookUrl;
    private final String sessionNameMode;

    public OpenWAWhatsAppSessionService(
            WhatsAppSessionRepository sessionRepository,
            SellerWhatsAppMessageRepository messageRepository,
            SellerRepository sellerRepository,
            OpenWAClient openWAClient,
            @Value("${open.wa.webhook-public-url}")
            String webhookUrl,
            @Value("${open.wa.session-name-mode:default}")
            String sessionNameMode) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.sellerRepository = sellerRepository;
        this.openWAClient = openWAClient;
        this.webhookUrl = webhookUrl;
        this.sessionNameMode = sessionNameMode;
    }

    @Transactional
    public Map<String, Object> connect(Long sellerId) {

        WhatsAppSession session = ensureLocalSession(sellerId);

        String status = asString(session.getStatus());

        try {

            /*
             * Create session first time
             */
            if (session.getSessionId() == null || session.getSessionId().isBlank()) {

                Map<String, Object> created = openWAClient.createSession(
                        session.getSessionName(),
                        webhookUrl,
                        session.getWhatAppToken()
                );

                if (created != null) {
                    applyWahaState(session, created);
                }
            }

            /*
             * Start session when stopped/disconnected
             */
            else if (shouldStart(status)) {

                openWAClient.startSession(
                        session.getSessionId(),
                        session.getWhatAppToken()
                );
            }

            /*
             * Always refresh latest status
             */
            Map<String, Object> latest = openWAClient.getSession(
                    session.getSessionId(),
                    session.getWhatAppToken()
            );

            if (latest != null) {
                applyWahaState(session, latest);
            }

            return composeStatusPayload(session);

        } catch (Exception e) {

            log.error("WhatsApp connect failed for seller {}", sellerId, e);

            session.setStatus("FAILED");
            session.setConnected(false);

            sessionRepository.save(session);

            throw new RuntimeException("Unable to connect WhatsApp session");
        }
    }

    @Transactional
    public void createToken(Long sellerId) {
        WhatsAppSession session = ensureLocalSession(sellerId);
        Map<String, Object> wahaToken = openWAClient.createToken(session.getSessionName());
        if (wahaToken != null) applyWahaToken(session, wahaToken);
        String apiKey = wahaToken != null ? asString(wahaToken.get("apiKey")) : null;
        if(apiKey != null) {
            Map<String, Object> wahaSession = openWAClient.createSession(session.getSessionName(), webhookUrl,session.getWhatAppToken());
            if (wahaSession != null) applyWahaState(session, wahaSession);
        }
    }

    //
    @Transactional
    public void updateToken(Long sellerId) {
        WhatsAppSession session = ensureLocalSession(sellerId);
        Map<String, Object> wahaToken = openWAClient.UpdateToken(session.getWhatAppTokenID(),session.getSessionName(),session.getSessionId());
        if (wahaToken != null) applyWahaToken(session, wahaToken);

    }

    @Transactional
    public Map<String, Object> status(Long sellerId) {

        WhatsAppSession session = sessionRepository
                .findBySeller_Id(sellerId)
                .orElse(null);

        if (session == null) {

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "DISCONNECTED");
            out.put("connected", false);

            return out;
        }

        if (session.getSessionId() == null || session.getSessionId().isBlank()) {
            return composeStatusPayload(session);
        }

        try {

            Map<String, Object> latest = openWAClient.getSession(
                    session.getSessionId(),
                    session.getWhatAppToken()
            );

            if (latest != null) {
                applyWahaState(session, latest);
            }

        } catch (Exception e) {
            log.error("Failed to fetch WA session status", e);
        }

        return composeStatusPayload(session);
    }

    @Transactional
    public Map<String, Object> restart(Long sellerId) {

        WhatsAppSession session = sessionRepository.findBySeller_Id(sellerId)
                .orElseThrow(() -> new IllegalStateException(
                        "No WhatsApp session found"
                ));

        openWAClient.restartSession(
                session.getSessionId(),
                session.getWhatAppToken()
        );

        session.setStatus("STARTING");
        session.setConnected(false);

        sessionRepository.save(session);

        return composeStatusPayload(session);
    }

    @Transactional
    public void disconnect(Long sellerId) {

        WhatsAppSession session = sessionRepository
                .findBySeller_Id(sellerId)
                .orElse(null);

        if (session == null) {
            return;
        }

        try {

            if (session.getSessionId() != null) {

                openWAClient.logoutSession(
                        session.getSessionId(),
                        session.getWhatAppToken()
                );

                openWAClient.stopSession(
                        session.getSessionId(),
                        session.getWhatAppToken()
                );

                openWAClient.deleteSession(
                        session.getSessionId(),
                        session.getWhatAppToken()
                );
            }

        } catch (Exception e) {
            log.error("Failed to disconnect WA session", e);
        }

        session.setStatus("DISCONNECTED");
        session.setConnected(false);
        session.setPhoneNumber(null);
        session.setPushName(null);
        //session.setSessionId(null);

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
        humanLikeSend(session.getSessionId(), chatId, text,session.getWhatAppToken());

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
                    session.setConnected("READY".equalsIgnoreCase(status.toString()));
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
                .map(existing -> renameIfStale(existing, sellerId))
                .orElseGet(() -> {
                    WhatsAppSession s = new WhatsAppSession();
                    s.setSeller(seller);
                    s.setSessionName(desiredSessionName(seller));
                    s.setStatus("STARTING");
                    s.setConnected(false);
                    return sessionRepository.save(s);
                });
    }

    private WhatsAppSession renameIfStale(WhatsAppSession existing, Long sellerId) {
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

    private String desiredSessionName(Seller seller) {
        if ("per-user".equalsIgnoreCase(sessionNameMode) && seller != null) {
            return  "session-"+seller.getId();
        }
        return "default";
    }

    private void applyWahaState(WhatsAppSession session, Map<String, Object> wahaSession) {
        Object status = wahaSession.get("status");
        if (status != null) {
            session.setStatus(status.toString());
            String s = status.toString().toUpperCase();

            session.setConnected(isConnected(s));

        }
        Object id = wahaSession.get("id");
        if(id != null) {
            session.setSessionId(id.toString());
        }
        Object phonenumber = wahaSession.get("phone");
        if(phonenumber!=null) {
            String phone = phonenumber.toString();
            if (phone != null && phone.contains("@")) phone = phone.substring(0, phone.indexOf('@'));
            session.setPhoneNumber(phone);
        }
        Object pushName = wahaSession.get("pushName");
        if(pushName!=null) {
            session.setPushName(pushName.toString());
        }

        sessionRepository.save(session);
    }

    //wahaToken
    private void applyWahaToken(WhatsAppSession session, Map<String, Object> wahaSession) {

        Object id = wahaSession.get("id");
        if(id != null) {
            session.setWhatAppTokenID(id.toString());
        }
        Object expiresAt = wahaSession.get("expiresAt");
        if (expiresAt != null) {
            session.setExpiresAt(expiresAt.toString());
        }
        Object apiKey = wahaSession.get("apiKey");
        if(apiKey!=null) {
            session.setWhatAppToken(apiKey.toString());
        }
        sessionRepository.save(session);
    }


    private Map<String, Object> composeStatusPayload(
            WhatsAppSession session
    ) {

        Map<String, Object> out = new LinkedHashMap<>();

        String status = asString(session.getStatus());

        out.put("name", session.getSessionName());
        out.put("status", status);
        out.put("connected", Boolean.TRUE.equals(session.getConnected()));

        try {

            /*
             * QR READY
             */
            if ("QR_READY".equalsIgnoreCase(status)) {

                Map<String, Object> qr = openWAClient.getQrRaw(
                        session.getSessionId(),
                        session.getWhatAppToken()
                );

                if (qr != null && qr.get("value") != null) {

                    out.put("qrBase64", qr.get("value"));

                    out.put(
                            "qrMimetype",
                            qr.getOrDefault("mimetype", "image/png")
                    );
                }
            }

            /*
             * Connected details
             */
            if (Boolean.TRUE.equals(session.getConnected())) {

                out.put("phone", session.getPhoneNumber());
                out.put("pushName", session.getPushName());
            }

        } catch (Exception e) {

            log.error("Failed to compose WA payload", e);

            out.put("error", e.getMessage());
        }

        return out;
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

    private boolean isConnected(String status) {

        if (status == null) {
            return false;
        }

        return switch (status.toUpperCase()) {

            case "CONNECTED",
                 "WORKING",
                 "READY" -> true;

            default -> false;
        };
    }

    private String getStatus(Map<String, Object> map) {

        Object status = map.get("status");

        return status != null
                ? status.toString()
                : "";
    }

    private void humanLikeSend(String sessionId, String chatId, String text,String Token) {
        //coming Soon
        //waha.setPresence(sessionName, chatId, "typing");
        try {
            Thread.sleep(typingDelayMillis(text));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        try {
            openWAClient.sendText(sessionId, chatId, text,Token);
        } catch (Exception e) {
            log.error("Open WA client messages send failed "+e.getMessage());
        }
        /*finally {
            //openWAClient.setPresence(sessionName, chatId, "paused");
        }*/
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
