package com.skillatlas.projects.dto;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;

// PUT = full replacement: fields left out are cleared, active must be sent explicitly.
public record ProjectUpdateRequest(
        @NotBlank String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        Set<String> skillIds
) {
}
