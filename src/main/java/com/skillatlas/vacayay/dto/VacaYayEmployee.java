package com.skillatlas.vacayay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * One row exactly as VacaYAY's {@code GET /api/employees} returns it — field names are the old
 * system's, not SkillAtlas's. The constraints are here because the import path has to validate what
 * it receives: a row with a malformed email must not become a Person just because it arrived over
 * HTTP instead of through the form.
 *
 * @param role "Employee" or "HR"; deliberately never mapped to a SkillAtlas role
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VacaYayEmployee(
        int id,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        String role,
        String department,
        String jobTitle,
        boolean isActive) {
}
