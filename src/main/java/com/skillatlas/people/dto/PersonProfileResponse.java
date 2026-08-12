package com.skillatlas.people.dto;

import java.time.LocalDate;
import java.util.List;

import com.skillatlas.people.enums.Role;

/**
 * E4.2 — the aggregated profile behind {@code GET /api/v1/people/{id}}.
 *
 * <p>A superset of {@link PersonResponse}: the same seven safe person fields plus everything the
 * profile screen renders around them. Like {@code PersonResponse} it never exposes
 * {@code passwordHash} or the soft-delete fields. Empty branches are empty lists, never
 * {@code null}, so the client needs no null checks.
 *
 * <p>{@code GraphNode} / {@code GraphEdge} are nested here on purpose: E5.1 (the graph explorer)
 * will reuse the shape and can promote them to a {@code graph} package when it needs them for more
 * than one caller. They carry no layout (x/y/radius) — placement is the force-graph's job on the
 * client, not the database's.
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
    /** A KNOWS edge: {@code level} and {@code since} are relationship properties, not skill fields. */
    public record KnownSkill(String skillId, String name, String category, String color, int level,
            LocalDate since) {
    }

    public record WishedSkill(String skillId, String name, String category, String color) {
    }

    /** A WORKED_ON edge; {@code uses} is what the project USES, so the row explains itself. */
    public record ProjectMembership(String projectId, String name, String role, LocalDate from,
            LocalDate to, boolean active, List<String> uses) {
    }

    /** One MENTORS edge seen from this person. {@code skill} is null if the Skill node is gone. */
    public record Mentorship(String personId, String name, String skill, LocalDate since) {
    }

    /** Both directions of MENTORS: who this person mentors, and who mentors them. */
    public record Mentoring(List<Mentorship> mentees, List<Mentorship> mentors) {
    }

    public record GraphNode(String id, String kind, String label, String meta) {
    }

    public record GraphEdge(String source, String target, String type) {
    }

    /** The person's surroundings, capped server-side; {@code truncated} says the cap was hit. */
    public record Neighbourhood(List<GraphNode> nodes, List<GraphEdge> edges, boolean truncated) {
    }

    public PersonProfileResponse withNeighbourhood(Neighbourhood value) {
        return new PersonProfileResponse(id, email, firstName, lastName, position, role, active,
                teams, skills, wishes, projects, mentoring, value);
    }
}
