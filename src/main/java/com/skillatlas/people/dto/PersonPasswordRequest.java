package com.skillatlas.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Its own request rather than a field on PersonUpdateRequest: a password must never be able to
// ride along with a name change, and the endpoint that takes it is admin-only on its own.
public record PersonPasswordRequest(
        @NotBlank @Size(min = 8) String password
) {
}
