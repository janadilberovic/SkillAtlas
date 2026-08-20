package com.skillatlas.teams;

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
import com.skillatlas.teams.domain.Team;
import com.skillatlas.teams.dto.TeamCreateRequest;
import com.skillatlas.teams.dto.TeamResponse;
import com.skillatlas.teams.dto.TeamUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TeamsService service;

    public TeamsController(TeamsService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TeamResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<Team> result = service.list(PageRequest.of(safePage, safeSize, Sort.by("name")));
        return PageResponse.from(result.map(TeamResponse::from));
    }

    @GetMapping("/{id}")
    public TeamResponse get(@PathVariable String id) {
        return TeamResponse.from(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse create(@Valid @RequestBody TeamCreateRequest request) {
        return TeamResponse.from(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TeamResponse update(@PathVariable String id, @Valid @RequestBody TeamUpdateRequest request) {
        return TeamResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    // No body: MEMBER_OF carries no properties, unlike WORKED_ON on a project.
    @PostMapping("/{id}/members/{personId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(@PathVariable String id, @PathVariable String personId) {
        service.addMember(id, personId);
    }
}
