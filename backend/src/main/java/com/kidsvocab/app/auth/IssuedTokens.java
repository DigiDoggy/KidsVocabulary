package com.kidsvocab.app.auth;

import com.kidsvocab.domain.account.AuthMode;

import java.time.Duration;

public record IssuedTokens(String accessToken, Duration accessTokenTtl, String refreshToken, Duration refreshTokenTtl,
                           AuthMode mode, Long childId) {
}
