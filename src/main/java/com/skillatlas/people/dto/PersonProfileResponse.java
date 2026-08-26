package com.skillatlas.people.dto;

import java.time.LocalDate;
import java.util.List;

import com.skillatlas.graph.dto.GraphEdge;
import com.skillatlas.graph.dto.GraphNode;
import com.skillatlas.people.enums.Role;

/**
 * A superset of {@link PersonResponse}, so callers that only read the person fields keep working.
 * Empty branches are empty lists, never {@code null}.
 */
public record PersonProfileResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String position,
        Role role,
        boolean active,
        // Null for anyone but an admin. An imported person has no hash yet and cannot sign in,
        // which is the one thing the admin panel has to be able to say out loud.
        Boolean hasPassword,
        // The repository puts the blob key here; PeopleProfileService signs it on the way out.
        String avatarUrl,
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

    /** {@code truncated}: the server hit its cap, so this is a sample of the surroundings. */
    public record Neighbourhood(List<GraphNode> nodes, List<GraphEdge> edges, boolean truncated) {
    }

    public PersonProfileResponse withNeighbourhood(Neighbourhood value) {
        return new PersonProfileResponse(id, email, firstName, lastName, position, role, active,
                hasPassword, avatarUrl, teams, skills, wishes, projects, mentoring, value);
    }

    public PersonProfileResponse withAvatarUrl(String value) {
        return new PersonProfileResponse(id, email, firstName, lastName, position, role, active,
                hasPassword, value, teams, skills, wishes, projects, mentoring, neighbourhood);
    }

    public PersonProfileResponse withHasPassword(Boolean value) {
        return new PersonProfileResponse(id, email, firstName, lastName, position, role, active,
                value, avatarUrl, teams, skills, wishes, projects, mentoring, neighbourhood);
    }
}
