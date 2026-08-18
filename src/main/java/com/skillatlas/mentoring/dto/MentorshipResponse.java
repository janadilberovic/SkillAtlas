package com.skillatlas.mentoring.dto;

import java.time.LocalDate;

public record MentorshipResponse(
        String mentorId,
        String mentorName,
        String menteeId,
        String menteeName,
        SkillRef skill,
        LocalDate since) {
}
