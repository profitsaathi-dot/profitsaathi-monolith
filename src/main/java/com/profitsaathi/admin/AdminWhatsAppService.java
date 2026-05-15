package com.profitsaathi.admin;

import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.whatsapp.WahaClient;
import com.profitsaathi.seller.whatsapp.Entity.WhatsAppSession;
import com.profitsaathi.seller.whatsapp.Repo.WhatsAppSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the WhatsApp/WAHA overview for the admin console.
 *
 * Two data sources merged in one query path:
 *   - WAHA server (live state) — list of running sessions + their statuses
 *   - whatsapp_sessions table   — seller mapping, last-known phone/pushName
 *
 * Server probe and DB read run independently so a WAHA outage doesn't hide
 * the seller mapping (and vice versa).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminWhatsAppService {

    private final WahaClient wahaClient;
    private final WhatsAppSessionRepository sessionRepository;

    /**
     * Read-only transaction keeps the Hibernate session open for the whole
     * method so the lazy {@code WhatsAppSession.seller} proxy can initialize
     * when we touch seller.getName() / .getEmail() below. Without this,
     * {@code spring.jpa.open-in-view=false} closes the session as soon as the
     * repository call returns and the next access throws
     * LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public WhatsAppOverview overview() {
        // ── 1. Probe WAHA ────────────────────────────────────────────────
        WhatsAppOverview.ServerStatus serverStatus = WhatsAppOverview.ServerStatus.UNKNOWN;
        Map<String, String> wahaStatusBySession = new HashMap<>();
        try {
            List<Map<String, Object>> live = wahaClient.listSessions();
            serverStatus = WhatsAppOverview.ServerStatus.UP;
            for (Map<String, Object> entry : live) {
                Object name = entry.get("name");
                Object status = entry.get("status");
                if (name instanceof String n && status != null) {
                    wahaStatusBySession.put(n, status.toString());
                }
            }
        } catch (Exception e) {
            log.warn("[admin-whatsapp] WAHA probe failed: {}", e.getMessage());
            serverStatus = WhatsAppOverview.ServerStatus.DOWN;
        }

        // ── 2. Pull our DB rows (with seller for enrichment) ─────────────
        List<WhatsAppSession> rows = sessionRepository.findAll();

        // ── 3. Merge into rows + counts ──────────────────────────────────
        List<WhatsAppOverview.SessionRow> sessions = new java.util.ArrayList<>(rows.size());
        Map<String, Long> byStatus = new LinkedHashMap<>();
        int working = 0, connected = 0, scanning = 0, failed = 0, stopped = 0, other = 0;

        for (WhatsAppSession row : rows) {
            Seller seller = row.getSeller();
            String wahaStatus = wahaStatusBySession.get(row.getSessionName());
            String effective = wahaStatus != null ? wahaStatus : row.getStatus();

            byStatus.merge(effective == null ? "UNKNOWN" : effective, 1L, Long::sum);
            switch (effective == null ? "" : effective.toUpperCase()) {
                case "WORKING"      -> working++;
                case "SCAN_QR_CODE" -> scanning++;
                case "FAILED"       -> failed++;
                case "STOPPED"      -> stopped++;
                default             -> other++;
            }
            if (Boolean.TRUE.equals(row.getConnected())) connected++;

            sessions.add(new WhatsAppOverview.SessionRow(
                    row.getSessionName(),
                    row.getStatus(),
                    wahaStatus,
                    Boolean.TRUE.equals(row.getConnected()),
                    row.getPhoneNumber(),
                    row.getPushName(),
                    seller != null ? seller.getId() : null,
                    seller != null ? seller.getName() : null,
                    seller != null ? seller.getEmail() : null,
                    seller != null ? seller.getStoreName() : null,
                    row.getUpdatedAt()
            ));
        }

        // Surface WAHA-only sessions (registered on the server but missing
        // from our DB) so the dashboard exposes drift instead of hiding it.
        for (Map.Entry<String, String> e : wahaStatusBySession.entrySet()) {
            boolean known = rows.stream()
                    .anyMatch(r -> e.getKey().equals(r.getSessionName()));
            if (!known) {
                byStatus.merge(e.getValue(), 1L, Long::sum);
                if ("WORKING".equalsIgnoreCase(e.getValue())) working++;
                else other++;
                sessions.add(new WhatsAppOverview.SessionRow(
                        e.getKey(),
                        null,
                        e.getValue(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            }
        }

        WhatsAppOverview.Counts counts = new WhatsAppOverview.Counts(
                sessions.size(), working, connected, scanning, failed, stopped, other, byStatus);

        return new WhatsAppOverview(serverStatus, counts, sessions);
    }
}
