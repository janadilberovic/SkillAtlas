package com.skillatlas.projects;

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
import com.skillatlas.projects.dto.ProjectCreateRequest;
import com.skillatlas.projects.dto.ProjectDetailResponse;
import com.skillatlas.projects.dto.ProjectMemberRequest;
import com.skillatlas.projects.dto.ProjectResponse;
import com.skillatlas.projects.dto.ProjectUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectsService service;

    public ProjectsController(ProjectsService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProjectResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return PageResponse.from(service.list(search, PageRequest.of(safePage, safeSize, Sort.by("name"))));
    }

    @GetMapping("/{id}")
    public ProjectDetailResponse get(@PathVariable String id) {
        return service.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody ProjectCreateRequest request) {
        return ProjectResponse.from(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectDetailResponse update(@PathVariable String id,
            @Valid @RequestBody ProjectUpdateRequest request) {
        // Answers with the roster too: the project screen writes here and would otherwise have to
        // re-read the project just to keep the people it is already showing.
        service.update(id, request);
        return service.detail(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @PostMapping("/{id}/members/{personId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignMember(@PathVariable String id, @PathVariable String personId,
            @Valid @RequestBody ProjectMemberRequest request) {
        service.assignMember(id, personId, request);
    }

    @DeleteMapping("/{id}/members/{personId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable String id, @PathVariable String personId) {
        service.removeMember(id, personId);
    }
}
