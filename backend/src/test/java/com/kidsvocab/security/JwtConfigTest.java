package com.kidsvocab.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class JwtConfigTest {

    @Test
    void usesTheConfiguredSecret() {
        String secret = "x".repeat(JwtConfig.MIN_SECRET_BYTES);

        assertThat(JwtConfig.signingKey(secret).getEncoded()).hasSize(JwtConfig.MIN_SECRET_BYTES);
    }

    @Test
    void generatesARandomKeyWhenNoSecretIsSet() {
        assertThat(JwtConfig.signingKey("").getEncoded())
                .hasSize(JwtConfig.MIN_SECRET_BYTES)
                .isNotEqualTo(JwtConfig.signingKey(null).getEncoded());
    }

    @Test
    void rejectsShortSecrets() {
        assertThatIllegalStateException().isThrownBy(() -> JwtConfig.signingKey("too-short"));
    }
}
