package com.kidsvocab.security;

/** Custom claim names in access tokens. The account id is the standard {@code sub} claim. */
public final class TokenClaims {

    public static final String MODE = "mode";
    public static final String CHILD_ID = "childId";
    /** Authority granted for the token mode, e.g. {@code MODE_PARENT}. */
    public static final String MODE_AUTHORITY_PREFIX = "MODE_";

    private TokenClaims() {
    }
}
