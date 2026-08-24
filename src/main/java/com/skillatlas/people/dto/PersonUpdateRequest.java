package com.skillatlas.people.dto;

import jakarta.validation.constraints.NotBlank;

// Profile-safe fields only. No role/email/active here — those must not be settable
// through a profile update (mass-assignment protection, CLAUDE.md). The picture is not here
// either: it has exactly one writer, POST /people/{id}/avatar, which stores a blob key.
public record PersonUpdateRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String position
) {
}
