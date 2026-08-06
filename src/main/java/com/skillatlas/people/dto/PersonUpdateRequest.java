package com.skillatlas.people.dto;

import jakarta.validation.constraints.NotBlank;

// Profile-safe fields only. No role/email/active here — those must not be settable
// through a profile update (mass-assignment protection, CLAUDE.md).
public record PersonUpdateRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String position,
        String profilePicture
) {
}
