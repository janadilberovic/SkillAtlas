package com.skillatlas.projects.dto;

import java.time.LocalDate;
import java.util.List;

import com.skillatlas.projects.domain.Project;
import com.skillatlas.skills.dto.SkillResponse;

/**
 * A superset of {@link ProjectResponse} — the project plus its roster, so callers that only read
 * the project fields keep working. {@code GET /projects/{id}} and {@code PUT /projects/{id}} both
 * answer with this; the list answers with {@link ProjectResponse}, which carries only the count.
 */
public record ProjectDetailResponse(
        String id,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        List<SkillResponse> skills,
        int memberCount,
        List<ProjectMemberResponse> members
) {
    public static ProjectDetailResponse from(Project p, List<ProjectMemberResponse> members) {
        // The count is the staff, not the history: people who left the company are listed but
        // not counted, so a card and this header never disagree.
        int staffed = (int) members.stream().filter(m -> !m.left()).count();
        ProjectResponse base = ProjectResponse.from(p, staffed);
        return new ProjectDetailResponse(base.id(), base.name(), base.description(),
                base.startDate(), base.endDate(), base.active(), base.skills(),
                base.memberCount(), members);
    }
}
