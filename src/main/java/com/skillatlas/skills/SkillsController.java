package com.skillatlas.skills;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.skillatlas.common.PageResponse;
import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.dto.SkillCreateRequest;
import com.skillatlas.skills.dto.SkillResponse;
import com.skillatlas.skills.dto.SkillUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SkillsService service;

    public SkillsController(SkillsService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<SkillResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<Skill> result = service.list(PageRequest.of(safePage, safeSize, Sort.by("name")));
        return PageResponse.from(result.map(SkillResponse::from));
    }

    @GetMapping("/{id}")
    public SkillResponse get(@PathVariable String id) {
        return SkillResponse.from(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public SkillResponse create(@Valid @RequestBody SkillCreateRequest request) {
        return SkillResponse.from(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SkillResponse update(@PathVariable String id, @Valid @RequestBody SkillUpdateRequest request) {
        return SkillResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
