package com.kidsvocab.app.auth;

import com.kidsvocab.app.AppException;
import com.kidsvocab.domain.account.AuthMode;
import com.kidsvocab.domain.account.ParentAccount;
import com.kidsvocab.domain.account.ParentAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Local development shortcut ({@code DEV_MODE=true}): a fixed account is created on start-up and the app
 * signs in to it without a password. Parent mode is still guarded by the account's PIN. Off by default;
 * with it off the dev sign-in endpoint does not exist and no account is created.
 */
@Component
public class DevMode {

    public static final String EMAIL = "admin";
    public static final String PASSWORD = "123456";
    public static final String PIN = "1234";

    private static final Logger log = LoggerFactory.getLogger(DevMode.class);

    private final boolean enabled;
    private final ParentAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final Clock clock;

    public DevMode(@Value("${app.dev-mode:false}") boolean enabled, ParentAccountRepository accounts,
                   PasswordEncoder encoder, TokenService tokens, Clock clock) {
        this.enabled = enabled;
        this.accounts = accounts;
        this.encoder = encoder;
        this.tokens = tokens;
        this.clock = clock;
    }

    public boolean enabled() {
        return enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createAccount() {
        if (!enabled) {
            return;
        }
        log.warn("DEV MODE IS ON: anyone can sign in as '{}' without a password. Never use this in production.",
                EMAIL);
        if (!accounts.existsByEmail(EMAIL)) {
            accounts.save(new ParentAccount(EMAIL, encoder.encode(PASSWORD), encoder.encode(PIN), "Europe/Tallinn",
                    clock.instant()));
        }
    }

    /** Parent-mode tokens for the dev account. */
    @Transactional
    public IssuedTokens signIn() {
        if (!enabled) {
            throw AppException.notFound("Resource");
        }
        createAccount();
        ParentAccount account = accounts.findByEmail(EMAIL).orElseThrow();
        return tokens.issue(account.getId(), AuthMode.PARENT, null);
    }
}
