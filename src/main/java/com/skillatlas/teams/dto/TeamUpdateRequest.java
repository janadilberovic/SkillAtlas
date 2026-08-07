package com.skillatlas.teams.dto;

import jakarta.validation.constraints.NotBlank;

public record TeamUpdateRequest(
        @NotBlank String name
) {
}
