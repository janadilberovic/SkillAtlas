package com.skillatlas.people.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// Body for PUT /people/{id}/skills/{skillId}. Level is validated server-side (spec §4.1).
public record SetSkillLevelRequest(
        @Min(value = 1, message = "level must be between 1 and 5")
        @Max(value = 5, message = "level must be between 1 and 5")
        int level
) {
}
