package com.skillatlas.people.dto;

import java.time.LocalDate;
import java.util.List;

import com.skillatlas.people.enums.Role;

/**
 * A superset of {@link PersonResponse}, so callers that only read the person fields keep working.
 * Empty branches are empty lists, never {@code null}.
 *
 * <p>{@code GraphNode} / {@code GraphEdge} are nested until E5.1 needs them for a second caller.
 * They carry no layout — placement is the client force-graph's job, not the database's.
 */
public record PersonProfileResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String position,
        Role role,
        boolean active,
        List<String> teams,
        List<KnownSkill> skills,
        List<WishedSkill> wishes,
        List<ProjectMembership> projects,
        Mentoring mentoring,
        Neighbourhood neighbourhood
) {
    /** {@code level} and {@code since} are KNOWS properties, not skill fields. */
    public record KnownSkill(String skillId, String name, String category, String color, int level,
            LocalDate since) {
    }

    public record WishedSkill(String skillId, String name, String category, String color) {
    }

    public record ProjectMembership(String projectId, String name, String role, LocalDate from,
            LocalDate to, boolean active, List<String> uses) {
    }

    /** {@code skill} is null when the Skill node behind the MENTORS edge is gone. */
    public record Mentorship(String personId, String name, String skill, LocalDate since) {
    }

    public record Mentoring(List<Mentorship> mentees, List<Mentorship> mentors) {
    }

    public record GraphNode(String id, String kind, String label, String meta) {
    }

    public record GraphEdge(String source, String target, String type) {
    }

    /** {@code truncated}: the server hit its cap, so this is a sample of the surroundings. */
    public record Neighbourhood(List<GraphNode> nodes, List<GraphEdge> edges, boolean truncated) {
    }

    public PersonProfileResponse withNeighbourhood(Neighbourhood value) {
        return new PersonProfileResponse(id, email, firstName, lastName, position, role, active,
                teams, skills, wishes, projects, mentoring, value);
    }
}
