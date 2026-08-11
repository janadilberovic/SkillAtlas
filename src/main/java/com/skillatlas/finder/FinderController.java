package com.skillatlas.finder;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skillatlas.common.PageResponse;
import com.skillatlas.finder.dto.ExpertResponse;

/**
 * E4.1 expert finder: "who knows Neo4j AND Docker?".
 *
 * <p>Readable by any authenticated user (see SecurityConfig — everything outside /auth requires a
 * token). Ordering is decided in Cypher (score DESC, lastName ASC), so no sort parameter here.
 */
@RestController
@RequestMapping("/api/v1/experts")
public class FinderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final FinderService service;

    public FinderController(FinderService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ExpertResponse> search(
            @RequestParam(required = false) List<String> skills,
            @RequestParam(required = false) String team,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        // `skills` is optional here so a missing value produces our own 400 instead of Spring's.
        return PageResponse.from(service.findExperts(skills, team, PageRequest.of(safePage, safeSize)));
    }
}
