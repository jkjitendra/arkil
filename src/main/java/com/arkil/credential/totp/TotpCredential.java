package com.arkil.credential.totp;

import com.arkil.user.ArkilUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * TOTP credential for multi-factor authentication.
 * One user can have one TOTP credential.
 */
@Entity
@Table(name = "totp_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotpCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private ArkilUser user;

    /**
     * Encrypted TOTP secret (base32 encoded, then encrypted).
     */
    @Column(nullable = false)
    private String secretEncrypted;

    /**
     * Number of digits in the TOTP code.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer digits = 6;

    /**
     * Time period in seconds for each TOTP code.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer period = 30;

    /**
     * HMAC algorithm used (SHA1, SHA256, SHA512).
     */
    @Column(nullable = false)
    @Builder.Default
    private String algorithm = "SHA1";

    /**
     * Whether TOTP is enabled (set to true after verification).
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    /**
     * When the TOTP was first verified/confirmed.
     */
    private Instant confirmedAt;

    /**
     * Hashed backup codes for account recovery.
     */
    @Column(columnDefinition = "TEXT")
    private String backupCodesHash;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant lastUsedAt;

    @PreUpdate
    protected void onUpdate() {
        if (this.enabled && this.confirmedAt == null) {
            this.confirmedAt = Instant.now();
        }
    }
}
