package com.kidsvocab.app.auth;

import com.kidsvocab.app.AppException;
import com.kidsvocab.domain.account.AuthMode;
import com.kidsvocab.domain.account.RefreshToken;
import com.kidsvocab.domain.account.RefreshTokenRepository;
import com.kidsvocab.security.SecurityProperties;
import com.kidsvocab.security.TokenClaims;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues short-lived JWT access tokens and long-lived opaque refresh tokens.
 * Refresh tokens are single-use (rotated on every refresh); presenting an already used token
 * revokes every refresh token of the account, since it means the token has leaked.
 */
@Service
public class TokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens, SecurityProperties properties,
                        Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedTokens issue(long parentId, AuthMode mode, Long childId) {
        Instant now = clock.instant();
        String refresh = newRefreshToken();
        refreshTokens.save(new RefreshToken(parentId, hash(refresh), mode, childId,
                now.plus(properties.refreshTokenTtl()), now));
        return new IssuedTokens(accessToken(parentId, mode, childId, now), properties.accessTokenTtl(),
                refresh, properties.refreshTokenTtl(), mode, childId);
    }

    /** Exchanges a refresh token for a new pair, keeping its mode. */
    @Transactional(noRollbackFor = AppException.class)
    public IssuedTokens rotate(String rawRefreshToken) {
        RefreshToken token = findUsable(rawRefreshToken);
        token.revoke();
        return issue(token.getParentId(), token.getMode(), token.getChildId());
    }

    /** Revokes the given refresh token if it exists; unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken != null) {
            refreshTokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(RefreshToken::revoke);
        }
    }

    private RefreshToken findUsable(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidRefresh();
        }
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawRefreshToken)).orElseThrow(TokenService::invalidRefresh);
        if (token.isRevoked()) {
            refreshTokens.revokeAllForParent(token.getParentId());
            throw invalidRefresh();
        }
        if (!token.isUsable(clock.instant())) {
            throw invalidRefresh();
        }
        return token;
    }

    private String accessToken(long parentId, AuthMode mode, Long childId, Instant now) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(Long.toString(parentId))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(TokenClaims.MODE, mode.name());
        if (childId != null) {
            claims.claim(TokenClaims.CHILD_ID, childId);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex of a refresh token, as stored in the database. */
    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static AppException invalidRefresh() {
        return new AppException(AppException.Code.INVALID_REFRESH_TOKEN, "Refresh token is invalid or expired");
    }
}
