package com.skillatlas.mentoring.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Admin confirmation of a mentorship. Deliberately the three ids and nothing else: {@code since}
 * is stamped by the server, so a client cannot backdate a relationship it just created.
 */
public record MentorshipRequest(
        @NotBlank String mentorId,
        @NotBlank String menteeId,
        @NotBlank String skillId) {
}
