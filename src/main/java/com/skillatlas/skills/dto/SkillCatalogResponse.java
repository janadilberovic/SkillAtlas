package com.skillatlas.skills.dto;

import java.util.List;

import com.skillatlas.skills.enums.SkillCategory;

/**
 * A catalog row: the skill plus what deleting it would cost — how many people know it, how many
 * want to learn it, and which projects still USE it.
 */
public record SkillCatalogResponse(
        String id,
        String name,
        SkillCategory category,
        String color,
        long knownBy,
        long wantedBy,
        List<String> usedBy
) {
}
