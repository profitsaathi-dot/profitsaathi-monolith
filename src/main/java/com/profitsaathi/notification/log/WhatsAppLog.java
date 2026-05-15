package com.profitsaathi.notification.log;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "whatsapp_log")
public class WhatsAppLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private String chatId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    public WhatsAppLog() {}

    public Long getId() { return id; }
    public String getChatId() { return chatId; }
    public String getStatus() { return status; }
    public String getText() { return text; }
    public String getErrorMessage() { return errorMessage; }
    public Date getCreatedAt() { return createdAt; }

    public void setChatId(String chatId) { this.chatId = chatId; }
    public void setStatus(String status) { this.status = status; }
    public void setText(String text) { this.text = text; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
