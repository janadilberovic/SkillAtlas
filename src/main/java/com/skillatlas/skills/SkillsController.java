package com.skillatlas.skills;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import com.skillatlas.skills.dto.SkillCatalogResponse;
import com.skillatlas.skills.dto.SkillCreateRequest;
import com.skillatlas.skills.dto.SkillResponse;
import com.skillatlas.skills.dto.SkillUpdateRequest;
import com.skillatlas.skills.enums.SkillCategory;
import com.skillatlas.skills.exception.InvalidSkillCategoryException;

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
    public PageResponse<SkillCatalogResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<SkillCatalogResponse> result = service.list(search, parseCategory(category),
                SkillSort.of(sort), PageRequest.of(safePage, safeSize));
        return PageResponse.from(result);
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

    // Bound as a String rather than the enum: Spring's own type-mismatch path answers through
    // /error, which the security chain turns into a 401 - a wrong filter value is a 400.
    private static SkillCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SkillCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidSkillCategoryException(value);
        }
    }
}
