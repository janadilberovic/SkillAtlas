package com.skillatlas.projects.dto;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;

public record ProjectCreateRequest(
        @NotBlank String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        Set<String> skillIds
) {
}
