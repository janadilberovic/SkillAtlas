package com.skillatlas.mentoring.dto;

import java.util.List;

/**
 * @param minLevel the KNOWS level a candidate had to clear (§4.3) — the UI states the bar it is
 *                 showing rather than hardcoding a 3 that could drift from the server's rule
 */
public record MentorCandidatesResponse(
        String menteeId,
        String menteeName,
        SkillRef skill,
        int minLevel,
        List<MentorCandidate> candidates) {
}
