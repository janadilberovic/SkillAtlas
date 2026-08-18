package com.skillatlas.dashboard.dto;

import java.time.Instant;
import java.util.List;

import com.skillatlas.common.PageResponse;

/**
 * E6.3, the four widgets in one payload. They come from four queries, not four requests: an admin
 * opening the dashboard wants one answer, and the widgets read each other's context.
 *
 * @param skillGap     technologies a team's projects use that nobody (or only one person) on that
 *                     team knows — the only widget that grows without bound, so it is a page and
 *                     carries its own total rather than a silently truncated list
 * @param mentorRequests wishes nobody mentors yet — the admin's queue, so approving a mentor does
 *                       not mean walking every profile looking for one
 * @param busFactor    skills exactly one active person knows — losing them loses the skill
 * @param mappingQueue active people with no KNOWS at all, so the graph cannot see them yet
 */
public record DashboardResponse(
        Metrics metrics,
        PageResponse<SkillGapRow> skillGap,
        PageResponse<MentorRequestRow> mentorRequests,
        List<BusFactorRow> busFactor,
        MappingQueue mappingQueue) {

    public record Metrics(long people, long skills, long projects, long mentorships) {
    }

    public record SkillGapRow(String team, String skill, List<String> projects, long knownBy) {
    }

    /**
     * One unanswered "wants to learn".
     *
     * @param candidates how many people could mentor it today — a row with none is a hiring or
     *                   training problem, not a click, and the card says so instead of opening an
     *                   empty modal
     */
    public record MentorRequestRow(
            String personId,
            String personName,
            String skillId,
            String skillName,
            Instant wantedSince,
            long candidates) {
    }

    public record BusFactorRow(String skill, String personId, String personName) {
    }

    public record MappingQueue(long total, List<PersonRef> people) {
    }

    public record PersonRef(String id, String name) {
    }
}
