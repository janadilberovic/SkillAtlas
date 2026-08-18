package com.skillatlas.mentoring;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.skillatlas.mentoring.dto.MentorshipRequest;
import com.skillatlas.mentoring.dto.MentorshipResponse;

import jakarta.validation.Valid;

/**
 * The MENTORS relationship itself. Admin-only on both verbs: 4.3 makes the relationship an
 * admin's confirmation, and a teardown is the same decision reversed.
 *
 * <p>The write is a MERGE, so confirming the same pairing twice leaves one relationship and
 * answers 201 both times — a double-click is not a conflict.
 */
@RestController
@RequestMapping("/api/v1/mentorships")
public class MentorshipsController {

    private final MentoringService service;

    public MentorshipsController(MentoringService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public MentorshipResponse confirm(@Valid @RequestBody MentorshipRequest request) {
        return service.confirm(request);
    }

    // Identified by the triple that defines it — a MENTORS relationship has no id of its own that
    // survives a restart, and the spec's catalogue has no id to hand out.
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void remove(@RequestParam String mentorId, @RequestParam String menteeId,
            @RequestParam String skillId) {
        service.remove(mentorId, menteeId, skillId);
    }
}
