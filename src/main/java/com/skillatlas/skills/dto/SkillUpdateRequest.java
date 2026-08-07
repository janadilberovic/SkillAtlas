package com.skillatlas.skills.dto;

import com.skillatlas.skills.enums.SkillCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SkillUpdateRequest(
        @NotBlank String name,
        @NotNull SkillCategory category,
        @Pattern(regexp = "^#([0-9a-fA-F]{6})$", message = "color must be a hex code like #4581C3") String color
) {
}
