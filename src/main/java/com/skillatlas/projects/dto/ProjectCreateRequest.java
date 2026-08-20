package com.skillatlas.projects.dto;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;

public record ProjectCreateRequest(
        @NotBlank String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        Set<String> skillIds,
        Boolean active
) {
    /** Omitting `active` means an active project — the common case, and what every caller before it assumed. */
    public ProjectCreateRequest(String name, String description, LocalDate startDate, LocalDate endDate,
            Set<String> skillIds) {
        this(name, description, startDate, endDate, skillIds, null);
    }
}
