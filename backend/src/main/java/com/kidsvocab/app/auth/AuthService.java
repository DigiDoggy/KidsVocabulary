package com.kidsvocab.app.auth;

import com.kidsvocab.app.AppException;
import com.kidsvocab.app.AppException.Code;
import com.kidsvocab.domain.account.AuthMode;
import com.kidsvocab.domain.account.ParentAccount;
import com.kidsvocab.domain.account.ParentAccountRepository;
import com.kidsvocab.domain.child.ChildRepository;
import com.kidsvocab.security.CurrentUser;
import com.kidsvocab.security.SecurityProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

@Service
public class AuthService {

    static final String DEFAULT_TIMEZONE = "Europe/Tallinn";

    private final ParentAccountRepository accounts;
    private final ChildRepository children;
    private final TokenService tokens;
    private final PasswordEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;
    /** Compared against when the e-mail is unknown, so both paths take the same time. */
    private final String dummyHash;

    public AuthService(ParentAccountRepository accounts, ChildRepository children, TokenService tokens,
                       PasswordEncoder encoder, SecurityProperties properties, Clock clock) {
        this.accounts = accounts;
        this.children = children;
        this.tokens = tokens;
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @Transactional
    public IssuedTokens register(String email, String password, String pin, String timezone) {
        String normalizedEmail = normalizeEmail(email);
        if (accounts.existsByEmail(normalizedEmail)) {
            throw new AppException(Code.CONFLICT, "An account with this e-mail already exists");
        }
        ParentAccount account = accounts.save(new ParentAccount(normalizedEmail, encoder.encode(password),
                encoder.encode(pin), validTimezone(timezone), clock.instant()));
        return tokens.issue(account.getId(), AuthMode.PARENT, null);
    }

    @Transactional
    public IssuedTokens login(String email, String password) {
        ParentAccount account = accounts.findByEmail(normalizeEmail(email)).orElse(null);
        String hash = account == null ? dummyHash : account.getPasswordHash();
        if (!encoder.matches(password, hash) || account == null) {
            throw new AppException(Code.INVALID_CREDENTIALS, "Wrong e-mail or password");
        }
        return tokens.issue(account.getId(), AuthMode.PARENT, null);
    }

    public IssuedTokens refresh(String refreshToken) {
        return tokens.rotate(refreshToken);
    }

    public void logout(String refreshToken) {
        tokens.revoke(refreshToken);
    }

    /** Opens a child profile: the session continues in child mode with a new token pair. */
    @Transactional
    public IssuedTokens switchToChild(CurrentUser user, long childId, String currentRefreshToken) {
        requireParent(user);
        children.findByIdAndParentIdAndArchivedFalse(childId, user.parentId())
                .orElseThrow(() -> AppException.notFound("Child"));
        tokens.revoke(currentRefreshToken);
        return tokens.issue(user.parentId(), AuthMode.CHILD, childId);
    }

    /** Returns to parent mode; requires the PIN and locks after too many wrong attempts. */
    @Transactional(noRollbackFor = AppException.class)
    public IssuedTokens switchToParent(CurrentUser user, String pin, String currentRefreshToken) {
        ParentAccount account = account(user);
        Instant now = clock.instant();
        if (account.isPinLocked(now)) {
            throw new AppException(Code.PIN_LOCKED, "Too many wrong PINs, try again later");
        }
        if (!encoder.matches(pin, account.getPinHash())) {
            account.registerFailedPin(now, properties.pinMaxAttempts(), properties.pinLockDuration());
            throw new AppException(Code.INVALID_PIN, "Wrong PIN");
        }
        account.resetPinAttempts();
        tokens.revoke(currentRefreshToken);
        return tokens.issue(account.getId(), AuthMode.PARENT, null);
    }

    @Transactional
    public void changePin(CurrentUser user, String currentPassword, String newPin) {
        requireParent(user);
        ParentAccount account = account(user);
        if (!encoder.matches(currentPassword, account.getPasswordHash())) {
            throw new AppException(Code.INVALID_CREDENTIALS, "Wrong password");
        }
        account.changePin(encoder.encode(newPin));
    }

    @Transactional(readOnly = true)
    public ParentAccount account(CurrentUser user) {
        return accounts.findById(user.parentId()).orElseThrow(() -> AppException.notFound("Account"));
    }

    private static void requireParent(CurrentUser user) {
        if (!user.isParent()) {
            throw new AppException(Code.FORBIDDEN, "Parent mode required");
        }
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static String validTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException e) {
            throw new AppException(Code.VALIDATION, "Unknown time zone: " + timezone);
        }
    }
}
