package com.skillatlas.finder.dto;

import java.util.List;

// One ranked person for an expert query: the safe person fields plus why they ranked where they did.
// `score` is the sum of the levels of the matched skills; `matchedSkills` shows the breakdown.
// `teams` is every MEMBER_OF team the person belongs to — the same names the `team` filter accepts.
public record ExpertResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String position,
        List<String> teams,
        int score,
        List<MatchedSkill> matchedSkills
) {
    public record MatchedSkill(String name, int level) {
    }
}
