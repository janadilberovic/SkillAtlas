package com.skillatlas.projects.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One WORKED_ON edge, projected onto the project.
 *
 * <p>{@code left} marks a soft-deleted person. Spec §4.6 keeps their history here — "the project
 * still knows who worked on it" — while every other read drops them, so this is the one response
 * that carries a deleted person at all, and it carries no profile link: that read filters them out.
 */
public record ProjectMemberResponse(
        String personId,
        String name,
        String role,
        LocalDate from,
        LocalDate to,
        boolean left,
        List<StackSkill> knows
) {
    /** Only the project's own USES skills; {@code level} is a KNOWS property, not a skill field. */
    public record StackSkill(String skillId, String name, int level) {
    }
}
