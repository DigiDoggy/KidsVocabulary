package com.kidsvocab.domain.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "parent_account")
public class ParentAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "pin_hash", nullable = false)
    private String pinHash;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "failed_pin_attempts", nullable = false)
    private int failedPinAttempts;

    @Column(name = "pin_locked_until")
    private Instant pinLockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ParentAccount() {
    }

    public ParentAccount(String email, String passwordHash, String pinHash, String timezone, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.pinHash = pinHash;
        this.timezone = timezone;
        this.createdAt = createdAt;
    }

    public boolean isPinLocked(Instant now) {
        return pinLockedUntil != null && now.isBefore(pinLockedUntil);
    }

    /** Counts a wrong PIN; after {@code maxAttempts} wrong PINs in a row parent mode is locked for {@code lock}. */
    public void registerFailedPin(Instant now, int maxAttempts, Duration lock) {
        failedPinAttempts++;
        if (failedPinAttempts >= maxAttempts) {
            pinLockedUntil = now.plus(lock);
            failedPinAttempts = 0;
        }
    }

    public void resetPinAttempts() {
        failedPinAttempts = 0;
        pinLockedUntil = null;
    }

    public void changePin(String newPinHash) {
        this.pinHash = newPinHash;
        resetPinAttempts();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPinHash() {
        return pinHash;
    }

    public String getTimezone() {
        return timezone;
    }

    public int getFailedPinAttempts() {
        return failedPinAttempts;
    }

    public Instant getPinLockedUntil() {
        return pinLockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
