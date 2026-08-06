package com.skillatlas.people.dto;

import com.skillatlas.people.domain.Person;
import com.skillatlas.people.enums.Role;

// Read shape. Never exposes passwordHash, soft-delete fields, or the relationship graph.
public record PersonResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String position,
        Role role,
        boolean active
) {
    public static PersonResponse from(Person p) {
        return new PersonResponse(
                p.getId(), p.getEmail(), p.getFirstName(), p.getLastName(),
                p.getPosition(), p.getRole(), p.isActive());
    }
}
