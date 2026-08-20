package com.skillatlas.people.dto;

import java.util.Comparator;
import java.util.List;

import com.skillatlas.people.domain.Person;
import com.skillatlas.people.enums.Role;

/**
 * Read shape for lists. Never exposes passwordHash or the soft-delete fields; {@code teams} and
 * {@code topSkills} are flattened off the entity the repository already loaded, so a row costs no
 * extra query and the list stays a summary rather than the profile.
 */
public record PersonResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String position,
        Role role,
        boolean active,
        List<String> teams,
        List<TopSkill> topSkills
) {
    public record TopSkill(String skillId, String name, int level) {
    }

    private static final int TOP_SKILLS = 3;

    public static PersonResponse from(Person p) {
        return of(p.getId(), p.getEmail(), p.getFirstName(), p.getLastName(), p.getPosition(),
                p.getRole(), p.isActive(),
                p.getTeams().stream().map(t -> t.getName()).toList(),
                p.getKnows().stream()
                        .map(k -> new TopSkill(k.getSkill().getId(), k.getSkill().getName(),
                                k.getLevel()))
                        .toList());
    }

    /** Same row from an already-flattened projection, so the ordering rules live in one place. */
    public static PersonResponse of(String id, String email, String firstName, String lastName,
            String position, Role role, boolean active, List<String> teams, List<TopSkill> skills) {
        return new PersonResponse(id, email, firstName, lastName, position, role, active,
                teams.stream().sorted().toList(),
                skills.stream()
                        .sorted(Comparator.comparingInt(TopSkill::level).reversed()
                                .thenComparing(TopSkill::name))
                        .limit(TOP_SKILLS)
                        .toList());
    }
}
