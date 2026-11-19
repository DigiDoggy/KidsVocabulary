package com.kidsvocab.web.auth;

import com.kidsvocab.app.auth.AuthService;
import com.kidsvocab.domain.account.ParentAccount;
import com.kidsvocab.security.CurrentUser;
import com.kidsvocab.web.auth.AuthDtos.ChangePinRequest;
import com.kidsvocab.web.auth.AuthDtos.MeResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AuthService auth;

    public AccountController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/me")
    public MeResponse me(CurrentUser user) {
        ParentAccount account = auth.account(user);
        return new MeResponse(account.getEmail(), account.getTimezone(), user.mode(), user.childId());
    }

    @PutMapping("/pin")
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public ResponseEntity<Void> changePin(CurrentUser user, @Valid @RequestBody ChangePinRequest request) {
        auth.changePin(user, request.currentPassword(), request.newPin());
        return ResponseEntity.noContent().build();
    }
}
