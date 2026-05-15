package com.profitsaathi.admin;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "admins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "theme", length = 16)
    private String theme;

    @Column(name = "accent", length = 16)
    private String accent;

    @Column(name = "region", length = 16)
    private String region;

    // columnDefinition (not just @Builder.Default) gives the ALTER TABLE an
    // SQL-level default so the NOT NULL add succeeds against tables that
    // already have rows. Without this, Postgres rejects the migration.
    @Column(name = "notify_email", columnDefinition = "boolean not null default true")
    @Builder.Default
    private boolean notifyEmail = true;

    @Column(name = "notify_whatsapp", columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean notifyWhatsapp = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @LastModifiedBy
    private String updatedBy;

    public enum Status { ACTIVE, INACTIVE, SUSPENDED }
}
