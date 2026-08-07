package com.skillatlas.auth.dto;

import com.skillatlas.people.enums.Role;

public record LoginResponse(
        String token,
        String tokenType,
        Role role
) {
    public static LoginResponse bearer(String token, Role role) {
        return new LoginResponse(token, "Bearer", role);
    }
}
