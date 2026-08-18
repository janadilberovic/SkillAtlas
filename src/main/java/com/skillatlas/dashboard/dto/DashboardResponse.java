package com.skillatlas.dashboard.dto;

import java.util.List;

/**
 * E6.3, the four widgets in one payload. They come from four queries, not four requests: an admin
 * opening the dashboard wants one answer, and the widgets read each other's context.
 *
 * @param skillGap     technologies a team's projects use that nobody (or only one person) on that
 *                     team knows
 * @param busFactor    skills exactly one active person knows — losing them loses the skill
 * @param mappingQueue active people with no KNOWS at all, so the graph cannot see them yet
 */
public record DashboardResponse(
        Metrics metrics,
        List<SkillGapRow> skillGap,
        List<BusFactorRow> busFactor,
        MappingQueue mappingQueue) {

    public record Metrics(long people, long skills, long projects, long mentorships) {
    }

    public record SkillGapRow(String team, String skill, List<String> projects, long knownBy) {
    }

    public record BusFactorRow(String skill, String personId, String personName) {
    }

    public record MappingQueue(long total, List<PersonRef> people) {
    }

    public record PersonRef(String id, String name) {
    }
}
