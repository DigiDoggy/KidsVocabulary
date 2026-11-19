package com.kidsvocab.domain.account;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ParentAccountTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final Duration LOCK = Duration.ofMinutes(5);

    @Test
    void locksAfterTooManyWrongPinsAndUnlocksLater() {
        ParentAccount account = account();

        for (int i = 0; i < 4; i++) {
            account.registerFailedPin(NOW, 5, LOCK);
        }
        assertThat(account.isPinLocked(NOW)).isFalse();
        assertThat(account.getFailedPinAttempts()).isEqualTo(4);

        account.registerFailedPin(NOW, 5, LOCK);
        assertThat(account.isPinLocked(NOW)).isTrue();
        assertThat(account.isPinLocked(NOW.plus(LOCK).minusSeconds(1))).isTrue();
        assertThat(account.isPinLocked(NOW.plus(LOCK))).isFalse();
        assertThat(account.getFailedPinAttempts()).isZero();
    }

    @Test
    void correctPinOrNewPinResetsTheCounter() {
        ParentAccount account = account();
        account.registerFailedPin(NOW, 5, LOCK);

        account.resetPinAttempts();
        assertThat(account.getFailedPinAttempts()).isZero();

        account.registerFailedPin(NOW, 1, LOCK);
        account.changePin("new-hash");
        assertThat(account.isPinLocked(NOW)).isFalse();
        assertThat(account.getPinHash()).isEqualTo("new-hash");
    }

    private static ParentAccount account() {
        return new ParentAccount("a@example.com", "pw", "pin", "Europe/Tallinn", NOW);
    }
}
