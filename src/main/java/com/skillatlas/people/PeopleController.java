package com.skillatlas.people;

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
import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.dto.PersonProfileResponse;
import com.skillatlas.people.dto.PersonResponse;
import com.skillatlas.people.dto.PersonUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/people")
public class PeopleController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PeopleService service;
    private final PeopleProfileService profileService;

    public PeopleController(PeopleService service, PeopleProfileService profileService) {
        this.service = service;
        this.profileService = profileService;
    }

    @GetMapping
    public PageResponse<PersonResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<Person> result = service.list(
                PageRequest.of(safePage, safeSize, Sort.by("lastName", "firstName")));
        return PageResponse.from(result.map(PersonResponse::from));
    }

    // E4.2: the profile, not the shallow person. A superset of PersonResponse, so callers that
    // only read the person fields (people list, finder links) keep working unchanged.
    @GetMapping("/{id}")
    public PersonProfileResponse get(@PathVariable String id) {
        return profileService.getProfile(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonResponse create(@Valid @RequestBody PersonCreateRequest request) {
        return PersonResponse.from(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PersonResponse update(@PathVariable String id, @Valid @RequestBody PersonUpdateRequest request) {
        return PersonResponse.from(service.updateProfile(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.softDelete(id);
    }
}
