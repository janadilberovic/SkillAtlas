package com.skillatlas.projects.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

// Body for assigning a person to a project (the WORKED_ON relationship).
public record ProjectMemberRequest(
        @NotBlank String role,
        LocalDate from,
        LocalDate to
) {
}
