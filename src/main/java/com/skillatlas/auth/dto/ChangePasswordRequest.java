package com.skillatlas.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// No id and no email: the endpoint takes the caller from the token, so a body that carried an
// identity would only be a second, weaker way to say who is changing what.
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8) String newPassword
) {
}
