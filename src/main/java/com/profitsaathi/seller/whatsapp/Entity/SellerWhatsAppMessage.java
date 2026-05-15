package com.profitsaathi.seller.whatsapp.Entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Audit log of seller-initiated WhatsApp messages (sends + inbound webhook
 * captures). Distinct from the system-wide whatsapp_log written by the
 * notification module — this one is keyed by the seller's session_name and
 * surfaces in the seller-app's chat history view.
 */
@Entity
@Data
@Table(name = "whatsapp_messages")
public class SellerWhatsAppMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_name", nullable = false, length = 128)
    private String sessionName;

    @Column(name = "chat_id", length = 128)
    private String chatId;

    @Column(columnDefinition = "TEXT")
    private String message;

    /** "INBOUND" | "OUTBOUND" */
    @Column(nullable = false, length = 16)
    private String direction;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
