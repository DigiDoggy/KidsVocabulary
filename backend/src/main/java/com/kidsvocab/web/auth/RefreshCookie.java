package com.kidsvocab.web.auth;

import com.kidsvocab.app.auth.IssuedTokens;
import com.kidsvocab.security.SecurityProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** The refresh token cookie: httpOnly, SameSite=Strict, sent only to the auth endpoints. */
@Component
public class RefreshCookie {

    public static final String NAME = "refresh_token";
    static final String PATH = "/api/v1/auth";

    private final SecurityProperties properties;

    public RefreshCookie(SecurityProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie issue(IssuedTokens tokens) {
        return build(tokens.refreshToken(), tokens.refreshTokenTtl());
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
