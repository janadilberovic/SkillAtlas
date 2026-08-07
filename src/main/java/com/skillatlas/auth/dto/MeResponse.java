package com.skillatlas.auth.dto;

import com.skillatlas.people.domain.Person;
import com.skillatlas.people.enums.Role;

public record MeResponse(
        String id,
        String email,
        String fullName,
        Role role
) {
    public static MeResponse from(Person p) {
        return new MeResponse(p.getId(), p.getEmail(),
                p.getFirstName() + " " + p.getLastName(), p.getRole());
    }
}
