package com.skillatlas.people;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skillatlas.people.dto.MySkillsResponse;
import com.skillatlas.people.dto.SetSkillLevelRequest;
import com.skillatlas.security.SecurityUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/people/{id}")
public class PeopleSkillsController {

    private final PeopleSkillsService service;

    public PeopleSkillsController(PeopleSkillsService service) {
        this.service = service;
    }

    // Read is open to any authenticated user (profiles are read-only visible to everyone).
    @GetMapping("/skills")
    public MySkillsResponse mySkills(@PathVariable String id) {
        return service.getMySkills(id);
    }

    @PutMapping("/skills/{skillId}")
    public MySkillsResponse setSkill(@PathVariable String id, @PathVariable String skillId,
            @Valid @RequestBody SetSkillLevelRequest request) {
        requireSelf(id);
        return service.setSkillLevel(id, skillId, request.level());
    }

    @DeleteMapping("/skills/{skillId}")
    public MySkillsResponse removeSkill(@PathVariable String id, @PathVariable String skillId) {
        requireSelf(id);
        return service.removeSkill(id, skillId);
    }

    @PutMapping("/wishes/{skillId}")
    public MySkillsResponse addWish(@PathVariable String id, @PathVariable String skillId) {
        requireSelf(id);
        return service.addWish(id, skillId);
    }

    @DeleteMapping("/wishes/{skillId}")
    public MySkillsResponse removeWish(@PathVariable String id, @PathVariable String skillId) {
        requireSelf(id);
        return service.removeWish(id, skillId);
    }

    // IDOR guard: you may only mutate your OWN skills. Identity comes from the token, never the path.
    private void requireSelf(String id) {
        if (!id.equals(SecurityUtil.currentUserId())) {
            throw new AccessDeniedException("You can only modify your own skills");
        }
    }
}
