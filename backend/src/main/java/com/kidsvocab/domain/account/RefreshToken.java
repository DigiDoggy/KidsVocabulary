package com.kidsvocab.domain.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id", nullable = false)
    private Long parentId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthMode mode;

    @Column(name = "child_id")
    private Long childId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(Long parentId, String tokenHash, AuthMode mode, Long childId, Instant expiresAt,
                        Instant createdAt) {
        this.parentId = parentId;
        this.tokenHash = tokenHash;
        this.mode = mode;
        this.childId = childId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public boolean isUsable(Instant now) {
        return !revoked && now.isBefore(expiresAt);
    }

    public void revoke() {
        revoked = true;
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public AuthMode getMode() {
        return mode;
    }

    public Long getChildId() {
        return childId;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
