package com.skillatlas.mentoring.dto;

import java.util.List;

/**
 * One ranked candidate, with the ranking criteria on the row rather than folded into a score —
 * §4.3 requires the UI to show <em>why</em> someone is first, and "level 5, 1 mentorship" says it
 * where "score 8.5" does not.
 */
public record MentorCandidate(
        String id,
        String email,
        String firstName,
        String lastName,
        String position,
        List<String> teams,
        int level,
        long activeMentorships) {
}
