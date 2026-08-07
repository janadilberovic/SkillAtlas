package com.skillatlas.teams.dto;

import com.skillatlas.teams.domain.Team;

public record TeamResponse(
        String id,
        String name
) {
    public static TeamResponse from(Team t) {
        return new TeamResponse(t.getId(), t.getName());
    }
}
