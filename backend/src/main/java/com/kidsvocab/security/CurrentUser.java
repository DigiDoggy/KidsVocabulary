package com.kidsvocab.security;

import com.kidsvocab.domain.account.AuthMode;

/**
 * The authenticated caller, taken from the access token.
 *
 * @param parentId account id (a child session still belongs to the parent's account)
 * @param mode     parent or child mode
 * @param childId  the open child profile in child mode, otherwise {@code null}
 */
public record CurrentUser(long parentId, AuthMode mode, Long childId) {

    public boolean isParent() {
        return mode == AuthMode.PARENT;
    }

    /** True if the caller may view data of the given child: its parent, or the child itself. */
    public boolean canView(long otherChildId) {
        return isParent() || Long.valueOf(otherChildId).equals(childId);
    }
}
