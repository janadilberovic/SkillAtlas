package com.skillatlas.skills.dto;

import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.enums.SkillCategory;

public record SkillResponse(
        String id,
        String name,
        SkillCategory category,
        String color
) {
    public static SkillResponse from(Skill s) {
        return new SkillResponse(s.getId(), s.getName(), s.getCategory(), s.getColor());
    }
}
