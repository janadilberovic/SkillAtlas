package com.skillatlas.dashboard;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skillatlas.common.PageResponse;
import com.skillatlas.dashboard.dto.DashboardResponse;

/**
 * E6.3. Admin-only: the gap and bus-factor widgets are a readout of where the company is thin,
 * naming the single person a skill depends on.
 *
 * <p>The bus factor and the mapping queue are capped in Cypher and answered whole; the skill-gap
 * table and the mentor-request queue grow with headcount, so they page — the overview embeds their
 * first page and the two sub-resources walk the rest without re-running the other widgets.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private static final int MAX_PAGE_SIZE = 100;

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public DashboardResponse overview() {
        return service.overview();
    }

    @GetMapping("/skill-gap")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<DashboardResponse.SkillGapRow> skillGap(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return service.skillGap(PageRequest.of(safePage, safeSize));
    }

    @GetMapping("/mentor-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<DashboardResponse.MentorRequestRow> mentorRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return service.mentorRequests(PageRequest.of(safePage, safeSize));
    }
}
