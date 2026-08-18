package com.skillatlas.mentoring;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skillatlas.mentoring.dto.LearningPathResponse;
import com.skillatlas.mentoring.dto.MentorCandidatesResponse;

/**
 * The two reads that hang off a person: who could mentor them (E6.1) and how they get to a skill
 * (E6.2).
 */
@RestController
@RequestMapping("/api/v1/people/{id}")
public class MentoringController {

    private final MentoringService service;

    public MentoringController(MentoringService service) {
        this.service = service;
    }

    /** Admin-only per the API catalogue: proposing mentors is a staffing decision, not a browse. */
    @GetMapping("/mentor-candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public MentorCandidatesResponse mentorCandidates(@PathVariable String id,
            @RequestParam String skill,
            @RequestParam(required = false) Integer limit) {
        return service.mentorCandidates(id, skill, limit);
    }

    /**
     * Owner or admin. The identity in {@code authentication.name} is the id the token was issued
     * for, so a member asking for someone else's path is a 403 no matter what the path variable
     * says.
     */
    @GetMapping("/learning-path")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.name")
    public LearningPathResponse learningPath(@PathVariable String id, @RequestParam String skill) {
        return service.learningPath(id, skill);
    }
}
