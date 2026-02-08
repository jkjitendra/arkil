package com.arkil.credential.passkey;

import com.arkil.user.ArkilUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Passkey/WebAuthn credential for passwordless authentication.
 * One user can have multiple passkeys (multiple devices).
 */
@Entity
@Table(name = "passkey_credentials", uniqueConstraints = {
        @UniqueConstraint(name = "uk_passkey_credential_id", columnNames = {"credentialId"})
}, indexes = {
        @Index(name = "idx_passkey_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasskeyCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private ArkilUser user;

    /**
     * WebAuthn credential ID (base64 encoded).
     */
    @Column(nullable = false, length = 1024)
    private String credentialId;

    /**
     * Public key (base64 encoded COSE key).
     */
    @Column(nullable = false, length = 2048)
    private String publicKey;

    /**
     * Signature counter for replay protection.
     */
    @Column(nullable = false)
    @Builder.Default
    private Long signCount = 0L;

    /**
     * User-friendly label for the device.
     */
    private String label;

    /**
     * Relying party ID this credential is bound to.
     */
    @Column(nullable = false)
    private String rpId;

    /**
     * AAGUID of the authenticator (for device identification).
     */
    private String aaguid;

    /**
     * Whether this credential supports user verification.
     */
    @Builder.Default
    private Boolean userVerificationCapable = false;

    /**
     * Whether this credential is a resident/discoverable credential.
     */
    @Builder.Default
    private Boolean discoverable = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant lastUsedAt;

    @PreUpdate
    protected void onUpdate() {
        this.lastUsedAt = Instant.now();
    }
}
