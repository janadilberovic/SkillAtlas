package com.skillatlas.mentoring.exception;

public class MentorshipNotFoundException extends RuntimeException {

    public MentorshipNotFoundException(String mentorId, String menteeId, String skillId) {
        super("No mentorship from " + mentorId + " to " + menteeId + " for skill " + skillId);
    }
}
