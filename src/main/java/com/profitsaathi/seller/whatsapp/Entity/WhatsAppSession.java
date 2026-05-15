package com.profitsaathi.seller.whatsapp.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.profitsaathi.seller.user.Seller;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One row per seller — each seller maps to exactly one WAHA session.
 *
 * sessionName is the deterministic id we hand to WAHA so we can reattach
 * to an already-running session after a restart.
 *
 * status mirrors WAHA's session state (STARTING / SCAN_QR_CODE / WORKING /
 * FAILED / STOPPED) — lazily updated from webhook events plus pulls.
 */
@Entity
@Data
@Table(name = "whatsapp_sessions")
public class WhatsAppSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false, unique = true)
    @JsonIgnore
    private Seller seller;

    @Column(name = "session_id", unique = true, length = 128)
    private String sessionId;

    @Column(name = "session_name", nullable = false, unique = true, length = 128)
    private String sessionName;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "push_name", length = 128)
    private String pushName;

    @Column(nullable = false)
    private Boolean connected = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
