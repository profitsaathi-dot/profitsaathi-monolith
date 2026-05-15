package com.profitsaathi.auth;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "passkeys",
        uniqueConstraints = @UniqueConstraint(columnNames = "credential_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Passkeys {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "users_id")
    private Long userId;

    @Column(name = "credential_id", nullable = false, unique = true)
    private String credentialId;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    private int counter;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(nullable = false,name="backed_up")
    private boolean backUp = true;

    @Column(name = "transports",columnDefinition = "TEXT")
    private String transports;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

}
