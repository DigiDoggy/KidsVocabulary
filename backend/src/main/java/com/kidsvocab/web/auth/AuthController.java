package com.kidsvocab.web.auth;

import com.kidsvocab.app.auth.AuthService;
import com.kidsvocab.app.auth.IssuedTokens;
import com.kidsvocab.security.CurrentUser;
import com.kidsvocab.web.auth.AuthDtos.LoginRequest;
import com.kidsvocab.web.auth.AuthDtos.PinRequest;
import com.kidsvocab.web.auth.AuthDtos.RegisterRequest;
import com.kidsvocab.web.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final RefreshCookie refreshCookie;

    public AuthController(AuthService auth, RefreshCookie refreshCookie) {
        this.auth = auth;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return respond(HttpStatus.CREATED,
                auth.register(request.email(), request.password(), request.pin(), request.timezone()));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return respond(HttpStatus.OK, auth.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        return respond(HttpStatus.OK, auth.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        auth.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .build();
    }

    @PostMapping("/switch-to-child/{childId}")
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public ResponseEntity<TokenResponse> switchToChild(
            CurrentUser user, @PathVariable long childId,
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        return respond(HttpStatus.OK, auth.switchToChild(user, childId, refreshToken));
    }

    @PostMapping("/switch-to-parent")
    public ResponseEntity<TokenResponse> switchToParent(
            CurrentUser user, @Valid @RequestBody PinRequest request,
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        return respond(HttpStatus.OK, auth.switchToParent(user, request.pin(), refreshToken));
    }

    private ResponseEntity<TokenResponse> respond(HttpStatus status, IssuedTokens tokens) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.issue(tokens).toString())
                .body(new TokenResponse(tokens.accessToken(), tokens.accessTokenTtl().toSeconds(),
                        tokens.mode(), tokens.childId()));
    }
}
