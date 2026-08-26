package com.skillatlas.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// Identity comes from the token via the SecurityContext — never from a path/query param.
public final class SecurityUtil {

    private static final String ADMIN = "ROLE_ADMIN";

    private SecurityUtil() {
    }

    public static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    /** For read shapes that hide a field from non-admins. Access control itself is @PreAuthorize. */
    public static boolean currentUserIsAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ADMIN.equals(a.getAuthority()));
    }
}
