package com.skillatlas.projects.dto;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.skillatlas.projects.domain.Project;
import com.skillatlas.skills.dto.SkillResponse;

public record ProjectResponse(
        String id,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        List<SkillResponse> skills
) {
    public static ProjectResponse from(Project p) {
        List<SkillResponse> skills = p.getUses().stream()
                .map(SkillResponse::from)
                .sorted(Comparator.comparing(SkillResponse::name))
                .toList();
        return new ProjectResponse(
                p.getId(), p.getName(), p.getDescription(),
                p.getStartDate(), p.getEndDate(), p.isActive(), skills);
    }
}
