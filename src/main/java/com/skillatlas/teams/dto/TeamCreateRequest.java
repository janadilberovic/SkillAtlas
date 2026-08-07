package com.skillatlas.teams.dto;

import jakarta.validation.constraints.NotBlank;

public record TeamCreateRequest(
        @NotBlank String name
) {
}
