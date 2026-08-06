package com.skillatlas.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// Identity comes from the token via the SecurityContext — never from a path/query param.
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
