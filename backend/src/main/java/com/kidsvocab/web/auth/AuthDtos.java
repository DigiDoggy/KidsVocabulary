package com.kidsvocab.web.auth;

import com.kidsvocab.domain.account.AuthMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    static final String PIN_PATTERN = "\\d{4,6}";

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Pattern(regexp = PIN_PATTERN, message = "must be 4 to 6 digits") String pin,
            @Size(max = 50) String timezone) {

        /** Keyboards and autofill often add spaces around the e-mail. */
        public RegisterRequest {
            email = strip(email);
        }
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {

        public LoginRequest {
            email = strip(email);
        }
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    public record PinRequest(@NotBlank String pin) {
    }

    public record ChangePinRequest(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PIN_PATTERN, message = "must be 4 to 6 digits") String newPin) {
    }

    /** The refresh token is not part of the body: it travels in an httpOnly cookie. */
    public record TokenResponse(String accessToken, long expiresIn, AuthMode mode, Long childId) {
    }

    public record MeResponse(String email, String timezone, AuthMode mode, Long childId) {
    }
}
